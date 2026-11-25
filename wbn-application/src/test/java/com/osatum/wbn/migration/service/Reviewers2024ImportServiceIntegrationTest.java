package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.dictionary.service.DictionaryCountryService;
import com.osatum.wbn.domain.model.dictionary.DictionaryCountry;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.Cooperator;
import com.osatum.wbn.domain.model.survey.model.ScientistMetric;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import com.osatum.wbn.migration.service.Reviewers2024ImportService.ReviewerValues;
import com.osatum.wbn.migration.service.Reviewers2024ImportService.Reviewers2024Record;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Reviewers2024ImportServiceIntegrationTest.Configuration.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:reviewers-import;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@Transactional
class Reviewers2024ImportServiceIntegrationTest {
    @TestConfiguration
    @EnableAutoConfiguration
    @EntityScan("com.osatum.wbn.domain.model")
    @EnableJpaRepositories(basePackageClasses = QuestionnaireSpringDataRepository.class)
    @Import(Reviewers2024ImportService.class)
    static class Configuration {
        @Bean
        DictionaryCountryService dictionaryCountryService() {
            return mock(DictionaryCountryService.class);
        }
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Reviewers2024ImportService service;

    @Autowired
    private DictionaryCountryService dictionaryCountryService;

    @BeforeEach
    void configureDictionary() {
        reset(dictionaryCountryService);
        DictionaryCountry country = new DictionaryCountry();
        country.setCode("PL");
        country.setCodeAlpha3("POL");
        country.setNamePl("Polska");
        country.setNameEn("Poland");
        when(dictionaryCountryService.getAll()).thenReturn(List.of(country));
    }

    @Test
    void persistsReplacementAndNullsOnlyInSelected2024Questionnaire() {
        Journal journal = journal(123L);
        Questionnaire current = questionnaire(journal, 2024, survey());
        Questionnaire previous = questionnaire(journal, 2023, survey());
        Questionnaire other = questionnaire(journal(456L), 2024, survey());
        entityManager.flush();
        entityManager.clear();

        Reviewers2024Record record = new Reviewers2024Record(123L,
                List.of(new ReviewerValues("Anna Nowak", null, "Polska", null)), "reviewers.xlsx", 2);
        service.updateReviewers(record);
        service.updateReviewers(record);
        entityManager.flush();
        entityManager.clear();

        SurveyData saved = entityManager.find(Questionnaire.class, current.getId()).getSurveyData();
        Survey updated = saved.getSurvey();
        assertThat(updated.getCooperation().getReviewersList()).singleElement().satisfies(reviewer ->
                assertThat(reviewer).extracting("fullName", "affiliation", "country", "email")
                        .containsExactly("Anna Nowak", null, "PL", null));
        assertUnrelatedDataUnchanged(updated);
        assertThat(entityManager.find(Questionnaire.class, previous.getId()).getSurveyData().getSurvey()).isEqualTo(survey());
        assertThat(entityManager.find(Questionnaire.class, other.getId()).getSurveyData().getSurvey()).isEqualTo(survey());
        var reviewers = new ObjectMapper().readTree(json(saved)).path("cooperation").path("reviewersList");
        assertThat(reviewers.size()).isEqualTo(1);
        assertThat(reviewers.get(0).get("affiliation").isNull()).isTrue();
        assertThat(reviewers.get(0).get("email").isNull()).isTrue();
    }

    @Test
    void persistsEmptyArrayWhenClearingReviewers() {
        Questionnaire questionnaire = questionnaire(journal(123L), 2024, survey());
        entityManager.flush();
        entityManager.clear();

        service.updateReviewers(new Reviewers2024Record(123L, List.of(), "reviewers.xlsx", 2));
        entityManager.flush();
        entityManager.clear();

        SurveyData saved = entityManager.find(Questionnaire.class, questionnaire.getId()).getSurveyData();
        assertThat(saved.getSurvey().getCooperation().getReviewersList()).isEmpty();
        assertUnrelatedDataUnchanged(saved.getSurvey());
        var reviewers = new ObjectMapper().readTree(json(saved)).path("cooperation").path("reviewersList");
        assertThat(reviewers.isArray()).isTrue();
        assertThat(reviewers.size()).isZero();
    }

    @Test
    void persistsNewSurveyDataWhenMissing() {
        Questionnaire questionnaire = questionnaire(journal(123L), 2024, null);
        entityManager.flush();
        entityManager.clear();

        service.updateReviewers(new Reviewers2024Record(123L,
                List.of(new ReviewerValues("Anna Nowak", null, "PL", null)), "reviewers.xlsx", 2));
        entityManager.flush();
        entityManager.clear();

        SurveyData saved = entityManager.find(Questionnaire.class, questionnaire.getId()).getSurveyData();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSurvey().getCooperation().getReviewersList()).hasSize(1);
    }

    @Test
    void unknownCountryDoesNotChangePersistedList() {
        Questionnaire questionnaire = questionnaire(journal(123L), 2024, survey());
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> service.updateReviewers(new Reviewers2024Record(123L,
                List.of(new ReviewerValues("Anna Nowak", null, "PL", null), new ReviewerValues("Jan Kowalski", null, "Unknown", null)),
                "reviewers.xlsx", 2))).isInstanceOf(IllegalStateException.class).hasMessageContaining("Unknown");
        entityManager.clear();

        assertThat(entityManager.find(Questionnaire.class, questionnaire.getId()).getSurveyData().getSurvey()).isEqualTo(survey());
    }

    private void assertUnrelatedDataUnchanged(Survey survey) {
        assertThat(survey.getCooperation()).extracting("authorMetric", "reviewerMetric", "directorsMetric", "directorsList", "reviewers")
                .containsExactly(survey().getCooperation().getAuthorMetric(), survey().getCooperation().getReviewerMetric(),
                        survey().getCooperation().getDirectorsMetric(), survey().getCooperation().getDirectorsList(), "legacy");
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
        Cooperator reviewer = new Cooperator();
        reviewer.setFullName("Existing reviewer");
        reviewer.setCountry("PL");
        survey.getCooperation().setReviewersList(List.of(reviewer));
        survey.getCooperation().setDirectorsList(List.of(reviewer));
        survey.getCooperation().setReviewers("legacy");
        ScientistMetric metric = new ScientistMetric();
        metric.setUnique(10);
        survey.getCooperation().setAuthorMetric(metric);
        survey.getCooperation().setReviewerMetric(metric);
        survey.getCooperation().setDirectorsMetric(metric);
        survey.getStability().setFirstIssueYear(1980);
        return survey;
    }
}
