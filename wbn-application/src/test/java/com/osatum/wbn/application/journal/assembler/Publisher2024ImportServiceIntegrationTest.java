package com.osatum.wbn.application.journal.assembler;

import com.osatum.wbn.application.dictionary.service.DictionaryAsjcCategoryService;
import com.osatum.wbn.application.dictionary.service.DictionaryCountryService;
import com.osatum.wbn.application.dictionary.service.DictionaryLanguageService;
import com.osatum.wbn.application.dictionary.service.DictionaryScientificCategoryService;
import com.osatum.wbn.application.dictionary.service.DictionaryTRCategoryService;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.Institution;
import com.osatum.wbn.domain.model.survey.model.JournalInstitution;
import com.osatum.wbn.domain.model.survey.model.Person;
import com.osatum.wbn.domain.service.journal.JournalRatingPolicyService;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import com.osatum.wbn.migration.service.Publisher2024ImportService;
import com.osatum.wbn.migration.service.Publisher2024ImportService.Publisher2024Record;
import com.osatum.wbn.migration.service.Publisher2024ImportService.InstitutionValues;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Publisher2024ImportServiceIntegrationTest.Configuration.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:publisher-import;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@Transactional
class Publisher2024ImportServiceIntegrationTest {
    @TestConfiguration
    @EnableAutoConfiguration
    @EntityScan("com.osatum.wbn.domain.model")
    @EnableJpaRepositories(basePackageClasses = QuestionnaireSpringDataRepository.class)
    @Import(Publisher2024ImportService.class)
    static class Configuration {
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Publisher2024ImportService service;

