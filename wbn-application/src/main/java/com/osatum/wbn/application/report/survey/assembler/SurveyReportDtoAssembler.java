package com.osatum.wbn.application.report.survey.assembler;

import com.osatum.wbn.application.dictionary.service.DictionaryCountryService;
import com.osatum.wbn.application.report.survey.dto.*;
import com.osatum.wbn.domain.model.dictionary.DictionaryCountry;
import com.osatum.wbn.domain.model.journal.IssnType;
import com.osatum.wbn.domain.model.journal.PaidArticleType;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.survey.*;
import com.osatum.wbn.domain.model.survey.model.*;
import com.osatum.wbn.domain.service.journal.AuthorFeeSelectionPolicy;
import com.osatum.wbn.shared.assembler.AssemblerDto;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.osatum.wbn.application.report.survey.assembler.SurveyReportRowSupport.addIfHasSectionData;
import static com.osatum.wbn.application.report.survey.assembler.SurveyReportRowSupport.getEmpty;
import static com.osatum.wbn.application.report.survey.assembler.SurveyReportRowSupport.safeList;
import static com.osatum.wbn.application.report.survey.assembler.SurveyReportRowSupport.valueOrEmpty;
import static com.osatum.wbn.application.report.survey.assembler.SurveyReportRowSupport.valueOrNull;

@Service
@RequiredArgsConstructor
public class SurveyReportDtoAssembler implements AssemblerDto<List<Questionnaire>, SurveyReportDto, SurveyReportDtoParameters> {

    private static final Locale DEFAULT_REPORT_LOCALE = Locale.forLanguageTag("pl");
    private static final String ENUM_MESSAGE_PREFIX = "survey.report.enum.";
    private static final String REVIEW_PROCEDURE_MESSAGE_PREFIX = "survey.report.reviewProcedure.";
    private static final String FEE_DETAIL_DESCRIPTION_MESSAGE_PREFIX = "survey.report.feeDetail.description.";
    private static final String BOOLEAN_TRUE_MESSAGE_CODE = "survey.report.boolean.true";
    private static final String BOOLEAN_FALSE_MESSAGE_CODE = "survey.report.boolean.false";

    private final MessageSource messageSource;
    private final DictionaryCountryService dictionaryCountryService;
    private final AuthorFeeSelectionPolicy authorFeeSelectionPolicy;

    @Override
    public SurveyReportDto toDto(SurveyReportDtoParameters parameters) {
        SurveyReportDto dto = parameters.getDto() == null ? new SurveyReportDto() : parameters.getDto();
        initializeLists(dto);

        List<Questionnaire> questionnaires = parameters.getEntity();
        if (questionnaires == null) {
            return dto;
        }

        Locale locale = parameters.getLocale();
        questionnaires.stream()
                .filter(Objects::nonNull)
                .forEach(questionnaire -> appendQuestionnaire(dto, questionnaire, locale));

        return dto;
    }

    public SurveyReportDto emptyDto() {
        SurveyReportDto dto = new SurveyReportDto();
        initializeLists(dto);
        return dto;
    }

    public SurveyReportDto merge(SurveyReportDto target, SurveyReportDto source) {
        SurveyReportDto merged = target == null ? emptyDto() : target;
        initializeLists(merged);
        if (source == null) {
            return merged;
        }

        appendAll(merged.getJournals(), source.getJournals());
        appendAll(merged.getScientificProfiles(), source.getScientificProfiles());
        appendAll(merged.getEditorialAddresses(), source.getEditorialAddresses());
        appendAll(merged.getEditorialBoards(), source.getEditorialBoards());
        appendAll(merged.getPublishers(), source.getPublishers());
        appendAll(merged.getPublishingVersions(), source.getPublishingVersions());
        appendAll(merged.getPublishingStabilities(), source.getPublishingStabilities());
        appendAll(merged.getPublishingStabilitiesLanguages(), source.getPublishingStabilitiesLanguages());
        appendAll(merged.getReviewStandards(), source.getReviewStandards());
        appendAll(merged.getAuthorInformation(), source.getAuthorInformation());
        appendAll(merged.getDigitalization(), source.getDigitalization());
        appendAll(merged.getScientificDatabases(), source.getScientificDatabases());
        appendAll(merged.getCooperation(), source.getCooperation());
        appendAll(merged.getCooperationReviewers(), source.getCooperationReviewers());
        appendAll(merged.getCooperationScientificCouncil(), source.getCooperationScientificCouncil());
        return merged;
    }

    private <T> void appendAll(List<T> target, List<T> source) {
        if (source != null) {
            target.addAll(source);
        }
    }

    private void initializeLists(SurveyReportDto dto) {
        if (dto.getJournals() == null) {
            dto.setJournals(new ArrayList<>());
        }
        if (dto.getScientificProfiles() == null) {
            dto.setScientificProfiles(new ArrayList<>());
        }
        if (dto.getEditorialAddresses() == null) {
            dto.setEditorialAddresses(new ArrayList<>());
        }
        if (dto.getEditorialBoards() == null) {
            dto.setEditorialBoards(new ArrayList<>());
        }
        if (dto.getPublishers() == null) {
            dto.setPublishers(new ArrayList<>());
        }
        if (dto.getPublishingVersions() == null) {
            dto.setPublishingVersions(new ArrayList<>());
        }
        if (dto.getPublishingStabilities() == null) {
            dto.setPublishingStabilities(new ArrayList<>());
        }
        if (dto.getPublishingStabilitiesLanguages() == null) {
            dto.setPublishingStabilitiesLanguages(new ArrayList<>());
        }
        if (dto.getReviewStandards() == null) {
            dto.setReviewStandards(new ArrayList<>());
        }
        if (dto.getAuthorInformation() == null) {
            dto.setAuthorInformation(new ArrayList<>());
        }
        if (dto.getDigitalization() == null) {
            dto.setDigitalization(new ArrayList<>());
        }
        if (dto.getScientificDatabases() == null) {
            dto.setScientificDatabases(new ArrayList<>());
        }
        if (dto.getCooperation() == null) {
            dto.setCooperation(new ArrayList<>());
        }
        if (dto.getCooperationReviewers() == null) {
            dto.setCooperationReviewers(new ArrayList<>());
        }
        if (dto.getCooperationScientificCouncil() == null) {
            dto.setCooperationScientificCouncil(new ArrayList<>());
        }
    }

