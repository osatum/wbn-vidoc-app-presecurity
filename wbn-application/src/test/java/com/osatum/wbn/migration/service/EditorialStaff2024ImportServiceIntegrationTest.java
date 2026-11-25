package com.osatum.wbn.migration.service;

import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.journal.PersonPosition;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.Cooperator;
import com.osatum.wbn.domain.model.survey.model.Country;
import com.osatum.wbn.domain.model.survey.model.JournalInstitution;
import com.osatum.wbn.domain.model.survey.model.Person;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import com.osatum.wbn.migration.service.EditorialStaff2024ImportService.EditorValues;
import com.osatum.wbn.migration.service.EditorialStaff2024ImportService.EditorialStaff2024Record;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = EditorialStaff2024ImportServiceIntegrationTest.Configuration.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:editorial-staff-import;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@Transactional
class EditorialStaff2024ImportServiceIntegrationTest {
    @TestConfiguration
    @EnableAutoConfiguration
    @EntityScan("com.osatum.wbn.domain.model")
    @EnableJpaRepositories(basePackageClasses = QuestionnaireSpringDataRepository.class)
    @Import(EditorialStaff2024ImportService.class)
    static class Configuration {
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EditorialStaff2024ImportService service;

    @Test
    void replacesOnlySelected2024EditorialStaffClearsMissingFieldsAndPreservesRepeatedRows() {
        Journal journal = journal(123L);
        Questionnaire current = questionnaire(journal, 2024, survey());
        Questionnaire previous = questionnaire(journal, 2023, survey());
        Questionnaire other = questionnaire(journal(456L), 2024, survey());
        entityManager.flush();
        entityManager.clear();
        EditorValues editor = new EditorValues("Anna", null, "Nowak", null, null, null, null, null, null);
        EditorialStaff2024Record record = new EditorialStaff2024Record(123L, List.of(editor, editor), "editors.xlsx", 2);

        service.updateEditorialStaff(record);
        service.updateEditorialStaff(record);
        entityManager.flush();
        entityManager.clear();

        SurveyData saved = entityManager.find(Questionnaire.class, current.getId()).getSurveyData();
        assertThat(saved.getSurvey().getEditorial().getEditors()).hasSize(2).allSatisfy(person -> {
            assertThat(person).extracting("firstName", "lastName").containsExactly("Anna", "Nowak");
            assertThat(person).extracting("middleName", "personPosition", "title", "department", "email", "gicid", "country")
                    .containsOnlyNulls();
        });
        assertUnrelatedDataUnchanged(saved.getSurvey());
        assertThat(new ObjectMapper().writeValueAsString(entityManager.find(Questionnaire.class, previous.getId()).getSurveyData().getSurvey()))
                .isEqualTo(new ObjectMapper().writeValueAsString(survey()));
        assertThat(new ObjectMapper().writeValueAsString(entityManager.find(Questionnaire.class, other.getId()).getSurveyData().getSurvey()))
                .isEqualTo(new ObjectMapper().writeValueAsString(survey()));
        var editors = new ObjectMapper().readTree(json(saved)).path("editorial").path("editors");
        assertThat(editors.size()).isEqualTo(2);
        for (int editorIndex = 0; editorIndex < editors.size(); editorIndex++) {
            for (String field : List.of("middleName", "personPosition", "title", "department", "email", "gicid", "country")) {
                assertThat(editors.get(editorIndex).get(field).isNull()).isTrue();
            }
        }
    }

    @Test
    void persistsEmptyListWhenAllEditorialStaffAreRemoved() {
        Questionnaire questionnaire = questionnaire(journal(123L), 2024, survey());
        entityManager.flush();
        entityManager.clear();

        service.updateEditorialStaff(new EditorialStaff2024Record(123L, List.of(), "editors.xlsx", 2));
        entityManager.flush();
        entityManager.clear();

        SurveyData saved = entityManager.find(Questionnaire.class, questionnaire.getId()).getSurveyData();
        assertThat(saved.getSurvey().getEditorial().getEditors()).isEmpty();
        assertUnrelatedDataUnchanged(saved.getSurvey());
        var editors = new ObjectMapper().readTree(json(saved)).path("editorial").path("editors");
        assertThat(editors.isArray()).isTrue();
        assertThat(editors.size()).isZero();
    }

    @Test
    void persistsSurveyDataWhenTheQuestionnaireHasNone() {
        Questionnaire questionnaire = questionnaire(journal(123L), 2024, null);
        entityManager.flush();
        entityManager.clear();

        service.updateEditorialStaff(new EditorialStaff2024Record(123L,
                List.of(new EditorValues("Anna", null, "Nowak", PersonPosition.EDITOR_IN_CHIEF, null, null, null, null, null)),
                "editors.xlsx", 2));
        entityManager.flush();
        entityManager.clear();

        SurveyData saved = entityManager.find(Questionnaire.class, questionnaire.getId()).getSurveyData();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSurvey().getEditorial().getEditors()).singleElement().satisfies(person ->
                assertThat(person.getPersonPosition()).isEqualTo(PersonPosition.EDITOR_IN_CHIEF));
    }

    private void assertUnrelatedDataUnchanged(Survey survey) {
        assertThat(survey.getEditorial().getRedactionInstitutions()).isEqualTo(survey().getEditorial().getRedactionInstitutions());
        assertThat(survey.getCooperation()).isEqualTo(survey().getCooperation());
        assertThat(survey.getStability()).isEqualTo(survey().getStability());
        assertThat(survey.getTimestamp()).isEqualTo("original");
        assertThat(survey.getEvaluationYear()).isEqualTo(2024);
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
        Country country = new Country();
        country.setCode("PL");
        country.setNamePl("Polska");
        Person person = new Person();
        person.setFirstName("Previous");
        person.setMiddleName("Middle");
        person.setLastName("Editor");
        person.setPersonPosition(PersonPosition.EDITOR_IN_CHIEF);
        person.setTitle("Dr");
        person.setDepartment("Previous university");
        person.setEmail("previous@example.org");
        person.setGicid("previous-id");
        person.setCountry(country);
        survey.getEditorial().setEditors(List.of(person));
        JournalInstitution institution = new JournalInstitution();
        institution.setInstitutionRole('E');
        survey.getEditorial().setRedactionInstitutions(List.of(institution));
        Cooperator cooperator = new Cooperator();
        cooperator.setFullName("Existing cooperator");
        survey.getCooperation().setDirectorsList(List.of(cooperator));
        survey.getCooperation().setReviewersList(List.of(cooperator));
        survey.getStability().setFirstIssueYear(1980);
        return survey;
    }
}
