package com.osatum.wbn.application.report.survey.assembler;

import com.osatum.wbn.application.dictionary.service.DictionaryCountryService;
import com.osatum.wbn.application.report.survey.dto.JournalReportDto;
import com.osatum.wbn.application.report.survey.dto.ScientificDatabaseReportDto;
import com.osatum.wbn.application.report.survey.dto.SurveyReportDto;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.journal.JournalKind;
import com.osatum.wbn.domain.model.journal.PaidArticleType;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.AuthorsFee;
import com.osatum.wbn.domain.model.survey.model.BooleanDouble;
import com.osatum.wbn.domain.model.survey.model.FeeDetail;
import com.osatum.wbn.domain.model.survey.model.FeeType;
import com.osatum.wbn.domain.model.survey.model.InfoReviewProcedure;
import com.osatum.wbn.domain.model.survey.model.JournalIndicator;
import com.osatum.wbn.domain.model.survey.model.CountrySpecificFeeSelection;
import com.osatum.wbn.domain.model.survey.model.FeeAuthorVariant;
import com.osatum.wbn.domain.model.survey.model.FeeSelectionSource;
import com.osatum.wbn.domain.model.survey.model.FeeSelectionStatus;
import com.osatum.wbn.domain.service.journal.AuthorFeeSelectionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SurveyReportDtoAssemblerTest {

    private final SurveyReportDtoAssembler assembler = new SurveyReportDtoAssembler(
            messageSource(),
            mock(DictionaryCountryService.class),
            new AuthorFeeSelectionPolicy()
    );

    @Test
    void mergesPartialDtosInOrder() {
        SurveyReportDto target = assembler.emptyDto();
        SurveyReportDto first = new SurveyReportDto();
        SurveyReportDto second = new SurveyReportDto();
        JournalReportDto firstJournal = new JournalReportDto();
        JournalReportDto secondJournal = new JournalReportDto();
        firstJournal.setJournalId("1");
        secondJournal.setJournalId("2");
        first.setJournals(List.of(firstJournal));
        second.setJournals(List.of(secondJournal));

        assembler.merge(target, first);
        assembler.merge(target, second);

        assertThat(target.getJournals())
                .extracting(JournalReportDto::getJournalId)
                .containsExactly("1", "2");
        assertThat(target.getScientificProfiles()).isEmpty();
        assertThat(target.getCooperationScientificCouncil()).isEmpty();
    }

    @Test
    void mapsEnumValuesWithMessageSource() {
        Survey survey = new Survey();
        survey.getSupplementaryData().setKind(JournalKind.SCIENTIFIC);
        survey.getSupplementaryData().setPaidArticle(PaidArticleType.PAID_ARTICLES);
        survey.getPublishingVersions().setPrintedVersion(true);
        survey.getPublishingVersions().setElectronicVersion(false);

        SurveyData surveyData = new SurveyData();
        surveyData.setSurvey(survey);

        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setSurveyData(surveyData);

        SurveyReportDto dto = assembler.toDto(new SurveyReportDtoParameters(List.of(questionnaire), null));

        assertThat(dto.getJournals()).hasSize(1);
        assertThat(dto.getJournals().getFirst().getKind()).isEqualTo("Czasopismo naukowe");
        assertThat(dto.getJournals().getFirst().getPaidArticles()).isEqualTo("Wybrane artyku\u0142y s\u0105 dost\u0119pne bezp\u0142atnie");
        assertThat(dto.getJournals().getFirst().getPrintVersion()).isEqualTo("TAK");
        assertThat(dto.getJournals().getFirst().getElectronicVersion()).isEqualTo("NIE");
    }

    @Test
    void mapsEnumValuesWithEnglishLocale() {
        Survey survey = new Survey();
        survey.getSupplementaryData().setKind(JournalKind.SCIENTIFIC);
        survey.getSupplementaryData().setPaidArticle(PaidArticleType.PAID_ARTICLES);
        survey.getPublishingVersions().setPrintedVersion(true);
        survey.getPublishingVersions().setElectronicVersion(false);

        SurveyData surveyData = new SurveyData();
        surveyData.setSurvey(survey);

        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setSurveyData(surveyData);

        SurveyReportDto dto = assembler.toDto(
                new SurveyReportDtoParameters(List.of(questionnaire), null, Locale.ENGLISH)
        );

        assertThat(dto.getJournals()).hasSize(1);
        assertThat(dto.getJournals().getFirst().getKind()).isEqualTo("Scientific journal");
        assertThat(dto.getJournals().getFirst().getPaidArticles()).isEqualTo("Some articles are paid");
        assertThat(dto.getJournals().getFirst().getPrintVersion()).isEqualTo("YES");
        assertThat(dto.getJournals().getFirst().getElectronicVersion()).isEqualTo("NO");
    }

    @Test
    void mapsPolishEnumValuesFromBaseBundleWhenSystemLocaleIsEnglish() {
        Locale previousDefault = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        try {
            Survey survey = new Survey();
            survey.getSupplementaryData().setKind(JournalKind.SCIENTIFIC);
            survey.getSupplementaryData().setPaidArticle(PaidArticleType.PAID_ARTICLES);

            SurveyData surveyData = new SurveyData();
            surveyData.setSurvey(survey);

            Questionnaire questionnaire = new Questionnaire();
            questionnaire.setSurveyData(surveyData);

            SurveyReportDto dto = assembler.toDto(
                    new SurveyReportDtoParameters(List.of(questionnaire), null, Locale.forLanguageTag("pl"))
            );

            assertThat(dto.getJournals()).hasSize(1);
            assertThat(dto.getJournals().getFirst().getKind()).isEqualTo("Czasopismo naukowe");
            assertThat(dto.getJournals().getFirst().getPaidArticles()).isEqualTo("Wybrane artyku\u0142y s\u0105 dost\u0119pne bezp\u0142atnie");
        } finally {
            Locale.setDefault(previousDefault);
        }
    }

    @Test
    void formatsScientificDatabaseIndicatorsWithTheirEstablishedPrecisionAndEmptyValue() {
        JournalIndicator sjr = new JournalIndicator();
        sjr.setPrevious(1.2);

        JournalIndicator tc = new JournalIndicator();
        tc.setCurrent(3.456);

        Survey survey = new Survey();
        survey.getDigitalization().getScientificQuality().setIndicatorSJR(sjr);
        survey.getDigitalization().getScientificQuality().setIndicatorTC(tc);

        SurveyData surveyData = new SurveyData();
        surveyData.setSurvey(survey);

        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setSurveyData(surveyData);

        SurveyReportDto dto = assembler.toDto(new SurveyReportDtoParameters(List.of(questionnaire), null));

        assertThat(dto.getScientificDatabases()).hasSize(1);
        ScientificDatabaseReportDto row = dto.getScientificDatabases().getFirst();
        assertThat(row.getIndicatorSjrPrevious()).isEqualTo("1,200");
        assertThat(row.getIndicatorSjrCurrent()).isEqualTo("-");
        assertThat(row.getIndicatorTcPrevious()).isEqualTo("-");
        assertThat(row.getIndicatorTcCurrent()).isEqualTo("3");
    }

    @Test
    void mapsReviewProcedureWithMessageSource() {
        InfoReviewProcedure procedure = new InfoReviewProcedure();
        procedure.setDoubleBlindReview(new BooleanDouble(true, 40.0));
        procedure.setSingleBlindReview(new BooleanDouble(true, null));

        Survey survey = new Survey();
        survey.getStandards().setInfoReviewProcedure(procedure);

        SurveyData surveyData = new SurveyData();
        surveyData.setSurvey(survey);

        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setSurveyData(surveyData);

        SurveyReportDto dto = assembler.toDto(new SurveyReportDtoParameters(List.of(questionnaire), null));

        assertThat(dto.getReviewStandards()).hasSize(1);
        assertThat(dto.getReviewStandards().getFirst().getInfoReviewProcedure())
                .isEqualTo("double blind (podw\u00F3jnie anonimowe recenzje) 40.0%; single blind (jednostronnie anonimowe recenzje)");
    }

    @Test
    void mapsPublicationFeeDetailDescriptionWithMessageSource() {
        FeeDetail feeDetail = new FeeDetail();
        feeDetail.setDescription("INTERNAL_AUTHORS");
        feeDetail.setMinValue(100);
        feeDetail.setMaxValue(200);
        feeDetail.setCurrencyCode("PLN");

        AuthorsFee fee = new AuthorsFee();
        fee.setFeeType(FeeType.VARIOUS_FEE);
        fee.setFeeDetails(List.of(feeDetail));

        Survey survey = new Survey();
        survey.getStandards().setPublicationFee(fee);

        SurveyData surveyData = new SurveyData();
        surveyData.setSurvey(survey);

        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setSurveyData(surveyData);

        SurveyReportDto dto = assembler.toDto(new SurveyReportDtoParameters(List.of(questionnaire), null));

        assertThat(dto.getAuthorInformation()).hasSize(1);
        assertThat(dto.getAuthorInformation().getFirst().getPublicationFeeType())
                .isEqualTo("R\u00F3\u017Cne op\u0142aty (Dla autor\u00F3w wewn\u0119trznych: 100-200 PLN)");
    }

    @ParameterizedTest
    @CsvSource({"pl, Dla autora z Polski", "en, For an author from Poland"})
    void reportsApplicableRangeAndKeepsCompleteTariff(String language, String label) {
        AuthorsFee fee = countrySpecificFee();
        String report = publicationFeeReport(fee, Locale.forLanguageTag(language));
        assertThat(report).contains(label + ": 1000-1500 PLN");
        assertThat(report).contains("500-700 PLN", "1000-1500 PLN");
    }

    @ParameterizedTest
    @CsvSource({
            "UNRESOLVED, Nie ustalono wariantu",
            "INCOMPLETE, Niekompletne dane",
            "INVALID, wymaga wyjaśnienia",
            "LEGACY, starszych danych"
    })
    void reportsSelectionProblemsWithoutAnApplicableAmount(FeeSelectionStatus status, String label) {
        AuthorsFee fee = countrySpecificFee();
        fee.setFeeSelectionStatus(status);
        assertThat(publicationFeeReport(fee, Locale.forLanguageTag("pl")))
                .contains(label).doesNotContain("Dla autora z Polski:");
    }

    private AuthorsFee countrySpecificFee() {
        CountrySpecificFeeSelection selection = new CountrySpecificFeeSelection();
        selection.setAuthorCountryCode("PL");
        selection.setOverrideVariant(FeeAuthorVariant.EXTERNAL_AUTHORS);
        selection.setEffectiveVariant(FeeAuthorVariant.EXTERNAL_AUTHORS);
        selection.setSource(FeeSelectionSource.JOURNAL_OVERRIDE);
        AuthorsFee fee = new AuthorsFee();
        fee.setFeeType(FeeType.COUNTRY_SPECIFIC_FEE);
        fee.setFeeSelectionStatus(FeeSelectionStatus.RESOLVED);
        fee.setCountrySpecificFeeSelection(selection);
        FeeDetail internal = new FeeDetail();
        internal.setDescription("INTERNAL_AUTHORS");
        internal.setMinValue(500);
        internal.setMaxValue(700);
        internal.setCurrencyCode("PLN");
        FeeDetail external = new FeeDetail();
        external.setDescription("EXTERNAL_AUTHORS");
        external.setMinValue(1000);
        external.setMaxValue(1500);
        external.setCurrencyCode("PLN");
        fee.setFeeDetails(List.of(external, internal));
        return fee;
    }

    private String publicationFeeReport(AuthorsFee fee, Locale locale) {
        Survey survey = new Survey();
        survey.getStandards().setPublicationFee(fee);
        SurveyData data = new SurveyData();
        data.setSurvey(survey);
        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setSurveyData(data);
        return assembler.toDto(new SurveyReportDtoParameters(List.of(questionnaire), null, locale))
                .getAuthorInformation().getFirst().getPublicationFeeType();
    }

    @Test
    void addsRowsWhenSectionsContainOnlyJournalIdAndEmptyValues() {
        SurveyData surveyData = new SurveyData();
        surveyData.setSurvey(new Survey());

        Journal journal = new Journal();
        journal.setId(123L);

        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setJournal(journal);
        questionnaire.setSurveyData(surveyData);

        SurveyReportDto dto = assembler.toDto(new SurveyReportDtoParameters(List.of(questionnaire), null));

        assertThat(dto.getJournals()).hasSize(1);
        assertThat(dto.getScientificProfiles()).hasSize(1);
        assertThat(dto.getEditorialAddresses()).hasSize(1);
        assertThat(dto.getEditorialBoards()).hasSize(1);
        assertThat(dto.getPublishers()).hasSize(1);
        assertThat(dto.getPublishingVersions()).hasSize(1);
        assertThat(dto.getPublishingStabilities()).hasSize(1);
        assertThat(dto.getPublishingStabilitiesLanguages()).hasSize(1);
        assertThat(dto.getReviewStandards()).hasSize(1);
        assertThat(dto.getAuthorInformation()).hasSize(1);
        assertThat(dto.getDigitalization()).hasSize(1);
        assertThat(dto.getScientificDatabases()).hasSize(1);
        assertThat(dto.getCooperation()).hasSize(1);
        assertThat(dto.getCooperationReviewers()).hasSize(1);
        assertThat(dto.getCooperationScientificCouncil()).hasSize(1);
        assertThat(dto.getEditorialAddresses().getFirst().getEditorial()).isEqualTo("-");
        assertThat(dto.getPublishingStabilitiesLanguages().getFirst().getLanguage()).isEqualTo("-");
        assertThat(dto.getCooperationReviewers().getFirst().getFullName()).isEqualTo("-");
        assertThat(dto.getCooperationScientificCouncil().getFirst().getFullName()).isEqualTo("-");
    }

    private static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("i18n/survey-report");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }
}