    private void appendQuestionnaire(SurveyReportDto dto, Questionnaire questionnaire, Locale locale) {
        Survey survey = survey(questionnaire);
        if (survey == null) {
            return;
        }

        String journalId = journalId(questionnaire);
        addIfHasSectionData(dto.getJournals(), journal(journalId, survey, locale));
        addIfHasSectionData(dto.getScientificProfiles(), scientificProfile(journalId, survey));
        appendEditorialAddresses(dto, journalId, survey);
        appendEditorialBoard(dto, journalId, survey, locale);
        appendPublishers(dto, journalId, survey);
        addIfHasSectionData(dto.getPublishingVersions(), publishingVersions(journalId, survey, locale));
        addIfHasSectionData(dto.getPublishingStabilities(), publishingStability(journalId, survey, locale));
        appendStabilityLanguages(dto.getPublishingStabilitiesLanguages(), publishingStabilityLanguages(journalId, survey, locale));
        addIfHasSectionData(dto.getReviewStandards(), reviewStandards(journalId, survey, locale));
        addIfHasSectionData(dto.getAuthorInformation(), authorInformation(journalId, survey, locale));
        addIfHasSectionData(dto.getDigitalization(), digitalization(journalId, survey, locale));
        addIfHasSectionData(dto.getScientificDatabases(), scientificDatabase(journalId, survey, locale));
        addIfHasSectionData(dto.getCooperation(), cooperation(journalId, survey));
        appendCooperationPeople(dto, journalId, survey, locale);
    }

    private void appendStabilityLanguages(List<PublishingStabilityLanguagesReportDto> publishingStabilitiesLanguages, List<PublishingStabilityLanguagesReportDto> dtos) {
        if (dtos != null) {
            dtos.forEach(dto -> addIfHasSectionData(publishingStabilitiesLanguages, dto));
        }
    }

    private Survey survey(Questionnaire questionnaire) {
        if (questionnaire.getSurveyData() == null) {
            return null;
        }
        return questionnaire.getSurveyData().getSurvey();
    }

    private String journalId(Questionnaire questionnaire) {
        if (questionnaire.getJournal() == null || questionnaire.getJournal().getId() == null) {
            return null;
        }
        return questionnaire.getJournal().getId().toString();
    }

    private JournalReportDto journal(String journalId, Survey survey, Locale locale) {
        JournalReportDto dto = new JournalReportDto();
        dto.setJournalId(journalId);
        dto.setPissn(issn(survey.getJournal(), IssnType.P));
        dto.setEissn(issn(survey.getJournal(), IssnType.E));
        dto.setTitleOrginal(title(survey.getJournal(), TitleType.ORIGINAL));
        dto.setTitleEnglish(title(survey.getJournal(), TitleType.ENGLISH));
        dto.setTitleShort(title(survey.getJournal(), TitleType.SHORT));
        if (survey.getDigitalization() != null){
            dto.setWebsite(survey.getDigitalization().getWebsite());
        }
        dto.setCountry(countryName(survey.getSupplementaryData().getCountry()));
        dto.setKind(format(survey.getSupplementaryData().getKind(), locale));
        dto.setPrintVersion(formatBoolean(survey.getPublishingVersions().getPrintedVersion(), locale));
        dto.setElectronicVersion(formatBoolean(survey.getPublishingVersions().getElectronicVersion(), locale));
        dto.setPaidArticles(formatPaidArticle(survey.getSupplementaryData(), locale));
        return dto;
    }

    private String formatPaidArticle(SupplementaryData supplementaryData, Locale locale) {
        if (supplementaryData.getPaidArticle() == null) {
            return getEmpty();
        }
        String value = format(supplementaryData.getPaidArticle(), locale);
        if (PaidArticleType.FREE_ARTICLES.equals(supplementaryData.getPaidArticle())){
            return value;
        }
        ArticleCost articleCost = supplementaryData.getArticleCost();
        if (articleCost == null || (articleCost.getValue() == null && articleCost.getCurrencyCode() == null)) {
            return value;
        }
        return String.format("%s (%s %s)", value, formatInteger(articleCost.getValue()), valueOrEmpty(articleCost.getCurrencyCode()));
    }


    private ScientificProfileReportDto scientificProfile(String journalId, Survey survey) {
        SupplementaryData data = survey.getSupplementaryData();
        ScientificProfileReportDto dto = new ScientificProfileReportDto();
        dto.setJournalId(journalId);
        dto.setMainScientificProfile(categoryName(data.getMainScienceArea(), data.getScientificCategories(), true));
        dto.setOtherScientificProfiles(categoryNames(data.getScientificCategories(), false));
        dto.setMainTrCategory(categoryNames(data.getTrCategories(), true));
        dto.setOtherTrCategories(categoryNames(data.getTrCategories(), false));
        dto.setMainAsjcCategory(categoryNames(data.getAsjcCategories(), true));
        dto.setOtherAsjcCategories(categoryNames(data.getAsjcCategories(), false));
        return dto;
    }

    private void appendEditorialAddresses(SurveyReportDto dto, String journalId, Survey survey) {
        List<JournalInstitution> institutions = safeList(survey.getEditorial().getRedactionInstitutions());
        if (institutions.isEmpty()) {
            addIfHasSectionData(dto.getEditorialAddresses(), newEditorialAddress(journalId, null));
            return;
        }
        institutions.forEach(institution -> addIfHasSectionData(dto.getEditorialAddresses(), newEditorialAddress(journalId, institution)));
    }

