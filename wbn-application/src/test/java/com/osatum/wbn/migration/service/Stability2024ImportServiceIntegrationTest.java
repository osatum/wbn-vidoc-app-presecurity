package com.osatum.wbn.migration.service;

import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.journal.JournalFrequency;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.ArticlePublished;
import com.osatum.wbn.domain.model.survey.model.ScientistMetric;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import com.osatum.wbn.migration.service.Stability2024ImportService.ArticlePublishedValues;
import com.osatum.wbn.migration.service.Stability2024ImportService.Stability2024Record;
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
@ContextConfiguration(classes = Stability2024ImportServiceIntegrationTest.Configuration.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:stability-import;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@Transactional
class Stability2024ImportServiceIntegrationTest {
    @TestConfiguration
    @EnableAutoConfiguration
    @EntityScan("com.osatum.wbn.domain.model")
    @EnableJpaRepositories(basePackageClasses = QuestionnaireSpringDataRepository.class)
    @Import(Stability2024ImportService.class)
    static class Configuration {
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Stability2024ImportService service;

    @Test
    void persistsYearKeyedUpdatesAndExplicitJsonNullsWithoutChangingOtherFieldsOrQuestionnaires() {
        Journal journal = journal();
        Questionnaire current = questionnaire(journal, 2024, survey());
        Questionnaire previous = questionnaire(journal, 2023, survey());
        entityManager.flush();
        entityManager.clear();

        service.updateStability(new Stability2024Record(journal.getId(), List.of(
                new ArticlePublishedValues(2021, null, null, null, null, null, null),
                new ArticlePublishedValues(2022, 7, 6, 20, 5, 1, JournalFrequency.a),
                new ArticlePublishedValues(2023, null, null, null, null, null, null),
                new ArticlePublishedValues(2024, 0, null, 40, 10, null, JournalFrequency.k)
        ), "stability.xlsx", 3));
        entityManager.flush();
        entityManager.clear();

        SurveyData saved = entityManager.find(Questionnaire.class, current.getId()).getSurveyData();
        Survey updated = saved.getSurvey();
        List<ArticlePublished> articles = updated.getStability().getArticlePublished();
        assertThat(articles).extracting(ArticlePublished::getYear).containsExactly(2024, 2020, 2021, 2022, 2023);
        assertThat(articles.getFirst()).extracting(ArticlePublished::getAmount, ArticlePublished::getScientificAmount,
                ArticlePublished::getReceivedManuscriptCount, ArticlePublished::getRejectedManuscriptCount,
                ArticlePublished::getPublishedIssuesAmount, ArticlePublished::getFrequencyCode, ArticlePublished::getReviewedAmount)
                .containsExactly(0, null, 40, 10, null, JournalFrequency.k, 8);
        assertThat(articles.get(1)).isEqualTo(article(2020));
        assertThat(articles.get(2)).extracting(ArticlePublished::getAmount, ArticlePublished::getScientificAmount,
                ArticlePublished::getReceivedManuscriptCount, ArticlePublished::getRejectedManuscriptCount,
                ArticlePublished::getPublishedIssuesAmount, ArticlePublished::getFrequencyCode, ArticlePublished::getReviewedAmount)
                .containsExactly(null, null, null, null, null, null, 8);
        assertThat(updated.getStability().getFirstIssueYear()).isEqualTo(1980);
        assertThat(updated.getStability().getFrequencyId()).isEqualTo(4L);
        assertThat(updated.getStability().getPublishingBreak()).isTrue();
        assertThat(updated.getCooperation()).isEqualTo(survey().getCooperation());
        assertThat(updated.getTimestamp()).isEqualTo("original-timestamp");
        assertThat(updated.getEvaluationYear()).isEqualTo(2024);
        assertThat(entityManager.find(Questionnaire.class, previous.getId()).getSurveyData().getSurvey()).isEqualTo(survey());

        String json = (String) entityManager.createNativeQuery("select survey from questionnaire_survey where id = :id")
                .setParameter("id", saved.getId()).getSingleResult();
        var jsonArticles = new ObjectMapper().readTree(json).path("stability").path("articlePublished");
        assertThat(jsonArticles.get(0).get("amount").asInt()).isZero();
        assertThat(jsonArticles.get(0).get("scientificAmount").isNull()).isTrue();
        assertThat(jsonArticles.get(0).get("publishedIssuesAmount").isNull()).isTrue();
        assertThat(jsonArticles.get(0).get("frequencyCode").asString()).isEqualTo("k");
        assertThat(jsonArticles.get(2).get("amount").isNull()).isTrue();
        assertThat(jsonArticles.get(2).get("frequencyCode").isNull()).isTrue();
    }

    @Test
    void persistsNewSurveyDataAndFourYearEntriesWhenQuestionnaireHasNone() {
        Journal journal = journal();
        Questionnaire questionnaire = questionnaire(journal, 2024, null);
        entityManager.flush();
        entityManager.clear();

        List<ArticlePublishedValues> articles = List.of(2021, 2022, 2023, 2024).stream()
                .map(year -> new ArticlePublishedValues(year, 5, 4, 20, 10, 4, JournalFrequency.q)).toList();
        service.updateStability(new Stability2024Record(journal.getId(), articles, "stability.xlsx", 3));
        entityManager.flush();
        entityManager.clear();

        SurveyData saved = entityManager.find(Questionnaire.class, questionnaire.getId()).getSurveyData();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSurvey().getStability().getArticlePublished()).extracting(ArticlePublished::getYear)
                .containsExactly(2021, 2022, 2023, 2024);
        assertThat(saved.getSurvey().getStability().getArticlePublished()).extracting(ArticlePublished::getAmount)
                .containsOnly(5);
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
        Survey survey = new Survey();
        survey.setEvaluationYear(2024);
        survey.setTimestamp("original-timestamp");
        survey.getStability().setArticlePublished(List.of(article(2024), article(2020), article(2021)));
        survey.getStability().setFirstIssueYear(1980);
        survey.getStability().setFrequencyId(4L);
        survey.getStability().setPublishingBreak(true);
        ScientistMetric metric = new ScientistMetric();
        metric.setUnique(30);
        survey.getCooperation().setAuthorMetric(metric);
        return survey;
    }

    private ArticlePublished article(int year) {
        ArticlePublished article = new ArticlePublished();
        article.setYear(year);
        article.setAmount(10);
        article.setScientificAmount(9);
        article.setReviewedAmount(8);
        article.setReceivedManuscriptCount(30);
        article.setRejectedManuscriptCount(5);
        article.setPublishedIssuesAmount(4);
        article.setFrequencyCode(JournalFrequency.q);
        return article;
    }
}
