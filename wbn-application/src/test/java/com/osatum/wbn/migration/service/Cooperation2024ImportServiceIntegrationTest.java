package com.osatum.wbn.migration.service;

import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Cooperation;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.Cooperator;
import com.osatum.wbn.domain.model.survey.model.ScientistMetric;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import com.osatum.wbn.migration.service.Cooperation2024ImportService.Cooperation2024Record;
import com.osatum.wbn.migration.service.Cooperation2024ImportService.MetricValues;
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

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Cooperation2024ImportServiceIntegrationTest.Configuration.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:cooperation-import;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@Transactional
class Cooperation2024ImportServiceIntegrationTest {
    @TestConfiguration
    @EnableAutoConfiguration
    @EntityScan("com.osatum.wbn.domain.model")
    @EnableJpaRepositories(basePackageClasses = QuestionnaireSpringDataRepository.class)
    @Import(Cooperation2024ImportService.class)
    static class Configuration {
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Cooperation2024ImportService service;

    @Test
    void persistsMetricChangesAndClearedValuesWithoutChangingOtherDataOrYears() {
        Journal journal = journal();
        Questionnaire current = questionnaire(journal, 2024, survey());
        Questionnaire previous = questionnaire(journal, 2023, survey());
        entityManager.flush();
        entityManager.clear();

        service.updateCooperation(new Cooperation2024Record(
                journal.getId(), new MetricValues(0, null, 0.0), new MetricValues(null, null, null),
                new MetricValues(9, 50.0, 25.0), "cooperation.xlsx", 5
        ));
        entityManager.flush();
        entityManager.clear();

        Survey updated = entityManager.find(Questionnaire.class, current.getId()).getSurveyData().getSurvey();
        Cooperation cooperation = updated.getCooperation();
        assertThat(cooperation.getAuthorMetric().getUnique()).isZero();
        assertThat(cooperation.getAuthorMetric().getExternalAffiliationPercentage()).isNull();
        assertThat(cooperation.getAuthorMetric().getForeignAffiliationPercentage()).isZero();
        assertThat(cooperation.getReviewerMetric().getUnique()).isNull();
        assertThat(cooperation.getReviewerMetric().getExternalAffiliationPercentage()).isNull();
        assertThat(cooperation.getReviewerMetric().getForeignAffiliationPercentage()).isNull();
        assertThat(cooperation.getDirectorsMetric().getUnique()).isEqualTo(9);
        assertThat(cooperation.getDirectorsMetric().getExternalAffiliationPercentage()).isEqualTo(50.0);
        assertThat(cooperation.getDirectorsMetric().getForeignAffiliationPercentage()).isEqualTo(25.0);
        assertThat(cooperation.getReviewersList()).extracting(Cooperator::getFullName).containsExactly("Reviewer");
        assertThat(cooperation.getDirectorsList()).extracting(Cooperator::getFullName).containsExactly("Director");
        assertThat(cooperation.getReviewers()).isEqualTo("Historical reviewers");
        assertThat(cooperation.getTitleAndAbstractInAllArticles()).isTrue();
        assertThat(updated.getTimestamp()).isEqualTo("original-timestamp");
        assertThat(updated.getEvaluationYear()).isEqualTo(2024);
        Survey unchanged = entityManager.find(Questionnaire.class, previous.getId()).getSurveyData().getSurvey();
        assertThat(unchanged).isEqualTo(survey());
    }

    @Test
    void persistsNewSurveyDataWhenQuestionnaireHasNone() {
        Journal journal = journal();
        Questionnaire questionnaire = questionnaire(journal, 2024, null);
        entityManager.flush();
        entityManager.clear();

        MetricValues metric = new MetricValues(5, 100.0, 0.0);
        service.updateCooperation(new Cooperation2024Record(journal.getId(), metric, metric, metric, "cooperation.xlsx", 5));
        entityManager.flush();
        entityManager.clear();

        SurveyData surveyData = entityManager.find(Questionnaire.class, questionnaire.getId()).getSurveyData();
        assertThat(surveyData.getId()).isNotNull();
        assertThat(surveyData.getSurvey().getCooperation().getAuthorMetric().getUnique()).isEqualTo(5);
    }

    private Journal journal() {
        Journal journal = new Journal();
        journal.setId(123L);
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
        ScientistMetric metric = new ScientistMetric();
        metric.setUnique(30);
        metric.setExternalAffiliationPercentage(40.0);
        metric.setForeignAffiliationPercentage(20.0);
        Cooperator reviewer = new Cooperator();
        reviewer.setFullName("Reviewer");
        Cooperator director = new Cooperator();
        director.setFullName("Director");
        Survey survey = new Survey();
        survey.setEvaluationYear(2024);
        survey.setTimestamp("original-timestamp");
        survey.getCooperation().setAuthorMetric(metric);
        survey.getCooperation().setReviewerMetric(metric);
        survey.getCooperation().setDirectorsMetric(metric);
        survey.getCooperation().setReviewers("Historical reviewers");
        survey.getCooperation().setTitleAndAbstractInAllArticles(true);
        survey.getCooperation().getReviewersList().add(reviewer);
        survey.getCooperation().getDirectorsList().add(director);
        return survey;
    }
}