    private EditorialAddressReportDto newEditorialAddress(String journalId, JournalInstitution journalInstitution) {
        Institution institution = journalInstitution == null ? null : journalInstitution.getInstitution();
        EditorialAddressReportDto dto = new EditorialAddressReportDto();
        dto.setJournalId(journalId);
        dto.setEditorial(institution == null ? null : institutionName(institution));
        dto.setAddress(institution == null ? null : address(institution));
        dto.setPhone(institution == null ? null : institution.getPhone());
        dto.setEmail(institution == null ? null : institution.getEmail());
        return dto;
    }

    private void appendEditorialBoard(SurveyReportDto dto, String journalId, Survey survey, Locale locale) {
        List<Person> editors = safeList(survey.getEditorial().getEditors());
        if (editors.isEmpty()) {
            EditorialBoardReportDto row = new EditorialBoardReportDto();
            row.setJournalId(journalId);
            addIfHasSectionData(dto.getEditorialBoards(), row);
            return;
        }
        editors.forEach(editor -> {
            EditorialBoardReportDto row = new EditorialBoardReportDto();
            row.setJournalId(journalId);
            row.setFunction(format(editor.getPersonPosition(), locale));
            row.setTitle(editor.getTitle());
            row.setFullName(fullName(editor));
            row.setInstitution(editor.getDepartment());
            addIfHasSectionData(dto.getEditorialBoards(), row);
        });
    }

    private void appendPublishers(SurveyReportDto dto, String journalId, Survey survey) {
        List<JournalInstitution> institutions = safeList(survey.getPublisher().getPublisherInstitutions());
        if (institutions.isEmpty()) {
            addIfHasSectionData(dto.getPublishers(), newPublisher(journalId, null));
            return;
        }
        institutions.forEach(institution -> addIfHasSectionData(dto.getPublishers(), newPublisher(journalId, institution)));
    }

    private PublisherReportDto newPublisher(String journalId, JournalInstitution journalInstitution) {
        Institution institution = journalInstitution == null ? null : journalInstitution.getInstitution();
        PublisherReportDto dto = new PublisherReportDto();
        dto.setJournalId(journalId);
        dto.setPublisher(institution == null ? null : institutionName(institution));
        dto.setAddress(institution == null ? null : address(institution));
        dto.setPhone(institution == null ? null : institution.getPhone());
        dto.setEmail(institution == null ? null : institution.getEmail());
        return dto;
    }

    private PublishingVersionsReportDto publishingVersions(String journalId, Survey survey, Locale locale) {
        PublishingVersions data = survey.getPublishingVersions();
        PublishingVersionsReportDto dto = new PublishingVersionsReportDto();
        dto.setJournalId(journalId);
        dto.setElectronicVersion(formatBoolean(data.getElectronicVersion(), locale));
        if (Boolean.FALSE.equals(data.getElectronicVersion())) {
            dto.setArticlesFormats(getNoData(locale));
            dto.setElectronicVersionWww(getNoData(locale));
            dto.setElectronicVersionInformation(getNoData( locale));
            dto.setElectronicArticleMetadata(getNoData( locale));
            dto.setAop(getNoData(locale));
        } else {
            dto.setArticlesFormats(join(data.getArticlesFormats(), locale));
            dto.setElectronicVersionWww(data.getElectronicVersionWww());
            dto.setElectronicVersionInformation(join(data.getElectronicVersionInformation(), locale));
            dto.setElectronicArticleMetadata(join(data.getElectronicArticleMetadata(), locale));
            dto.setAop(formatBoolean(data.getAop(), locale));
        }


        dto.setPrintedVersion(formatBoolean(data.getPrintedVersion(), locale));
        if (Boolean.FALSE.equals(data.getPrintedVersion())) {

            dto.setCoverInformation(getNoData( locale));
            dto.setCirculation(getNoData(locale));
            dto.setPrintedArticleMetadata(getNoData( locale));
        } else {

            dto.setCoverInformation(join(data.getCoverInformation(), locale));
            dto.setCirculation(formatInteger(data.getCirculation()));
            dto.setPrintedArticleMetadata(join(data.getPrintedArticleMetadata(), locale));
        }

        if (Boolean.FALSE.equals(data.getElectronicVersion()) || Boolean.FALSE.equals(data.getPrintedVersion())){
            dto.setPublishingPolicy(getNoData( locale));
            dto.setElectronicVersionsPercentage(getNoData( locale));
        } else {
            dto.setPublishingPolicy(format(data.getPublishingPolicy(), locale));
            dto.setElectronicVersionsPercentage(formatIndicatorInteger(data.getElectronicVersionsPercentage(), locale));
        }


        return dto;
    }

    private PublishingStabilityReportDto publishingStability(String journalId, Survey survey, Locale locale) {
        Stability data = survey.getStability();
        PublishingStabilityReportDto dto = new PublishingStabilityReportDto();
        dto.setJournalId(journalId);
        setArticlePublished(dto, 1, articlePublished(data, 0), locale);
        setArticlePublished(dto, 2, articlePublished(data, 1), locale);
        setArticlePublished(dto, 3, articlePublished(data, 2), locale);
        setArticlePublished(dto, 4, articlePublished(data, 3), locale);
        //dto.setArticleLanguage1PreviousValue(articleLanguage(data, 0) == null ? null : articleLanguage(data, 0).getPreviousValue());
        //dto.setArticleLanguage2PreviousValue(articleLanguage(data, 1) == null ? null : articleLanguage(data, 1).getPreviousValue());
        dto.setNd(null);
        dto.setConcatenateNumberPrevious(formatBoolean(data.getConcatenateNumber(), locale));
        dto.setPublishingBreakPrevious(formatBoolean(data.getPublishingBreak(), locale));
        //dto.setArticleLanguage1CurrentValue(articleLanguage(data, 0) == null ? null : articleLanguage(data, 0).getCurrentValue());
        //dto.setArticleLanguage2CurrentValue(articleLanguage(data, 1) == null ? null : articleLanguage(data, 1).getCurrentValue());
        dto.setEnglishTitleAndAbstractInAllArticlesPercentage(data.getEnglishTitleAndAbstractInAllArticlesPercentage());
        dto.setConcatenateNumber(formatBoolean(data.getConcatenateNumber(), locale));
        dto.setPublishingBreak(formatBoolean(data.getPublishingBreak(), locale));
        return dto;
    }

