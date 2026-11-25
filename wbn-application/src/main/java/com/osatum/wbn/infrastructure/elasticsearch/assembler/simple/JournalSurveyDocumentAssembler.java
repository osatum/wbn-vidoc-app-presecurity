package com.osatum.wbn.infrastructure.elasticsearch.assembler.simple;

import com.osatum.wbn.application.journal.assembler.SurveyJournalEntityMapper;
import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.domain.service.journal.JournalFeePolicy;
import com.osatum.wbn.domain.service.journal.JournalSurveyPolicy;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.journal.JournalReviewModel;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.ArticleLanguage;
import com.osatum.wbn.domain.model.survey.model.BooleanDouble;
import com.osatum.wbn.domain.model.survey.model.InfoReviewProcedure;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.JournalDocument;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.Language;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.ScientificCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class JournalSurveyDocumentAssembler {
    private final ConfigurationService configurationService;
    private final JournalFeePolicy feePolicy;
    private final JournalSurveyPolicy surveyPolicy;
    private final JournalDocumentAssembler documentAssembler = new JournalDocumentAssembler();
    private final SurveyJournalEntityMapper entityMapper = new SurveyJournalEntityMapper();

    public List<JournalDocument> toDocuments(Journal journal) {
        List<JournalDocument> documents = new ArrayList<>();
        for (var annualSurvey : surveyPolicy.resolvePublished(journal, configurationService.getCurrentRatingYear(),
                configurationService.getAvailableSearchYears())) {
            documents.add(toDocument(journal, annualSurvey));
        }
        return documents;
    }

    private JournalDocument toDocument(Journal journal, JournalSurveyPolicy.AnnualSurvey annualSurvey) {
        Survey survey = annualSurvey.survey();
        Integer year = annualSurvey.year();
        Journal projection = new Journal();
        projection.setId(journal.getId());
        projection.setState(journal.getState());
        projection.setWarningCode(journal.getWarningCode());
        projection.setCover(journal.getCover());
        projection.setCardinality(journal.getCardinality());
        projection.setRatings(journal.getRatings());
        if (survey.getDigitalization() != null) {
            projection.setDoi(survey.getDigitalization().getDoiPrefix());
        }
        projection.setTitles(new HashSet<>());
        projection.setIssns(new HashSet<>());
        projection.setInstitutions(new HashSet<>());
        if (survey.getJournal() != null) {
            if (survey.getJournal().getTitles() != null) {
                survey.getJournal().getTitles().stream()
                        .map(source -> entityMapper.toJournalTitle(projection, source))
                        .filter(Objects::nonNull).forEach(projection.getTitles()::add);
            }
            if (survey.getJournal().getIssns() != null) {
                survey.getJournal().getIssns().stream()
                        .map(source -> entityMapper.toJournalIssn(projection, source))
                        .filter(Objects::nonNull).forEach(projection.getIssns()::add);
            }
        }
        if (survey.getPublisher() != null && survey.getPublisher().getPublisherInstitutions() != null) {
            survey.getPublisher().getPublisherInstitutions().stream()
                    .filter(Objects::nonNull).filter(source -> Character.valueOf('P').equals(source.getInstitutionRole()))
                    .map(source -> entityMapper.toJournalInstitution(projection, source))
                    .filter(Objects::nonNull).forEach(projection.getInstitutions()::add);
        }
        if (survey.getEditorial() != null && survey.getEditorial().getRedactionInstitutions() != null) {
            survey.getEditorial().getRedactionInstitutions().stream()
                    .filter(Objects::nonNull).filter(source -> Character.valueOf('E').equals(source.getInstitutionRole()))
                    .map(source -> entityMapper.toJournalInstitution(projection, source))
                    .filter(Objects::nonNull).forEach(projection.getInstitutions()::add);
        }
        JournalDocument document = documentAssembler.toDto(new JournalDocumentParameters(projection, null));
        document.setDocumentId(journal.getId() + ":" + year);
        document.setSurveyYear(year);
        document.setReviewModels(reviewModels(survey.getStandards() == null ? null
                : survey.getStandards().getInfoReviewProcedure()));
        document.setScientificCategories(new ArrayList<>());
        document.setLanguages(new ArrayList<>());
        if (survey.getDigitalization() != null) {
            var digitalization = survey.getDigitalization();
            document.setDoiType(digitalization.getDoiType() == null ? null : digitalization.getDoiType().name());
            document.setEditorialTools(digitalization.getEditorialTools() == null ? null
                    : digitalization.getEditorialTools().stream().filter(Objects::nonNull).map(Enum::name).sorted().toList());
            document.setArticlePublishedLicense(digitalization.getArticlePublishedLicense() == null
                    ? null : digitalization.getArticlePublishedLicense().name());
            document.setSocialMediaUsed(digitalization.getSocialMedias() == null ? null : digitalization.getSocialMedias().getUsed());
            if (digitalization.getExternalRepositories() != null && digitalization.getExternalRepositories().getGeneral() != null) {
                document.setRepositoryIds(digitalization.getExternalRepositories().getGeneral().stream()
                        .filter(Objects::nonNull).map(com.osatum.wbn.domain.model.survey.model.Repository::getId)
                        .filter(Objects::nonNull).map(Object::toString).distinct().sorted().toList());
            }
        }
        if (survey.getPublishingVersions() != null) {
            document.setAop(survey.getPublishingVersions().getAop());
        }
        if (survey.getSupplementaryData() != null) {
            var data = survey.getSupplementaryData();
            document.setPaidArticle(data.getPaidArticle() == null ? null : data.getPaidArticle().name());
            if (data.getCountry() != null) {
                document.setCountryCode(data.getCountry().getCode());
            }
            if (data.getScientificCategories() != null) {
                data.getScientificCategories().stream().filter(Objects::nonNull)
                        .filter(category -> category.getId() != null)
                        .map(category -> new ScientificCategory(category.getId().toString()))
                        .forEach(document.getScientificCategories()::add);
            }
        }
        if (survey.getStability() != null) {
            var stability = survey.getStability();
            document.setFirstIssueYear(stability.getFirstIssueYear());
            if (stability.getArticleLanguages() != null) {
                stability.getArticleLanguages().stream().filter(Objects::nonNull)
                        .filter(language -> language.getLanguage() != null && hasArticles(language, year))
                        .map(language -> language.getLanguage().getCode()).filter(Objects::nonNull).distinct()
                        .map(Language::new).forEach(document.getLanguages()::add);
            }
            if (annualSurvey.publication() != null) {
                document.setFrequencyCode(annualSurvey.publication().getFrequencyCode() == null
                        ? null : annualSurvey.publication().getFrequencyCode().name());
            }
        }
        document.setRejectionRate(annualSurvey.rejectionRate());
        if (survey.getStandards() != null && survey.getStandards().getAvgManuscriptTimes() != null) {
            var times = survey.getStandards().getAvgManuscriptTimes();
            document.setReviewDays(times.getReview());
            document.setInitialAssessmentDays(times.getInitialAssessment());
            document.setPublicationDays(times.getPublication());
        }
        if (survey.getCooperation() != null) {
            var cooperation = survey.getCooperation();
            if (cooperation.getAuthorMetric() != null) {
                document.setAuthorForeignAffiliationPercentage(cooperation.getAuthorMetric().getForeignAffiliationPercentage());
            }
            if (cooperation.getReviewerMetric() != null) {
                document.setReviewerForeignAffiliationPercentage(cooperation.getReviewerMetric().getForeignAffiliationPercentage());
            }
        }
        document.setFees(feePolicy.project(survey.getStandards(), document.getCountryCode()));
        return document;
    }

    private List<String> reviewModels(InfoReviewProcedure procedure) {
        List<String> models = new ArrayList<>();
        if (procedure != null) {
            if (isConfirmed(procedure.getDoubleBlindReview())) {
                models.add(JournalReviewModel.DOUBLE_BLIND_REVIEW.name());
            }
            if (isConfirmed(procedure.getSingleBlindReview())) {
                models.add(JournalReviewModel.SINGLE_BLIND_REVIEW.name());
            }
            if (isConfirmed(procedure.getOpenPeerReview())) {
                models.add(JournalReviewModel.OPEN_PEER_REVIEW.name());
            }
        }
        return models;
    }

    private boolean isConfirmed(BooleanDouble model) {
        return model != null && Boolean.TRUE.equals(model.getConfirm());
    }

    private boolean hasArticles(ArticleLanguage language, Integer year) {
        return (year.equals(language.getCurrentYear()) && language.getCurrentValue() != null && language.getCurrentValue() > 0)
                || (year.equals(language.getPreviousYear()) && language.getPreviousValue() != null && language.getPreviousValue() > 0);
    }

}