    @Test
    void replacesOnlySelected2024InstitutionsAndPersistsNullsWithoutChangingEditorialData() {
        Journal journal = journal(123L);
        Questionnaire current = questionnaire(journal, 2024, survey());
        Questionnaire previous = questionnaire(journal, 2023, survey());
        Questionnaire other = questionnaire(journal(456L), 2024, survey());
        entityManager.flush();
        entityManager.clear();
        InstitutionValues institution = new InstitutionValues(null, null, null, null, null, null,
                null, null, "office@example.org", null, null, null);
        Publisher2024Record record = new Publisher2024Record(123L, List.of(institution, institution), "publisher.xlsx", 2);

        service.updatePublisher(record);
        service.updatePublisher(record);
        entityManager.flush();
        entityManager.clear();

        SurveyData saved = entityManager.find(Questionnaire.class, current.getId()).getSurveyData();
        assertThat(saved.getSurvey().getPublisher().getPublisherInstitutions()).hasSize(2).allSatisfy(office -> {
            assertThat(office.getInstitutionRole()).isEqualTo('P');
            assertThat(office.getInstitution().getEmail()).isEqualTo("office@example.org");
        });
        assertUnrelatedDataUnchanged(saved.getSurvey());
        ObjectMapper mapper = new ObjectMapper();
        assertThat(mapper.writeValueAsString(entityManager.find(Questionnaire.class, previous.getId()).getSurveyData().getSurvey()))
                .isEqualTo(mapper.writeValueAsString(survey()));
        assertThat(mapper.writeValueAsString(entityManager.find(Questionnaire.class, other.getId()).getSurveyData().getSurvey()))
                .isEqualTo(mapper.writeValueAsString(survey()));
        var offices = mapper.readTree(json(saved)).path("publisher").path("publisherInstitutions");
        assertThat(offices.size()).isEqualTo(2);
        for (int officeIndex = 0; officeIndex < offices.size(); officeIndex++) {
            assertThat(offices.get(officeIndex).path("institutionRole").asText()).isEqualTo("P");
            for (String field : List.of("name", "department", "street", "postCode", "city", "state", "country", "phone", "www", "gicid", "type")) {
                assertThat(offices.get(officeIndex).path("institution").get(field).isNull()).isTrue();
            }
        }

        Questionnaire updated = entityManager.find(Questionnaire.class, current.getId());
        updater().update(updated.getJournal(), saved.getSurvey());
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Journal.class, 123L).getInstitutions().stream()
                .filter(office -> "P".equals(office.getRoleCode())).toList()).hasSize(2).allSatisfy(office -> {
            assertThat(office.getRoleCode()).isEqualTo("P");
            assertThat(office.getEmail()).isEqualTo("office@example.org");
            assertThat(office.getName()).isNull();
            assertThat(office.getWww()).isNull();
        });
    }

    @Test
    void propagatesEmptyListToJournalAndDeletesOldPublishersWithoutRemovingEditorialInstitutions() {
        Journal journal = journal(123L);
        Questionnaire questionnaire = questionnaire(journal, 2024, survey());
        var previousPublisher = new com.osatum.wbn.domain.model.journal.JournalInstitution();
        previousPublisher.setJournal(journal);
        previousPublisher.setRoleCode("P");
        previousPublisher.setName("Previous office");
        var editorialOffice = new com.osatum.wbn.domain.model.journal.JournalInstitution();
        editorialOffice.setJournal(journal);
        editorialOffice.setRoleCode("E");
        editorialOffice.setName("Existing editorial office");
        journal.setInstitutions(new HashSet<>(List.of(previousPublisher, editorialOffice)));
        entityManager.flush();
        Long previousPublisherId = previousPublisher.getId();
        entityManager.clear();

        service.updatePublisher(new Publisher2024Record(123L, List.of(), "publisher.xlsx", 2));
        entityManager.flush();
        entityManager.clear();
        Questionnaire saved = entityManager.find(Questionnaire.class, questionnaire.getId());
        Survey survey = saved.getSurveyData().getSurvey();
        assertThat(survey.getPublisher().getPublisherInstitutions()).isEmpty();
        assertUnrelatedDataUnchanged(survey);
        assertThat(new ObjectMapper().readTree(json(saved.getSurveyData())).path("publisher").path("publisherInstitutions").size()).isZero();

        updater().update(saved.getJournal(), survey);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Journal.class, 123L).getInstitutions()).singleElement()
                .satisfies(institution -> assertThat(institution.getRoleCode()).isEqualTo("E"));
        assertThat(entityManager.find(com.osatum.wbn.domain.model.journal.JournalInstitution.class, previousPublisherId)).isNull();
    }

    @Test
    void createsSurveyDataForQuestionnaireWithoutSurvey() {
        Questionnaire questionnaire = questionnaire(journal(123L), 2024, null);
        entityManager.flush();
        entityManager.clear();

        service.updatePublisher(new Publisher2024Record(123L, List.of(), "publisher.xlsx", 2));
        entityManager.flush();
        entityManager.clear();

        SurveyData saved = entityManager.find(Questionnaire.class, questionnaire.getId()).getSurveyData();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSurvey().getPublisher().getPublisherInstitutions()).isEmpty();
    }

    private void assertUnrelatedDataUnchanged(Survey survey) {
        assertThat(survey.getEditorial().getEditors()).isEqualTo(survey().getEditorial().getEditors());
        assertThat(survey.getEditorial()).isEqualTo(survey().getEditorial());
        assertThat(survey.getCooperation()).isEqualTo(survey().getCooperation());
        assertThat(survey.getStability()).isEqualTo(survey().getStability());
        assertThat(survey.getEvaluationYear()).isEqualTo(2024);
        assertThat(survey.getTimestamp()).isEqualTo("original");
    }

    private SurveyToJournalUpdater updater() {
        return new SurveyToJournalUpdater(mock(DictionaryCountryService.class), mock(DictionaryScientificCategoryService.class),
                mock(DictionaryAsjcCategoryService.class), mock(DictionaryTRCategoryService.class), mock(DictionaryLanguageService.class),
                mock(JournalRatingPolicyService.class), new SurveyJournalEntityMapper());
    }

    private String json(SurveyData surveyData) {
        return (String) entityManager.createNativeQuery("select survey from questionnaire_survey where id = :id")
                .setParameter("id", surveyData.getId()).getSingleResult();
    }

    private Journal journal(long identifier) {
        Journal journal = new Journal();
        journal.setId(identifier);
        entityManager.persist(journal);
        return journal;
    }

    private Questionnaire questionnaire(Journal journal, int year, Survey survey) {
        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setJournal(journal);
        questionnaire.setYear(year);
        if (survey != null) {
            SurveyData surveyData = new SurveyData();
            surveyData.setSurvey(survey);
            questionnaire.setSurveyData(surveyData);
        }
        entityManager.persist(questionnaire);
        return questionnaire;
    }

    private Survey survey() {
        Survey survey = new Survey();
        survey.setEvaluationYear(2024);
        survey.setTimestamp("original");
        Person editor = new Person();
        editor.setFirstName("Existing");
        editor.setLastName("Editor");
        survey.getEditorial().setEditors(List.of(editor));
        Institution institution = new Institution();
        institution.setName("Previous office");
        institution.setWww("https://previous.example.org");
        JournalInstitution office = new JournalInstitution();
        office.setInstitutionRole('P');
        office.setInstitution(institution);
        survey.getPublisher().setPublisherInstitutions(List.of(office));
        Institution editorialInstitution = new Institution();
        editorialInstitution.setName("Existing editorial office");
        JournalInstitution editorialOffice = new JournalInstitution();
        editorialOffice.setInstitutionRole('E');
        editorialOffice.setInstitution(editorialInstitution);
        survey.getEditorial().setRedactionInstitutions(List.of(editorialOffice));
        survey.getStability().setFirstIssueYear(1980);
        return survey;
    }
}