    private List<PublishingStabilityLanguagesReportDto> publishingStabilityLanguages(String journalId, Survey survey, Locale locale) {
        Stability data = survey.getStability();

        List<PublishingStabilityLanguagesReportDto> dtos = new ArrayList<>();
        if (data.getArticleLanguages() == null || data.getArticleLanguages().isEmpty()) {
            PublishingStabilityLanguagesReportDto dto = new PublishingStabilityLanguagesReportDto();
            dto.setJournalId(journalId);
            dtos.add(dto);
            return dtos;
        }

        for (ArticleLanguage articleLanguage : data.getArticleLanguages()) {
            PublishingStabilityLanguagesReportDto dto = new PublishingStabilityLanguagesReportDto();
            dto.setJournalId(journalId);
            dto.setLanguage(languageName(articleLanguage.getLanguage(), locale));
            dto.setPreviousValue(formatInteger(articleLanguage.getPreviousValue()));
            dto.setCurrentValue(formatInteger(articleLanguage.getCurrentValue()));
            dtos.add(dto);
        }

        return dtos;
    }



    private void setArticlePublished(PublishingStabilityReportDto dto, int index, ArticlePublished value, Locale locale) {
        String amount = formatInteger(value == null ? null : value.getAmount());
        String scientificAmount = formatInteger(value == null ? null : value.getScientificAmount());
        String received = formatInteger(value == null ? null : value.getReceivedManuscriptCount());
        String rejected = formatInteger(value == null ? null : value.getRejectedManuscriptCount());
        String issues = formatInteger(value == null ? null : value.getPublishedIssuesAmount());
        String frequency = value == null ? null : format(value.getFrequencyCode(), locale);

        switch (index) {
            case 1 -> {
                dto.setArticlePublished1Amount(amount);
                dto.setArticlePublished1ScientificAmount(scientificAmount);
                dto.setArticlePublished1ReceivedManuscriptCount(received);
                dto.setArticlePublished1RejectedManuscriptCount(rejected);
                dto.setArticlePublished1PublishedIssuesAmount(issues);
                dto.setArticlePublished1FrequencyCode(frequency);
            }
            case 2 -> {
                dto.setArticlePublished2Amount(amount);
                dto.setArticlePublished2ScientificAmount(scientificAmount);
                dto.setArticlePublished2ReceivedManuscriptCount(received);
                dto.setArticlePublished2RejectedManuscriptCount(rejected);
                dto.setArticlePublished2PublishedIssuesAmount(issues);
                dto.setArticlePublished2FrequencyCode(frequency);
            }
            case 3 -> {
                dto.setArticlePublished3Amount(amount);
                dto.setArticlePublished3ScientificAmount(scientificAmount);
                dto.setArticlePublished3ReceivedManuscriptCount(received);
                dto.setArticlePublished3RejectedManuscriptCount(rejected);
                dto.setArticlePublished3PublishedIssuesAmount(issues);
                dto.setArticlePublished3FrequencyCode(frequency);
            }
            case 4 -> {
                dto.setArticlePublished4Amount(amount);
                dto.setArticlePublished4ScientificAmount(scientificAmount);
                dto.setArticlePublished4ReceivedManuscriptCount(received);
                dto.setArticlePublished4RejectedManuscriptCount(rejected);
                dto.setArticlePublished4PublishedIssuesAmount(issues);
                dto.setArticlePublished4FrequencyCode(frequency);
            }
            default -> {
            }
        }
    }

    private ReviewStandardsReportDto reviewStandards(String journalId, Survey survey, Locale locale) {
        Standards data = survey.getStandards();
        ReviewStandardsReportDto dto = new ReviewStandardsReportDto();
        dto.setJournalId(journalId);
        dto.setLinkToReviewProcedure(data.getLinkToReviewProcedure());
        dto.setInfoReviewProcedure(reviewProcedure(data.getInfoReviewProcedure(), locale));
        dto.setPublishedReviewersList(formatBoolean(has(data.getReviewRules(), ReviewRule.PUBLISHED_REVIEWERS_LIST), locale));
        dto.setMin2Reviewers(formatBoolean(has(data.getReviewRules(), ReviewRule.MIN_2_REVIEWERS), locale));
        dto.setPublicForm(formatBoolean(has(data.getReviewRules(), ReviewRule.PUBLIC_FORM), locale));
        dto.setPublicCriteria(formatBoolean(has(data.getReviewRules(), ReviewRule.PUBLIC_CRITERIA), locale));
        dto.setReviewerStatement(formatBoolean(has(data.getReviewRules(), ReviewRule.REVIEWER_STATEMENT), locale));
        dto.setPrepareReview(formatBoolean(has(data.getReviewRules(), ReviewRule.PREPARE_REVIEW), locale));
        return dto;
    }

