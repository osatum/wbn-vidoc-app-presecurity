package com.osatum.wbn.application.journal.assembler;

import com.osatum.wbn.domain.model.journal.Cardinality;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.journal.JournalInstitution;
import com.osatum.wbn.domain.model.journal.Title;
import com.osatum.wbn.domain.model.journal.TitleCode;
import com.osatum.wbn.domain.model.journal.Person;
import com.osatum.wbn.domain.model.journal.PersonPosition;
import com.osatum.wbn.domain.model.journal.PaidArticle;
import com.osatum.wbn.domain.model.journal.PaidArticleType;
import com.osatum.wbn.domain.model.journal.JournalKind;
import com.osatum.wbn.domain.model.journal.ScientificCategory;
import com.osatum.wbn.domain.model.journal.CategoryType;
import com.osatum.wbn.domain.model.journal.Description;
import com.osatum.wbn.domain.model.journal.DescriptionType;
import com.osatum.wbn.domain.model.dictionary.DictionaryScientificCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig({JournalDecisionIdentityMapperImpl.class, TitleViewSelectorMapperImpl.class,
        IssnViewSelectorMapperImpl.class, CountryViewMapperImpl.class, LanguageViewMapperImpl.class,
        DescriptionViewMapperImpl.class, CardinalityViewMapperImpl.class,
        InstitutionViewSelectorMapperImpl.class, InstitutionViewMapperImpl.class,
        PersonViewSelectorMapperImpl.class, PersonViewMapperImpl.class,
        ScientificCategoryViewSelectorMapperImpl.class, TrCategoryViewSelectorMapperImpl.class,
        AsjcCategorySelectorMapperImpl.class})
