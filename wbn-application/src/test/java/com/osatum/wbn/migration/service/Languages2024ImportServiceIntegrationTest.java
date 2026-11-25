package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.dictionary.service.DictionaryLanguageService;
import com.osatum.wbn.domain.model.dictionary.DictionaryLanguage;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.ArticleLanguage;
import com.osatum.wbn.domain.model.survey.model.ArticlePublished;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import com.osatum.wbn.migration.service.Languages2024ImportService.ArticleLanguageValues;
import com.osatum.wbn.migration.service.Languages2024ImportService.Languages2024Record;
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
@ContextConfiguration(classes = Languages2024ImportServiceIntegrationTest.Configuration.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:languages-import;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@Transactional
class Languages2024ImportServiceIntegrationTest {
    @TestConfiguration
    @EnableAutoConfiguration
    @EntityScan("com.osatum.wbn.domain.model")
    @EnableJpaRepositories(basePackageClasses = QuestionnaireSpringDataRepository.class)
    @Import(Languages2024ImportService.class)
    static class Configuration {
        @Bean
        DictionaryLanguageService dictionaryLanguageService() {
            return mock(DictionaryLanguageService.class);
        }
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Languages2024ImportService service;

    @Autowired
    private DictionaryLanguageService dictionaryLanguageService;

    @BeforeEach
    void configureDictionary() {
        reset(dictionaryLanguageService);
        DictionaryLanguage language = new DictionaryLanguage();
        language.setCode("UK");
        language.setNamePl("Ukraiński");
        language.setNameEn("Ukrainian");
        language.setNameRu("Украинский");
        when(dictionaryLanguageService.getByCode("UK")).thenReturn(language);
    }

    @Test
    void persistsReplacementNamesAndJsonNullsOnlyForSelected2024Questionnaire() {
        Journal journal = journal(123L);
        Questionnaire current = questionnaire(journal, 2024, survey());
        Questionnaire previous = questionnaire(journal, 2023, survey());
        Questionnaire otherJournal = questionnaire(journal(456L), 2024, survey());
        entityManager.flush();
        entityManager.clear();

        Languages2024Record record = new Languages2024Record(123L, List.of(new ArticleLanguageValues("UK", null, 0)), "languages.xlsx", 2);
        service.updateLanguages(record);
        service.updateLanguages(record);
        entityManager.flush();
        entityManager.clear();

        SurveyData saved = entityManager.find(Questionnaire.class, current.getId()).getSurveyData();
        Survey updated = saved.getSurvey();
        assertThat(updated.getStability().getArticleLanguages()).singleElement().satisfies(article -> {
            assertThat(article.getLanguage()).extracting("code", "namePl", "nameEn", "nameRu")
                    .containsExactly("UK", "Ukraiński", "Ukrainian", "Украинский");
            assertThat(article).extracting("previousYear", "previousValue", "currentYear", "currentValue")
                    .containsExactly(2023, null, 2024, 0);
        });
        assertThat(updated.getStability().getArticlePublished()).isEqualTo(survey().getStability().getArticlePublished());
        assertThat(updated.getStability().getFirstIssueYear()).isEqualTo(1980);
        assertThat(updated.getCooperation()).isEqualTo(survey().getCooperation());
        assertThat(updated.getTimestamp()).isEqualTo("original");
        assertThat(entityManager.find(Questionnaire.class, previous.getId()).getSurveyData().getSurvey()).isEqualTo(survey());
        assertThat(entityManager.find(Questionnaire.class, otherJournal.getId()).getSurveyData().getSurvey()).isEqualTo(survey());
        String json = (String) entityManager.createNativeQuery("select survey from questionnaire_survey where id = :id")
                .setParameter("id", saved.getId()).getSingleResult();
        var languages = new ObjectMapper().readTree(json).path("stability").path("articleLanguages");
        assertThat(languages.size()).isEqualTo(1);
        var article = languages.get(0);
        assertThat(article.get("previousValue").isNull()).isTrue();
        assertThat(article.get("currentValue").asInt()).isZero();
        assertThat(article.path("language").get("nameRu").asString()).isEqualTo("Украинский");
    }

    @Test
    void persistsNewSurveyDataWhenQuestionnaireHasNone() {
        Questionnaire questionnaire = questionnaire(journal(123L), 2024, null);
        entityManager.flush();
        entityManager.clear();

        service.updateLanguages(new Languages2024Record(123L, List.of(new ArticleLanguageValues("UK", 10, null)), "languages.xlsx", 2));
        entityManager.flush();
        entityManager.clear();

        SurveyData saved = entityManager.find(Questionnaire.class, questionnaire.getId()).getSurveyData();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSurvey().getStability().getArticleLanguages()).singleElement()
                .satisfies(article -> assertThat(article.getCurrentValue()).isNull());
    }

    @Test
    void unknownCodeDoesNotReplacePersistedLanguages() {
        Questionnaire questionnaire = questionnaire(journal(123L), 2024, survey());
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> service.updateLanguages(new Languages2024Record(123L,
                List.of(new ArticleLanguageValues("UK", 10, 8), new ArticleLanguageValues("ZZ", 5, 4)), "languages.xlsx", 2)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ZZ");
        entityManager.clear();

        assertThat(entityManager.find(Questionnaire.class, questionnaire.getId()).getSurveyData().getSurvey()).isEqualTo(survey());
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
        ArticleLanguage language = new ArticleLanguage();
        language.setCurrentValue(999);
        survey.getStability().setArticleLanguages(List.of(language));
        ArticlePublished article = new ArticlePublished();
        article.setYear(2024);
        article.setAmount(42);
        survey.getStability().setArticlePublished(List.of(article));
        survey.getStability().setFirstIssueYear(1980);
        return survey;
    }
}