    private AuthorInformationReportDto authorInformation(String journalId, Survey survey, Locale locale) {
        Standards standards = survey.getStandards();
        Digitalization digitalization = survey.getDigitalization();
        AuthorInformationReportDto dto = new AuthorInformationReportDto();
        dto.setJournalId(journalId);
        dto.setInstructionLink(standards.getInstructionLink());
        dto.setArticleStructureInstruction(formatBoolean(has(standards.getAuthorInstructions(), AuthorInstructionType.ARTICLE_STRUCTURE), locale));
        dto.setLiteratureInstruction(formatBoolean(has(standards.getAuthorInstructions(), AuthorInstructionType.LITERATURE_INSTRUCTION), locale));
        dto.setOriginalKindStatement(formatBoolean(has(standards.getStatements(), StatementType.ORIGINAL_KIND), locale));
        dto.setAuthorContributionStatement(formatBoolean(has(standards.getStatements(), StatementType.AUTHOR_CONTRIBUTION), locale));
        dto.setFinanceSourceStatement(formatBoolean(has(standards.getStatements(), StatementType.FINANCE_SOURCE), locale));
        dto.setOrcidRequired(formatNullBoolean(standards.getOrcidRequired(), locale));
        dto.setCitationsStyle(join(digitalization.getCitationsStyle(), locale));
        dto.setManuscriptProcessingTimeUrl(standards.getManuscriptProcessingTimeUrl());
        dto.setAvgInitialAssessment(formatInteger(standards.getAvgManuscriptTimes().getInitialAssessment()));
        dto.setAvgReview(formatInteger(standards.getAvgManuscriptTimes().getReview()));
        dto.setAvgPublication(formatInteger(standards.getAvgManuscriptTimes().getPublication()));
        dto.setManuscriptProcessingCostUrl(standards.getManuscriptProcessingCostUrl());
        dto.setSubmissionFeeType(publicationFeeType(standards.getSubmissionFee(), locale));
        dto.setPublicationFeeType(publicationFeeType(standards.getPublicationFee(), locale));
        dto.setOpenAccessFeeType(publicationFeeType(standards.getOpenAccessFee(), locale));
        dto.setApcFeeType(publicationFeeType(standards.getApc(), locale));
        dto.setRapidReviewFeeType(publicationFeeType(standards.getRapidReview(), locale));
        return dto;
    }

    private DigitalizationReportDto digitalization(String journalId, Survey survey, Locale locale) {
        Digitalization data = survey.getDigitalization();
        InfoWebsite infoWebsite = data.getInfoWebsite();
        DigitalizationReportDto dto = new DigitalizationReportDto();
        dto.setJournalId(journalId);
        dto.setWebsite(data.getWebsite());
        dto.setWebsiteLanguages(joinLanguages(data.getWebsiteLanguages()));
        dto.setDoiPrefix(data.getDoiPrefix());
        dto.setDoiType(format(data.getDoiType(), locale));
        dto.setInfoWebsiteTitles(formatBoolean(infoWebsiteValue(infoWebsite, infoWebsite == null ? null : infoWebsite.getTitles()), locale));
        dto.setInfoWebsiteIssn(formatBoolean(infoWebsiteValue(infoWebsite, infoWebsite == null ? null : infoWebsite.getIssn()), locale));
        dto.setInfoWebsiteDirectors(formatBoolean(infoWebsiteValue(infoWebsite, infoWebsite == null ? null : infoWebsite.getDirectors()), locale));
        dto.setInfoWebsiteEditOfficeInfo(formatBoolean(infoWebsiteValue(infoWebsite, infoWebsite == null ? null : infoWebsite.getEditOfficeInfo()), locale));
        dto.setArticlePublishedLicense(format(data.getArticlePublishedLicense(), locale));
        dto.setSystemForPublishedArticlesName(data.getSystemForPublishedArticlesName());
        dto.setSocialMediaIds(join(data.getSocialMedias().getIds(), locale));
        dto.setPublishingToolIds(join(data.getPublishingTools().getIds(), locale));
        dto.setCitationsTools(join(data.getCitationsTools(), locale));
        dto.setAntiPlagiarismSystem(formatBoolean(has(data.getEditorialTools(), EditorialTool.ANTI_PLAGIARISM_SYSTEM), locale));
        dto.setStatistics(formatBoolean(has(data.getEditorialTools(), EditorialTool.STATISTICS), locale));
        dto.setNewsletters(formatBoolean(has(data.getEditorialTools(), EditorialTool.NEWSLETTERS), locale));
        return dto;
    }

    private ScientificDatabaseReportDto scientificDatabase(String journalId, Survey survey, Locale locale) {
        ScientificQuality quality = survey.getDigitalization().getScientificQuality();
        ScientificDatabaseReportDto dto = new ScientificDatabaseReportDto();
        dto.setJournalId(journalId);
        dto.setExternalRepositoriesGeneral(join(survey.getDigitalization().getExternalRepositories().getGeneral(), locale));
        dto.setIndicatorSjrPrevious(formatd3Indicator(previous(quality.getIndicatorSJR()), locale));
        dto.setIndicatorTcPrevious(format0Indicator(previous(quality.getIndicatorTC()), locale));
        dto.setIndicatorSnipPrevious(formatd3Indicator(previous(quality.getIndicatorSNIP()), locale));
        dto.setIndicatorCsPrevious(format1Indicator(previous(quality.getIndicatorCS()), locale));
        dto.setIndicatorIfPrevious(format1Indicator(previous(quality.getIndicatorIF()), locale));
        dto.setIndicatorAiPrevious(formatd3Indicator(previous(quality.getIndicatorAI()), locale));
        dto.setIndicatorCnciPrevious(formatIndicator(previous(quality.getIndicatorCNCI()), locale));

        dto.setIndicatorSjrCurrent(formatd3Indicator(current(quality.getIndicatorSJR()), locale));
        dto.setIndicatorTcCurrent(format0Indicator(current(quality.getIndicatorTC()), locale));
        dto.setIndicatorSnipCurrent(formatd3Indicator(current(quality.getIndicatorSNIP()), locale));
        dto.setIndicatorCsCurrent(format1Indicator(current(quality.getIndicatorCS()), locale));
        dto.setIndicatorIfCurrent(format1Indicator(current(quality.getIndicatorIF()), locale));
        dto.setIndicatorAiCurrent(formatd3Indicator(current(quality.getIndicatorAI()), locale));
        dto.setIndicatorCnciCurrent(formatIndicator(current(quality.getIndicatorCNCI()), locale));
        return dto;
    }

    private CooperationReportDto cooperation(String journalId, Survey survey) {
        Cooperation data = survey.getCooperation();
        CooperationReportDto dto = new CooperationReportDto();
        dto.setJournalId(journalId);
        fillMetric(dto, data.getAuthorMetric(), "author");
        fillMetric(dto, data.getReviewerMetric(), "reviewer");
        fillMetric(dto, data.getDirectorsMetric(), "directors");
        return dto;
    }

