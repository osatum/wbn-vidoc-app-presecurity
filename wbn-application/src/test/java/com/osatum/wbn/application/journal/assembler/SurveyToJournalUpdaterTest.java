package com.osatum.wbn.application.journal.assembler;

import com.osatum.wbn.application.dictionary.service.DictionaryAsjcCategoryService;
import com.osatum.wbn.application.dictionary.service.DictionaryCountryService;
import com.osatum.wbn.application.dictionary.service.DictionaryLanguageService;
import com.osatum.wbn.application.dictionary.service.DictionaryScientificCategoryService;
import com.osatum.wbn.application.dictionary.service.DictionaryTRCategoryService;
import com.osatum.wbn.domain.model.dictionary.DictionaryAsjcCategory;
import com.osatum.wbn.domain.model.dictionary.DictionaryCountry;
import com.osatum.wbn.domain.model.dictionary.DictionaryLanguage;
import com.osatum.wbn.domain.model.dictionary.DictionaryScientificCategory;
import com.osatum.wbn.domain.model.dictionary.DictionaryTRCategory;
import com.osatum.wbn.domain.model.journal.CategoryType;
import com.osatum.wbn.domain.model.journal.DescriptionType;
import com.osatum.wbn.domain.model.journal.IssnType;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.journal.JournalFrequency;
import com.osatum.wbn.domain.model.journal.JournalKind;
import com.osatum.wbn.domain.model.journal.PersonPosition;
import com.osatum.wbn.domain.model.journal.TitleCode;
import com.osatum.wbn.domain.model.survey.Description;
import com.osatum.wbn.domain.model.survey.Digitalization;
import com.osatum.wbn.domain.model.survey.Editorial;
import com.osatum.wbn.domain.model.survey.PublishingVersions;
import com.osatum.wbn.domain.model.survey.Stability;
import com.osatum.wbn.domain.model.survey.SupplementaryData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.ArticlePublished;
import com.osatum.wbn.domain.model.survey.model.Category;
import com.osatum.wbn.domain.model.survey.model.Country;
import com.osatum.wbn.domain.model.survey.model.Institution;
import com.osatum.wbn.domain.model.survey.model.Issn;
import com.osatum.wbn.domain.model.survey.model.JournalInstitution;
import com.osatum.wbn.domain.model.survey.model.JournalTitle;
import com.osatum.wbn.domain.model.survey.model.Language;
import com.osatum.wbn.domain.model.journal.PaidArticleType;
import com.osatum.wbn.domain.model.survey.model.Person;
import com.osatum.wbn.domain.model.survey.model.TitleType;
import com.osatum.wbn.domain.service.journal.JournalRatingPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SurveyToJournalUpdaterTest {

    private DictionaryCountryService dictionaryCountryService;
    private DictionaryScientificCategoryService dictionaryScientificCategoryService;
    private DictionaryAsjcCategoryService dictionaryAsjcCategoryService;
    private DictionaryTRCategoryService dictionaryTRCategoryService;
    private DictionaryLanguageService dictionaryLanguageService;
    private JournalRatingPolicyService journalRatingPolicyService;

    private SurveyToJournalUpdater updater;

    @BeforeEach
    void setUp() {
        dictionaryCountryService = mock(DictionaryCountryService.class);
        dictionaryScientificCategoryService = mock(DictionaryScientificCategoryService.class);
        dictionaryAsjcCategoryService = mock(DictionaryAsjcCategoryService.class);
        dictionaryTRCategoryService = mock(DictionaryTRCategoryService.class);
        dictionaryLanguageService = mock(DictionaryLanguageService.class);
        journalRatingPolicyService = mock(JournalRatingPolicyService.class);

        updater = new SurveyToJournalUpdater(
                dictionaryCountryService,
                dictionaryScientificCategoryService,
                dictionaryAsjcCategoryService,
                dictionaryTRCategoryService,
                dictionaryLanguageService,
                journalRatingPolicyService,
                new SurveyJournalEntityMapper()
        );
    }

    @Test
    void updateMapsSurveyDataToJournal() {
        DictionaryCountry dictionaryCountry = dictionaryCountry("pl");
        DictionaryLanguage english = dictionaryLanguage("en");
        DictionaryLanguage polish = dictionaryLanguage("pl");
        DictionaryScientificCategory scientificMain = dictionaryScientificCategory(1L);
        DictionaryScientificCategory scientificOther = dictionaryScientificCategory(2L);
        DictionaryAsjcCategory asjcMain = dictionaryAsjcCategory(3L);
        DictionaryTRCategory trOther = dictionaryTrCategory(4L);

        when(dictionaryCountryService.getByCode("pl")).thenReturn(dictionaryCountry);
        when(dictionaryLanguageService.getByCode("en")).thenReturn(english);
        when(dictionaryLanguageService.getByCode("pl")).thenReturn(polish);
        when(dictionaryScientificCategoryService.getById(1L)).thenReturn(scientificMain);
        when(dictionaryScientificCategoryService.getById(2L)).thenReturn(scientificOther);
        when(dictionaryAsjcCategoryService.getById(3L)).thenReturn(asjcMain);
        when(dictionaryTRCategoryService.getById(4L)).thenReturn(trOther);

        Journal journal = new Journal();
        Survey survey = surveyWithMappedData();

        updater.update(journal, survey);

        assertThat(journal.getKind()).isEqualTo(JournalKind.SCIENTIFIC);
        assertThat(journal.getCountry()).isSameAs(dictionaryCountry);
        assertThat(journal.getDoi()).isEqualTo("10.1234");
        assertThat(journal.getWebsite()).isEqualTo("https://journal.example");
        assertThat(journal.getElectronicVersion()).isTrue();
        assertThat(journal.getPrintedVersion()).isFalse();
        assertThat(journal.getFirstIssueYear()).isEqualTo(1999);
        assertThat(journal.getCirculation()).isEqualTo(250);
        assertThat(journal.getFrequency()).isEqualTo(JournalFrequency.q);

        assertThat(journal.getTitles())
                .hasSize(3)
                .extracting("code", "content")
                .containsExactlyInAnyOrder(
                        tuple(TitleCode.ENGLISH, "English title"),
                        tuple(TitleCode.ORIGINAL, "Original title"),
                        tuple(TitleCode.SHORT, "Short title")
                );

        assertThat(journal.getIssns())
                .hasSize(2)
                .extracting("type", "content", "yearFrom", "yearTo", "active")
                .containsExactlyInAnyOrder(
                        tuple(IssnType.P, "1234-5678", 2000, null, true),
                        tuple(IssnType.E, "8765-4321", 2010, 2020, true)
                );

        assertThat(journal.getScientificCategories())
                .hasSize(2)
                .extracting(category -> category.getCategory().getId(), category -> category.getCategoryType())
                .containsExactlyInAnyOrder(
                        tuple(1L, CategoryType.BASIC),
                        tuple(2L, CategoryType.OTHER)
                );

        assertThat(journal.getAjscCategories())
                .singleElement()
                .satisfies(category -> {
                    assertThat(category.getCategory()).isSameAs(asjcMain);
                    assertThat(category.getCategoryType()).isEqualTo(CategoryType.BASIC);
                });

        assertThat(journal.getTrCategories())
                .singleElement()
                .satisfies(category -> {
                    assertThat(category.getCategory()).isSameAs(trOther);
                    assertThat(category.getCategoryType()).isEqualTo(CategoryType.OTHER);
                });

        assertThat(journal.getInstitutions())
                .hasSize(2)
                .extracting("roleCode", "name", "department", "street", "postCode", "city", "countryCode", "email", "phone", "www")
                .containsExactlyInAnyOrder(
                        tuple(com.osatum.wbn.domain.model.journal.JournalInstitution.ROLE_PUBLISHER,
                                "Publisher", "Publishing Dept", "Publisher Street", "00-001", "Warsaw", "pl",
                                "publisher@example.com", "+48123123123", "https://publisher.example"),
                        tuple(com.osatum.wbn.domain.model.journal.JournalInstitution.ROLE_EDITORIAL_OFFICE,
                                "Editorial Office", "Editorial Dept", "Editorial Street", "00-002", "Krakow", "pl",
                                "editorial@example.com", "+48456456456", "https://editorial.example")
                );

        assertThat(journal.getPersons())
                .singleElement()
                .satisfies(person -> {
                    assertThat(person.getFirstName()).isEqualTo("Anna");
                    assertThat(person.getMiddleName()).isEqualTo("Maria");
                    assertThat(person.getLastName()).isEqualTo("Nowak");
                    assertThat(person.getTitle()).isEqualTo("prof.");
                    assertThat(person.getEmail()).isEqualTo("anna.nowak@example.com");
                    assertThat(person.getDepartment()).isEqualTo("Science Dept");
                    assertThat(person.getCountryCode()).isEqualTo("pl");
                    assertThat(person.getPersonPosition()).isEqualTo(PersonPosition.EDITOR_IN_CHIEF);
                });

        assertThat(journal.getDescriptions())
                .hasSize(2)
                .extracting("type", "content")
                .containsExactlyInAnyOrder(
                        tuple(DescriptionType.PL, "Opis PL"),
                        tuple(DescriptionType.EN, "Description EN")
                );

        assertThat(journal.getLanguages())
                .hasSize(2)
                .extracting(language -> language.getLanguage().getCode())
                .containsExactlyInAnyOrder("en", "pl");
    }

    @Test
    void updateIgnoresNullInputs() {
        Journal journal = new Journal();

        assertThatCode(() -> updater.update(null, new Survey())).doesNotThrowAnyException();
        assertThatCode(() -> updater.update(journal, null)).doesNotThrowAnyException();

        assertThat(journal.getTitles()).isNull();
        assertThat(journal.getIssns()).isNull();
    }

    @Test
    void updateSkipsInactiveUnsupportedAndUnresolvedItems() {
        Survey survey = new Survey();
        survey.setEvaluationYear(2026);

        com.osatum.wbn.domain.model.survey.Journal surveyJournal = new com.osatum.wbn.domain.model.survey.Journal();
        surveyJournal.setTitles(List.of(
                title(TitleType.CITATION, "Citation title", true),
                title(TitleType.ENGLISH, "Inactive English title", false)
        ));
        surveyJournal.setIssns(List.of(
                issn("2222-2222", IssnType.P, false, 2002, null)
        ));
        survey.setJournal(surveyJournal);

        SupplementaryData supplementaryData = new SupplementaryData();
        supplementaryData.setScientificCategories(List.of(category(99L, true), category(null, false)));
        supplementaryData.setAsjcCategories(List.of(category(98L, true)));
        supplementaryData.setTrCategories(List.of(category(97L, false)));
        survey.setSupplementaryData(supplementaryData);

        Stability stability = new Stability();
        stability.setArticlePublished(List.of(articlePublished(2025, JournalFrequency.m)));
        stability.setArticleLanguages(List.of(articleLanguage("unknown")));
        survey.setStability(stability);

        when(dictionaryScientificCategoryService.getById(99L)).thenReturn(null);
        when(dictionaryAsjcCategoryService.getById(98L)).thenReturn(null);
        when(dictionaryTRCategoryService.getById(97L)).thenReturn(null);
        when(dictionaryLanguageService.getByCode("unknown")).thenReturn(null);

        Journal journal = new Journal();

        updater.update(journal, survey);

        assertThat(journal.getTitles()).isEmpty();
        assertThat(journal.getIssns()).isEmpty();
        assertThat(journal.getScientificCategories()).isEmpty();
        assertThat(journal.getAjscCategories()).isEmpty();
        assertThat(journal.getTrCategories()).isEmpty();
        assertThat(journal.getLanguages()).isEmpty();
        assertThat(journal.getFrequency()).isNull();
    }

    @ParameterizedTest
    @NullSource
    @MethodSource("emptyInstitutions")
    void clearsEditorialInstitutionsWithoutRemovingPublishers(List<JournalInstitution> institutions) {
        Survey survey = new Survey();
        survey.setPublisher(null);
        survey.getEditorial().setRedactionInstitutions(institutions);
        Journal journal = new Journal();
        var publisher = new com.osatum.wbn.domain.model.journal.JournalInstitution();
        publisher.setJournal(journal);
        publisher.setRoleCode("P");
        var previousOffice = new com.osatum.wbn.domain.model.journal.JournalInstitution();
        previousOffice.setJournal(journal);
        previousOffice.setRoleCode("E");
        journal.setInstitutions(new HashSet<>(List.of(publisher, previousOffice)));

        updater.update(journal, survey);

        assertThat(journal.getInstitutions()).containsExactly(publisher);
        assertThat(publisher.getJournal()).isSameAs(journal);
        assertThat(previousOffice.getJournal()).isNull();
    }

    @Test
    void replacesEditorialInstitutionsIncludingNamelessEntriesAndRemovesMissingFieldValues() {
        Survey survey = new Survey();
        Institution institution = new Institution();
        institution.setEmail("office@example.org");
        survey.getEditorial().setRedactionInstitutions(List.of(journalInstitution('E', institution)));
        Journal journal = new Journal();
        var previousOffice = new com.osatum.wbn.domain.model.journal.JournalInstitution();
        previousOffice.setJournal(journal);
        previousOffice.setRoleCode("E");
        previousOffice.setName("Previous office");
        previousOffice.setWww("https://previous.example.org");
        journal.setInstitutions(new HashSet<>(List.of(previousOffice)));

        updater.update(journal, survey);

        assertThat(journal.getInstitutions()).singleElement().satisfies(office -> {
            assertThat(office.getJournal()).isSameAs(journal);
            assertThat(office.getRoleCode()).isEqualTo("E");
            assertThat(office.getEmail()).isEqualTo("office@example.org");
            assertThat(office).extracting("name", "department", "street", "postCode", "city", "countryCode", "phone", "www")
                    .containsOnlyNulls();
        });
        assertThat(previousOffice.getJournal()).isNull();
    }

    @ParameterizedTest
    @NullSource
    @MethodSource("emptyInstitutions")
    void clearsPublisherInstitutionsWithoutRemovingEditorialInstitutions(List<JournalInstitution> institutions) {
        Survey survey = new Survey();
        survey.setEditorial(null);
        survey.getPublisher().setPublisherInstitutions(institutions);
        Journal journal = new Journal();
        var editorialOffice = new com.osatum.wbn.domain.model.journal.JournalInstitution();
        editorialOffice.setJournal(journal);
        editorialOffice.setRoleCode("E");
        var previousPublisher = new com.osatum.wbn.domain.model.journal.JournalInstitution();
        previousPublisher.setJournal(journal);
        previousPublisher.setRoleCode("P");
        journal.setInstitutions(new HashSet<>(List.of(editorialOffice, previousPublisher)));

        updater.update(journal, survey);

        assertThat(journal.getInstitutions()).containsExactly(editorialOffice);
        assertThat(editorialOffice.getJournal()).isSameAs(journal);
        assertThat(previousPublisher.getJournal()).isNull();
    }

    @Test
    void replacesPublisherInstitutionsIncludingNamelessEntriesAndRemovesMissingFieldValues() {
        Survey survey = new Survey();
        Institution institution = new Institution();
        institution.setEmail("publisher@example.org");
        survey.getPublisher().setPublisherInstitutions(List.of(journalInstitution('P', institution)));
        Journal journal = new Journal();
        var previousPublisher = new com.osatum.wbn.domain.model.journal.JournalInstitution();
        previousPublisher.setJournal(journal);
        previousPublisher.setRoleCode("P");
        previousPublisher.setName("Previous publisher");
        previousPublisher.setWww("https://previous.example.org");
        journal.setInstitutions(new HashSet<>(List.of(previousPublisher)));

        updater.update(journal, survey);

        assertThat(journal.getInstitutions()).singleElement().satisfies(publisher -> {
            assertThat(publisher.getJournal()).isSameAs(journal);
            assertThat(publisher.getRoleCode()).isEqualTo("P");
            assertThat(publisher.getEmail()).isEqualTo("publisher@example.org");
            assertThat(publisher).extracting("name", "department", "street", "postCode", "city", "countryCode", "phone", "www")
                    .containsOnlyNulls();
        });
        assertThat(previousPublisher.getJournal()).isNull();
    }

    private static Stream<List<JournalInstitution>> emptyInstitutions() {
        return Stream.of(List.of());
    }

    private Survey surveyWithMappedData() {
        Survey survey = new Survey();
        survey.setEvaluationYear(2026);

        com.osatum.wbn.domain.model.survey.Journal surveyJournal = new com.osatum.wbn.domain.model.survey.Journal();
        surveyJournal.setTitles(List.of(
                title(TitleType.ENGLISH, "English title", true),
                title(TitleType.ORIGINAL, "Original title", true),
                title(TitleType.SHORT, "Short title", true),
                title(TitleType.CITATION, "Citation title", true),
                title(TitleType.ENGLISH, "Inactive title", false)
        ));
        surveyJournal.setIssns(List.of(
                issn("1234-5678", IssnType.P, true, 2000, null),
                issn("8765-4321", IssnType.E, true, 2010, 2020),
                issn("9999-9999", IssnType.P, false, 2015, null)
        ));
        survey.setJournal(surveyJournal);

        SupplementaryData supplementaryData = new SupplementaryData();
        supplementaryData.setKind(JournalKind.SCIENTIFIC);
        supplementaryData.setCountry(country("pl"));
        supplementaryData.setPaidArticle(PaidArticleType.FREE_ARTICLES);
        supplementaryData.setScientificCategories(List.of(category(1L, true), category(2L, false)));
        supplementaryData.setAsjcCategories(List.of(category(3L, true)));
        supplementaryData.setTrCategories(List.of(category(4L, false)));
        survey.setSupplementaryData(supplementaryData);

        Digitalization digitalization = new Digitalization();
        digitalization.setDoiPrefix("10.1234");
        digitalization.setWebsite("https://journal.example");
        survey.setDigitalization(digitalization);

        PublishingVersions publishingVersions = new PublishingVersions();
        publishingVersions.setElectronicVersion(true);
        publishingVersions.setPrintedVersion(false);
        publishingVersions.setCirculation(250);
        survey.setPublishingVersions(publishingVersions);

        Stability stability = new Stability();
        stability.setFirstIssueYear(1999);
        stability.setArticlePublished(List.of(
                articlePublished(2025, JournalFrequency.m),
                articlePublished(2026, JournalFrequency.q)
        ));
        stability.setArticleLanguages(List.of(
                articleLanguage("en"),
                articleLanguage("pl"),
                articleLanguage("en")
        ));
        survey.setStability(stability);

        Editorial editorial = new Editorial();
        editorial.setEditors(List.of(editor()));
        editorial.setRedactionInstitutions(List.of(
                journalInstitution('P', institution(
                        "Publisher", "Publishing Dept", "Publisher Street", "00-001", "Warsaw",
                        "publisher@example.com", "+48123123123", "https://publisher.example"
                )),
                journalInstitution('E', institution(
                        "Editorial Office", "Editorial Dept", "Editorial Street", "00-002", "Krakow",
                        "editorial@example.com", "+48456456456", "https://editorial.example"
                ))
        ));
        survey.setEditorial(editorial);
        survey.getPublisher().setPublisherInstitutions(List.of(journalInstitution('P', institution(
                "Publisher", "Publishing Dept", "Publisher Street", "00-001", "Warsaw",
                "publisher@example.com", "+48123123123", "https://publisher.example"
        ))));

        Description description = new Description();
        description.setDescriptionPL("Opis PL");
        description.setDescriptionEN("Description EN");
        survey.setDescription(description);

        return survey;
    }

    private JournalTitle title(TitleType type, String value, boolean active) {
        JournalTitle title = new JournalTitle();
        title.setTitleType(type);
        title.setTitle(value);
        title.setActive(active);
        return title;
    }

    private Issn issn(String value, IssnType type, boolean active, Integer yearFrom, Integer yearTo) {
        Issn issn = new Issn();
        issn.setIssn(value);
        issn.setType(type);
        issn.setActive(active);
        issn.setYearFrom(yearFrom);
        issn.setYearTo(yearTo);
        return issn;
    }

    private Country country(String code) {
        Country country = new Country();
        country.setCode(code);
        return country;
    }

    private Category category(Long id, boolean main) {
        Category category = new Category();
        category.setId(id);
        category.setMain(main);
        return category;
    }

    private ArticlePublished articlePublished(Integer year, JournalFrequency frequency) {
        ArticlePublished articlePublished = new ArticlePublished();
        articlePublished.setYear(year);
        articlePublished.setFrequencyCode(frequency);
        return articlePublished;
    }

    private com.osatum.wbn.domain.model.survey.model.ArticleLanguage articleLanguage(String code) {
        Language language = new Language();
        language.setCode(code);

        com.osatum.wbn.domain.model.survey.model.ArticleLanguage articleLanguage =
                new com.osatum.wbn.domain.model.survey.model.ArticleLanguage();
        articleLanguage.setLanguage(language);
        return articleLanguage;
    }

    private Person editor() {
        Person person = new Person();
        person.setFirstName("Anna");
        person.setMiddleName("Maria");
        person.setLastName("Nowak");
        person.setTitle("prof.");
        person.setEmail("anna.nowak@example.com");
        person.setDepartment("Science Dept");
        person.setCountry(country("pl"));
        person.setPersonPosition(PersonPosition.EDITOR_IN_CHIEF);
        return person;
    }

    private Institution institution(
            String name,
            String department,
            String street,
            String postCode,
            String city,
            String email,
            String phone,
            String www
    ) {
        Institution institution = new Institution();
        institution.setName(name);
        institution.setDepartment(department);
        institution.setStreet(street);
        institution.setPostCode(postCode);
        institution.setCity(city);
        institution.setCountry(country("pl"));
        institution.setEmail(email);
        institution.setPhone(phone);
        institution.setWww(www);
        return institution;
    }

    private JournalInstitution journalInstitution(Character role, Institution institution) {
        JournalInstitution journalInstitution = new JournalInstitution();
        journalInstitution.setInstitutionRole(role);
        journalInstitution.setInstitution(institution);
        return journalInstitution;
    }

    private DictionaryCountry dictionaryCountry(String code) {
        DictionaryCountry country = new DictionaryCountry();
        country.setCode(code);
        return country;
    }

    private DictionaryLanguage dictionaryLanguage(String code) {
        DictionaryLanguage language = new DictionaryLanguage();
        language.setCode(code);
        return language;
    }

    private DictionaryScientificCategory dictionaryScientificCategory(Long id) {
        DictionaryScientificCategory category = new DictionaryScientificCategory();
        category.setId(id);
        return category;
    }

    private DictionaryAsjcCategory dictionaryAsjcCategory(Long id) {
        DictionaryAsjcCategory category = new DictionaryAsjcCategory();
        category.setId(id);
        return category;
    }

    private DictionaryTRCategory dictionaryTrCategory(Long id) {
        DictionaryTRCategory category = new DictionaryTRCategory();
        category.setId(id);
        return category;
    }
}