@TestPropertySource(properties = "app.cover-path=/covers")
class JournalDecisionIdentityMapperTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.osatum.wbn.application.dictionary.service.DictionaryCountryService countries;

    @Autowired
    private JournalDecisionIdentityMapper mapper;

    @Test
    void endpointKeepsCatalogueSeparateFromTheRequestedSurveyAndSerializesLocalAvailability() throws Exception {
        var journal = new Journal(); journal.setId(123L);
        var title = new Title(); title.setCode(TitleCode.ORIGINAL); title.setContent("Current catalogue title");
        journal.setTitles(Set.of(title));
        var catalogueDescription = new Description(); catalogueDescription.setType(DescriptionType.EN); catalogueDescription.setContent("Current catalogue description");
        journal.setDescriptions(Set.of(catalogueDescription));
        var oldSurvey = new com.osatum.wbn.domain.model.survey.Survey(); oldSurvey.setEvaluationYear(2024);
        oldSurvey.getStandards().getAvgManuscriptTimes().setReview(12);
        var selectedSurvey = new com.osatum.wbn.domain.model.survey.Survey(); selectedSurvey.setEvaluationYear(2025);
        selectedSurvey.getStandards().getAvgManuscriptTimes().setReview(42);
        selectedSurvey.getStandards().getAvgManuscriptTimes().setInitialAssessment(-1);
        selectedSurvey.getDescription().setDescriptionEN("Historical survey description");
        selectedSurvey.getPublishingVersions().setPassword("not-for-browser");
        var questionnaires = new java.util.HashSet<com.osatum.wbn.domain.model.questionnaire.Questionnaire>();
        for (var survey : java.util.List.of(oldSurvey, selectedSurvey)) {
            var questionnaire = new com.osatum.wbn.domain.model.questionnaire.Questionnaire(); questionnaire.setYear(survey.getEvaluationYear());
            var data = new com.osatum.wbn.domain.model.questionnaire.SurveyData(); data.setSurvey(survey); questionnaire.setSurveyData(data); questionnaires.add(questionnaire);
        }
        journal.setQuestionnaires(questionnaires);
        var rating = new com.osatum.wbn.domain.model.journal.Rating(); rating.setYear(2025); rating.setRatingCode("ICV");
        rating.setRatingStatus(com.osatum.wbn.domain.entity.RatingStatus.INDEXED); rating.setValueNum(81.125); journal.setRatings(Set.of(rating));
        var repository = org.mockito.Mockito.mock(com.osatum.wbn.domain.repository.journal.JournalRepository.class);
        org.mockito.Mockito.when(repository.findById(123L)).thenReturn(java.util.Optional.of(journal));
        var configuration = new com.osatum.wbn.application.configuration.service.ConfigurationService();
        var profileReader = new com.osatum.wbn.application.journal.service.DecisionProfileReader(
                org.mockito.Mockito.mock(com.osatum.wbn.application.dictionary.service.DictionaryScientificCategoryService.class),
                org.mockito.Mockito.mock(com.osatum.wbn.application.dictionary.service.DictionaryRepositoryService.class));
        var fees = new com.osatum.wbn.application.journal.service.DecisionFeeReader();
        var service = new com.osatum.wbn.application.journal.service.JournalDecisionService(configuration,
                org.mockito.Mockito.mock(com.osatum.wbn.application.configuration.service.OperatingProfileService.class), repository, mapper,
                new com.osatum.wbn.application.journal.service.DecisionAnnualReader(profileReader, fees),
                new com.osatum.wbn.application.journal.service.DecisionRatingReader(configuration,
                        org.mockito.Mockito.mock(com.osatum.wbn.application.dictionary.service.DictionaryMniswDisciplineService.class)),
                com.osatum.wbn.application.journal.service.DecisionAnalyticsTestSupport.readyAnalytics());
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new com.osatum.wbn.web.journal.controller.JournalDecisionController(service)).build();
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/journal/journal/123/decision").param("surveyYear", "2025"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.identity.titleOriginal").value("Current catalogue title"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.identity.descriptions[0].content").value("Current catalogue description"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.annual.surveyYear").value(2025))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.annual.times.review.value").value(42))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.annual.times.initialAssessment.status").value("INVALID"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.mnisw.year").value(2024)).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("not-for-browser", "Historical survey description");
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(selectedSurvey.getStandards().getAvgManuscriptTimes().getInitialAssessment()).isEqualTo(-1);
    }

    @Test
    void mapsTheCatalogueAndDepositPreservingZeroAndMissingFields() {
        Journal journal = new Journal();
        journal.setId(123L);
        Title title = new Title();
        title.setCode(TitleCode.ORIGINAL);
        title.setContent("Catalogue title");
        journal.setTitles(Set.of(title));
        Cardinality cardinality = new Cardinality();
        cardinality.setNumberOfPublications(0);
        cardinality.setPercentFullText(0);
        cardinality.setPercentAbstracts(100);
        journal.setCardinality(cardinality);
        JournalInstitution publisher = new JournalInstitution();
        publisher.setName("Publisher");
        publisher.setEmail("publisher@example.test");
        publisher.setStreet("Publisher street");
        publisher.setRoleCode(JournalInstitution.ROLE_PUBLISHER);
        JournalInstitution editor = new JournalInstitution();
        editor.setName("Editorial office");
        editor.setRoleCode(JournalInstitution.ROLE_EDITORIAL_OFFICE);
        journal.setInstitutions(Set.of(publisher, editor));

        var result = mapper.toView(journal);
        assertThat(result.id()).isEqualTo(123L);
        assertThat(result.titleOriginal()).isEqualTo("Catalogue title");
        assertThat(result.publishers()).containsExactly("Publisher");
        assertThat(result.cardinality().getNumberOfPublications()).isZero();
        assertThat(result.cardinality().getPercentFullText()).isZero();
        assertThat(result.cardinality().getPercentAbstracts()).isEqualTo(100);
        assertThat(result.cardinality().getPercentKeywords()).isNull();
        assertThat(result.cover()).isNull();
        assertThat(result.titleEnglish()).isNull();
        assertThat(result.publisherDetails()).extracting(value -> value.getName()).containsExactly("Publisher");
        assertThat(result.publisherDetails().getFirst().getEmail()).isEqualTo("publisher@example.test");
        assertThat(result.publisherDetails().getFirst().getStreet()).isEqualTo("Publisher street");
        assertThat(result.editors()).extracting(value -> value.getName()).containsExactly("Editorial office");
        assertThat(journal.getInstitutions()).hasSize(2);
    }

    @Test
    void mapsTheSameCatalogueFieldsAndRoleSelectionsAsFullJournalDetails() {
        Journal journal = new Journal();
        Title shortTitle = new Title();
        shortTitle.setCode(TitleCode.SHORT);
        shortTitle.setContent("Short title");
        journal.setTitles(Set.of(shortTitle));
        journal.setKind(JournalKind.SCIENTIFIC);
        journal.setPrintedVersion(false);
        journal.setElectronicVersion(true);
        PaidArticle paid = new PaidArticle();
        paid.setPaidType(PaidArticleType.FREE_ARTICLES);
        paid.setValue(0);
        paid.setCurrencyCode("PLN");
        journal.setPaidArticle(paid);
        journal.setAffiliationName("Affiliated institution");
        Description description = new Description();
        description.setType(DescriptionType.EN);
        description.setContent("Complete description. ".repeat(100));
        journal.setDescriptions(Set.of(description));
        Person chief = person(PersonPosition.EDITOR_IN_CHIEF, "Chief");
        Person deputy = person(PersonPosition.DEPUTY_EDITOR_IN_CHIEF, "Deputy");
        Person secretary = person(PersonPosition.EDITORIAL_SECRETARY, "Secretary");
        journal.setPersons(Set.of(chief, deputy, secretary, person(PersonPosition.ASSISTANCE_EDITOR, "Other")));
        DictionaryScientificCategory dictionary = new DictionaryScientificCategory();
        dictionary.setNameEn("Science");
        ScientificCategory category = new ScientificCategory();
        category.setCategoryType(CategoryType.BASIC);
        category.setCategory(dictionary);
        journal.setScientificCategories(Set.of(category));

        var result = mapper.toView(journal);
        assertThat(result.titleShort()).isEqualTo("Short title");
        assertThat(result.kind()).isEqualTo(JournalKind.SCIENTIFIC);
        assertThat(result.printedVersion()).isFalse();
        assertThat(result.electronicVersion()).isTrue();
        assertThat(result.paidArticle().getValue()).isZero();
        assertThat(result.paidArticle().getCurrencyCode()).isEqualTo("PLN");
        assertThat(result.affiliationName()).isEqualTo("Affiliated institution");
        assertThat(result.descriptions().getFirst().getContent()).isEqualTo(description.getContent());
        assertThat(result.editorInChiefs()).extracting(value -> value.getLastName()).containsExactly("Chief");
        assertThat(result.deputyEditorInChiefs()).extracting(value -> value.getLastName()).containsExactly("Deputy");
        assertThat(result.editorialSecretaries()).extracting(value -> value.getLastName()).containsExactly("Secretary");
        assertThat(result.scientificCategory().getNameEn()).isEqualTo("Science");
        assertThat(result.scientificCategories()).isEmpty();
        assertThat(result.trCategory()).isNull();
        assertThat(result.ajscCategory()).isNull();
        assertThat(journal.getPersons()).hasSize(4);
    }

    private Person person(PersonPosition position, String lastName) {
        Person person = new Person();
        person.setPersonPosition(position);
        person.setFirstName("Test");
        person.setLastName(lastName);
        return person;
    }
}