    private void fillMetric(CooperationReportDto dto, ScientistMetric metric, String type) {
        Integer unique = metric == null ? null : metric.getUnique();
        Double external = metric == null ? null : metric.getExternalAffiliationPercentage();
        Double foreign = metric == null ? null : metric.getForeignAffiliationPercentage();

        switch (type) {
            case "author" -> {
                dto.setAuthorUnique(formatInteger(unique));
                dto.setAuthorExternalAffiliationPercentage(external);
                dto.setAuthorForeignAffiliationPercentage(foreign);
            }
            case "reviewer" -> {
                dto.setReviewerUnique(formatInteger(unique));
                dto.setReviewerExternalAffiliationPercentage(external);
                dto.setReviewerForeignAffiliationPercentage(foreign);
            }
            case "directors" -> {
                dto.setDirectorsUnique(formatInteger(unique));
                dto.setDirectorsExternalAffiliationPercentage(external);
                dto.setDirectorsForeignAffiliationPercentage(foreign);
            }
            default -> {
            }
        }
    }

    private void appendCooperationPeople(SurveyReportDto dto, String journalId, Survey survey, Locale locale) {
        List<Cooperator> reviewers = safeList(survey.getCooperation().getReviewersList());
        if (reviewers.isEmpty()) {
            CooperationReviewerReportDto row = new CooperationReviewerReportDto();
            row.setJournalId(journalId);
            addIfHasSectionData(dto.getCooperationReviewers(), row);
        }
        reviewers.forEach(reviewer -> {
            CooperationReviewerReportDto row = new CooperationReviewerReportDto();
            row.setJournalId(journalId);
            row.setFullName(reviewer.getFullName());
            row.setAffiliation(reviewer.getAffiliation());
            row.setCountry(findCountry(reviewer.getCountry(), locale));
            row.setEmail(reviewer.getEmail());
            addIfHasSectionData(dto.getCooperationReviewers(), row);
        });

        List<Cooperator> directors = safeList(survey.getCooperation().getDirectorsList());
        if (directors.isEmpty()) {
            CooperationScientificCouncilReportDto row = new CooperationScientificCouncilReportDto();
            row.setJournalId(journalId);
            addIfHasSectionData(dto.getCooperationScientificCouncil(), row);
        }
        directors.forEach(director -> {
            CooperationScientificCouncilReportDto row = new CooperationScientificCouncilReportDto();
            row.setJournalId(journalId);
            row.setFullName(director.getFullName());
            row.setAffiliation(director.getAffiliation());
            row.setCountry(findCountry(director.getCountry(), locale));
            row.setEmail(director.getEmail());
            addIfHasSectionData(dto.getCooperationScientificCouncil(), row);
        });
    }

    private String findCountry(String country, Locale locale) {
        if (country == null) {
            return null;
        }
        DictionaryCountry dictionaryCountry = dictionaryCountryService.getByCode(country);
        if ("pl".equals(locale.getLanguage())){
            return dictionaryCountry.getNamePl();
        }
        return dictionaryCountry.getNameEn();
    }

    private String issn(Journal journal, IssnType type) {
        if (journal == null || journal.getIssns() == null) {
            return null;
        }
        return journal.getIssns().stream()
                .filter(issn -> type.equals(issn.getType()))
                .filter(issn -> Boolean.TRUE.equals(issn.getActive()))
                .map(Issn::getIssn)
                .filter(Objects::nonNull)
                .findFirst()
                .or(() -> journal.getIssns().stream()
                        .filter(issn -> type.equals(issn.getType()))
                        .map(Issn::getIssn)
                        .filter(Objects::nonNull)
                        .findFirst())
                .orElse(getEmpty());
    }

    private String title(Journal journal, TitleType type) {
        if (journal == null || journal.getTitles() == null) {
            return null;
        }
        return journal.getTitles().stream()
                .filter(title -> type.equals(title.getTitleType()))
                .filter(title -> Boolean.TRUE.equals(title.getActive()))
                .map(JournalTitle::getTitle)
                .filter(Objects::nonNull)
                .findFirst()
                .or(() -> journal.getTitles().stream()
                        .filter(title -> type.equals(title.getTitleType()))
                        .map(JournalTitle::getTitle)
                        .filter(Objects::nonNull)
                        .findFirst())
                .orElse(getEmpty());
    }

    private ArticlePublished articlePublished(Stability stability, int index) {
        List<ArticlePublished> values = safeList(stability.getArticlePublished()).stream()
                .sorted(Comparator.comparing(ArticlePublished::getYear, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        return index < values.size() ? values.get(index) : null;
    }

    private ArticleLanguage articleLanguage(Stability stability, int index) {
        List<ArticleLanguage> values = safeList(stability.getArticleLanguages());
        return index < values.size() ? values.get(index) : null;
    }

    private String categoryName(Category explicitMain, List<Category> categories, boolean main) {
        if (explicitMain != null) {
            return categoryName(explicitMain);
        }
        return categoryNames(categories, main);
    }

    private String categoryNames(List<Category> categories, boolean main) {
        List<Category> filtered = safeList(categories).stream()
                .filter(category -> main == Boolean.TRUE.equals(category.getMain()))
                .toList();
        if (main && filtered.isEmpty()) {
            return getEmpty();
        }

        if (filtered.isEmpty()) {
            return getEmpty();
        }

        return filtered.stream()
                .map(this::categoryName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("; "));
    }

    private String categoryName(Category category) {
        if (category == null) {
            return getEmpty();
        }
        if (category.getNamePl() != null && !category.getNamePl().isBlank()) {
            return category.getNamePl();
        }
        if (category.getNameEn() != null && !category.getNameEn().isBlank()) {
            return category.getNameEn();
        }
        if (category.getCode() != null && !category.getCode().isBlank()) {
            return category.getCode();
        }
        return category.getId() == null ? null : category.getId().toString();
    }

    private String institutionName(Institution institution) {
        if (institution == null) {
            return null;
        }
        if (institution.getDepartment() == null || institution.getDepartment().isBlank()) {
            return institution.getName();
        }
        if (institution.getName() == null || institution.getName().isBlank()) {
            return institution.getDepartment();
        }
        return institution.getName() + ", " + institution.getDepartment();
    }

    private String address(Institution institution) {
        if (institution == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        parts.add(institution.getStreet());
        parts.add(institution.getPostCode());
        parts.add(institution.getCity());
        parts.add(countryName(institution.getCountry()));
        return join(parts);
    }

    private String fullName(Person person) {
        List<String> parts = new ArrayList<>();
        parts.add(person.getFirstName());
        parts.add(person.getMiddleName());
        parts.add(person.getLastName());
        return join(parts, " ");
    }

    private String countryName(Country country) {
        if (country == null) {
            return null;
        }
        if (country.getNamePl() != null && !country.getNamePl().isBlank()) {
            return country.getNamePl();
        }
        if (country.getNameEn() != null && !country.getNameEn().isBlank()) {
            return country.getNameEn();
        }
        return country.getCode();
    }

    private String reviewProcedure(InfoReviewProcedure procedure, Locale locale) {
        if (procedure == null) {
            return null;
        }
        List<String> values = new ArrayList<>();
        appendReviewProcedure(values, reviewProcedureLabel("doubleBlind", locale), procedure.getDoubleBlindReview());
        appendReviewProcedure(values, reviewProcedureLabel("singleBlind", locale), procedure.getSingleBlindReview());
        appendReviewProcedure(values, reviewProcedureLabel("openPeer", locale), procedure.getOpenPeerReview());
        if (Boolean.TRUE.equals(procedure.getNone()) && (procedure.getDoubleBlindReview() == null && procedure.getSingleBlindReview() == null && procedure.getOpenPeerReview() == null)) {
            values.add("NONE");
        }
        return join(values);
    }

    private String reviewProcedureLabel(String code, Locale locale) {
        return messageSource.getMessage(
                REVIEW_PROCEDURE_MESSAGE_PREFIX + code,
                null,
                code,
                locale == null ? DEFAULT_REPORT_LOCALE : locale
        );
    }

    private void appendReviewProcedure(List<String> values, String label, BooleanDouble value) {
        if (value == null || !Boolean.TRUE.equals(value.getConfirm())) {
            return;
        }
        values.add(value.getValue() == null ? label : label + " " + value.getValue() + "%");
    }

    private String publicationFeeType(AuthorsFee fee, Locale locale) {
        if (fee == null || fee.getFeeType() == null) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();

        builder.append( format(fee.getFeeType(), locale));

        if (fee.getFeeType() != FeeType.NO_FEE && fee.getFeeDetails() != null) {

            List<String> details = new ArrayList<>();

            for (FeeDetail feeDetail : fee.getFeeDetails()) {
                if (feeDetail == null) {
                    continue;
                }
                StringBuilder feeDetailBuilder = new StringBuilder();

                feeDetailBuilder.append(feeDetailDescription(feeDetail.getDescription(), locale));
                feeDetailBuilder.append(": ");
                feeDetailBuilder.append(feeDetail.getMinValue());
                feeDetailBuilder.append("-");
                feeDetailBuilder.append(feeDetail.getMaxValue());
                feeDetailBuilder.append(" ");
                feeDetailBuilder.append(feeDetail.getCurrencyCode());

                details.add(feeDetailBuilder.toString());

            }

            if (details.size() > 0){
                builder.append(" ").append("(").append(String.join(", ", details)).append(")");
            }


        }

        AuthorFeeSelectionPolicy.Resolution resolution = authorFeeSelectionPolicy.resolve(fee);
        if (resolution.status() != null) {
            String selection = messageSource.getMessage("survey.report.feeSelection." + resolution.status(),
                    null, resolution.status().name(), locale == null ? DEFAULT_REPORT_LOCALE : locale);
            builder.append(" [").append(selection);
            if (resolution.selectedFeeDetail() != null) {
                FeeDetail selected = resolution.selectedFeeDetail();
                builder.append(": ").append(formatInteger(selected.getMinValue())).append("-")
                        .append(formatInteger(selected.getMaxValue())).append(" ").append(valueOrEmpty(selected.getCurrencyCode()));
                builder.append("; ").append(messageSource.getMessage(
                        "survey.report.feeSelection.source." + fee.getCountrySpecificFeeSelection().getSource(),
                        null, fee.getCountrySpecificFeeSelection().getSource().name(),
                        locale == null ? DEFAULT_REPORT_LOCALE : locale));
            }
            builder.append("]");
        }
        return builder.toString();
    }

    private String feeDetailDescription(String description, Locale locale) {
        if (description == null || description.isBlank()) {
            return getEmpty();
        }
        return messageSource.getMessage(
                FEE_DETAIL_DESCRIPTION_MESSAGE_PREFIX + description,
                null,
                description,
                locale == null ? DEFAULT_REPORT_LOCALE : locale
        );
    }

    private String feeType(AuthorsFee fee, Locale locale) {
        return fee == null ? null : format(fee.getFeeType(), locale);
    }


    private String formatNullBoolean(Boolean value, Locale locale) {
        String code = null;

        if (value == null) {
            code = BOOLEAN_FALSE_MESSAGE_CODE;
        } else {
            code = Boolean.TRUE.equals(value) ? BOOLEAN_TRUE_MESSAGE_CODE : BOOLEAN_FALSE_MESSAGE_CODE;
        }
        String fallback = Boolean.TRUE.equals(value) ? "TAK" : "NIE";
        return messageSource.getMessage(code, null, fallback, locale == null ? DEFAULT_REPORT_LOCALE : locale);
    }

    private String formatBoolean(Boolean value, Locale locale) {
        if (value == null) {
            return getEmpty();
        }
        String code = Boolean.TRUE.equals(value) ? BOOLEAN_TRUE_MESSAGE_CODE : BOOLEAN_FALSE_MESSAGE_CODE;
        String fallback = Boolean.TRUE.equals(value) ? "TAK" : "NIE";
        return messageSource.getMessage(code, null, fallback, locale == null ? DEFAULT_REPORT_LOCALE : locale);
    }

    private String formatInteger(Integer value) {
        return value == null ? getEmpty() : value.toString();
    }

    private Double previous(JournalIndicator indicator) {
        return indicator == null ? null : indicator.getPrevious();
    }

    private Double current(JournalIndicator indicator) {
        return indicator == null ? null : indicator.getCurrent();
    }
    private String formatd3Indicator(Double value, Locale locale) {
        if (value == null) {
            return getEmpty();
        }
        NumberFormat numberFormat = NumberFormat.getNumberInstance(locale == null ? DEFAULT_REPORT_LOCALE : locale);
        numberFormat.setMinimumFractionDigits(3);
        numberFormat.setMaximumFractionDigits(3);
        numberFormat.setGroupingUsed(false);
        return numberFormat.format(value);
    }

    private String format1Indicator(Double value, Locale locale) {
        if (value == null) {
            return getEmpty();
        }
        NumberFormat numberFormat = NumberFormat.getNumberInstance(locale == null ? DEFAULT_REPORT_LOCALE : locale);
        numberFormat.setMinimumFractionDigits(1);
        numberFormat.setMaximumFractionDigits(1);
        numberFormat.setGroupingUsed(false);
        return numberFormat.format(value);
    }

    private String format0Indicator(Double value, Locale locale) {
        if (value == null) {
            return getEmpty();
        }
        NumberFormat numberFormat = NumberFormat.getNumberInstance(locale == null ? DEFAULT_REPORT_LOCALE : locale);
        numberFormat.setMinimumFractionDigits(0);
        numberFormat.setMaximumFractionDigits(0);
        numberFormat.setGroupingUsed(false);
        return numberFormat.format(value);
    }
    private String formatIndicator(Double value, Locale locale) {
        if (value == null) {
            return getEmpty();
        }
        NumberFormat numberFormat = NumberFormat.getNumberInstance(locale == null ? DEFAULT_REPORT_LOCALE : locale);
        numberFormat.setMinimumFractionDigits(2);
        numberFormat.setMaximumFractionDigits(2);
        numberFormat.setGroupingUsed(false);
        return numberFormat.format(value);
    }

    private String formatIndicatorInteger(Double value, Locale locale) {
        if (value == null) {
            return getEmpty();
        }
        NumberFormat numberFormat = NumberFormat.getNumberInstance(locale == null ? DEFAULT_REPORT_LOCALE : locale);
        numberFormat.setMinimumFractionDigits(0);
        numberFormat.setMaximumFractionDigits(0);
        numberFormat.setGroupingUsed(false);
        return numberFormat.format(value);
    }

    private Boolean infoWebsiteValue(InfoWebsite infoWebsite, Boolean value) {
        return hasInfoWebsiteData(infoWebsite) ? Boolean.TRUE.equals(value) : null;
    }

    private boolean hasInfoWebsiteData(InfoWebsite infoWebsite) {
        return infoWebsite != null && (
                Boolean.TRUE.equals(infoWebsite.getTitles())
                        || Boolean.TRUE.equals(infoWebsite.getTitlesMainSite())
                        || Boolean.TRUE.equals(infoWebsite.getIssn())
                        || Boolean.TRUE.equals(infoWebsite.getIssnMainSite())
                        || Boolean.TRUE.equals(infoWebsite.getDirectors())
                        || Boolean.TRUE.equals(infoWebsite.getEditOfficeInfo())
                        || Boolean.TRUE.equals(infoWebsite.getNone())
        );
    }

    private String languageName(Language language, Locale locale) {
        if (language == null) {
            return null;
        }
        if ("pl".equals((locale == null ? DEFAULT_REPORT_LOCALE : locale).getLanguage())) {
            return valueOrNull(language.getNamePl(), language.getNameEn(), language.getCode());
        }
        return valueOrNull(language.getNameEn(), language.getNamePl(), language.getCode());
    }

    private String joinLanguages(Collection<Language> values) {
        return safeList(values).stream()
                .map(language -> {
                    if (language.getNamePl() != null && !language.getNamePl().isBlank()) {
                        return language.getNamePl();
                    }
                    if (language.getNameEn() != null && !language.getNameEn().isBlank()) {
                        return language.getNameEn();
                    }
                    return language.getCode();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.joining("; "));
    }

    private Boolean has(Set<?> values, Object expected) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.contains(expected);
    }

    private String join(Collection<?> values) {
        return join(values, "; ", DEFAULT_REPORT_LOCALE);
    }

    private String join(Collection<?> values, Locale locale) {
        if (values == null || values.isEmpty()) {
            return getEmpty();
        }
        return join(values, "; ", locale);
    }

    private String join(Collection<?> values, String delimiter) {
        return join(values, delimiter, DEFAULT_REPORT_LOCALE);
    }

    private String join(Collection<?> values, String delimiter, Locale locale) {
        String joined = safeList(values).stream()
                .filter(Objects::nonNull)
                .map(value -> format(value, locale))
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(delimiter));
        return joined.isBlank() ? null : joined;
    }

    private String format(Object value) {
        return format(value, DEFAULT_REPORT_LOCALE);
    }

    private String format(Object value, Locale locale) {
        if (value == null) {
            return getEmpty();
        }
        if (value instanceof Repository repository) {
            return repository.getName() == null ? String.valueOf(repository.getId()) : repository.getName();
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue(enumValue, locale);
        }
        return value.toString();
    }

    private String enumValue(Enum<?> value, Locale locale) {
        String fallback = value.toString();
        String code = ENUM_MESSAGE_PREFIX + value.getDeclaringClass().getName() + "." + value.name();
        return messageSource.getMessage(code, null, fallback, locale == null ? DEFAULT_REPORT_LOCALE : locale);
    }

    private String getNoData(Locale locale){
        return messageSource.getMessage("survey.report.noData", null, "nd", locale);
    }

}
