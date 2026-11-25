package com.osatum.wbn.application.analytics.assembler;



import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.RatingIcvStatus;
import com.osatum.wbn.domain.model.survey.Cooperation;
import com.osatum.wbn.domain.model.survey.Stability;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.analytics.JournalAnalytics;
import com.osatum.wbn.domain.model.analytics.JournalAnalyticsType;
import com.osatum.wbn.shared.assembler.AssemblerDto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class ToJournalAnalyticsAssembler implements AssemblerDto<Journal, List<JournalAnalytics>, ToJournalAnalyticsParameters> {
    private static final Set<JournalAnalyticsType> COOPERATION_ANALYTICS_TYPES = EnumSet.of(
            JournalAnalyticsType.AUTHOR_UNIQUE,
            JournalAnalyticsType.AUTHOR_EXTERNAL,
            JournalAnalyticsType.AUTHOR_FOREIGN,
            JournalAnalyticsType.REVIEWER_UNIQUE,
            JournalAnalyticsType.REVIEWER_EXTERNAL,
            JournalAnalyticsType.REVIEWER_FOREIGN,
            JournalAnalyticsType.DIRECTOR_UNIQUE,
            JournalAnalyticsType.DIRECTOR_EXTERNAL,
            JournalAnalyticsType.DIRECTOR_FOREIGN
    );

    @Override
    public List<JournalAnalytics> toDto(ToJournalAnalyticsParameters parameters) {

        Journal journal = parameters.getEntity();
        if (journal.getQuestionnaires() == null) {
            return List.of();
        }
        List<JournalAnalytics> analytics = new ArrayList<>();
        TreeSet<Integer> cooperationYears = new TreeSet<>();
        for (Questionnaire questionnaire : journal.getQuestionnaires()) {

            if (questionnaire.getRating() == null) {
                continue;
            }
            if (RatingIcvStatus.PUBLISHED.equals(questionnaire.getRating().getRatingStatus()) || RatingIcvStatus.NOT_INDEXED.equals(questionnaire.getRating().getRatingStatus())){
                if (questionnaire.getSurveyData() == null || questionnaire.getSurveyData().getSurvey() == null) {
                    continue;
                }
                Survey survey = questionnaire.getSurveyData().getSurvey();

                if (questionnaire.getYear() != null) {
                    cooperationYears.add(questionnaire.getYear());
                }
                analytics.addAll(buildCooperationAnalitycs(journal.getId(), questionnaire.getYear(), survey.getCooperation()));
                if (parameters.getYear() != null && parameters.getYear().equals(questionnaire.getYear())) {
                    analytics.addAll(buildStabilityAnalitycs(journal.getId(), questionnaire.getYear(), survey.getStability()));
                    analytics.addAll(buildCooperationReviewers(journal.getId(), questionnaire.getYear(), survey.getCooperation()));
                }

            }

        }

        analytics.addAll(buildMissingCooperationAnalytics(journal.getId(), cooperationYears, analytics));
        return analytics;
    }

    private List<JournalAnalytics> buildMissingCooperationAnalytics(
            Long journalId,
            TreeSet<Integer> cooperationYears,
            List<JournalAnalytics> analytics
    ) {
        if (cooperationYears.size() < 2) {
            return List.of();
        }

        Set<JournalAnalyticsType> presentTypes = EnumSet.noneOf(JournalAnalyticsType.class);
        for (JournalAnalytics item : analytics) {
            if (COOPERATION_ANALYTICS_TYPES.contains(item.getType())) {
                presentTypes.add(item.getType());
            }
        }
        if (presentTypes.isEmpty()) {
            return List.of();
        }

        List<JournalAnalytics> missingAnalytics = new ArrayList<>();
        for (int year = cooperationYears.first(); year < cooperationYears.last(); year++) {
            if (cooperationYears.contains(year)) {
                continue;
            }
            for (JournalAnalyticsType type : presentTypes) {
                JournalAnalytics item = new JournalAnalytics();
                item.setYear(year);
                item.setJournalId(journalId);
                item.setType(type);
                item.setValue(null);
                missingAnalytics.add(item);
            }
        }
        return missingAnalytics;
    }

    private Collection<? extends JournalAnalytics> buildCooperationReviewers(Long journalId, Integer year, Cooperation cooperation) {
        if (cooperation == null || cooperation.getReviewersList() == null){
            return List.of();
        }

        Map<String, Integer> reviewersByCountry = new TreeMap<>();
        for (var reviewer : cooperation.getReviewersList()) {
            if (reviewer == null || reviewer.getCountry() == null) {
                continue;
            }
            String countryCode = reviewer.getCountry().trim();
            if (countryCode.isEmpty()) {
                continue;
            }
            reviewersByCountry.merge(countryCode, 1, Integer::sum);
        }

        if (reviewersByCountry.isEmpty()) {
            return List.of();
        }

        List<JournalAnalytics> journalAnalytics = new ArrayList<>();
        for (var entry : reviewersByCountry.entrySet()) {
            JournalAnalytics item = new JournalAnalytics();
            item.setYear(year);
            item.setJournalId(journalId);
            item.setType(JournalAnalyticsType.REVIEWER_COUNTRY);
            item.setCountryCode(entry.getKey());
            item.setValue(entry.getValue().doubleValue());
            journalAnalytics.add(item);
        }

        return journalAnalytics;
    }

    private Collection<? extends JournalAnalytics> buildCooperationAnalitycs(Long journalId, Integer year, Cooperation cooperation) {
        if (cooperation == null){
            return List.of();
        }
        List<JournalAnalytics> journalAnalytics = new ArrayList<>();
        journalAnalytics.addAll(assemblyScientistMetric(journalId, year, cooperation));
        return journalAnalytics;
    }

    private Collection<? extends JournalAnalytics> assemblyScientistMetric(Long journalId, Integer year, Cooperation cooperation) {
        List<JournalAnalytics> journalAnalytics = new ArrayList<>();
        if (cooperation.getAuthorMetric() != null){
            if (cooperation.getAuthorMetric().getUnique() != null){
                JournalAnalytics item = new JournalAnalytics();
                item.setYear(year);
                item.setJournalId(journalId);
                item.setType(JournalAnalyticsType.AUTHOR_UNIQUE);
                item.setValue(Double.valueOf(cooperation.getAuthorMetric().getUnique()));
                journalAnalytics.add(item);
            }
            if (cooperation.getAuthorMetric().getExternalAffiliationPercentage() != null){
                JournalAnalytics item = new JournalAnalytics();
                item.setYear(year);
                item.setJournalId(journalId);
                item.setType(JournalAnalyticsType.AUTHOR_EXTERNAL);
                item.setValue(cooperation.getAuthorMetric().getExternalAffiliationPercentage());
                journalAnalytics.add(item);
            }
            if (cooperation.getAuthorMetric().getForeignAffiliationPercentage() != null){
                JournalAnalytics item = new JournalAnalytics();
                item.setYear(year);
                item.setJournalId(journalId);
                item.setType(JournalAnalyticsType.AUTHOR_FOREIGN);
                item.setValue(cooperation.getAuthorMetric().getForeignAffiliationPercentage());
                journalAnalytics.add(item);
            }
        }
        if (cooperation.getReviewerMetric() != null){
            if (cooperation.getReviewerMetric().getUnique() != null){
                JournalAnalytics item = new JournalAnalytics();
                item.setYear(year);
                item.setJournalId(journalId);
                item.setType(JournalAnalyticsType.REVIEWER_UNIQUE);
                item.setValue(Double.valueOf(cooperation.getReviewerMetric().getUnique()));
                journalAnalytics.add(item);
            }
            if (cooperation.getReviewerMetric().getExternalAffiliationPercentage() != null){
                JournalAnalytics item = new JournalAnalytics();
                item.setYear(year);
                item.setJournalId(journalId);
                item.setType(JournalAnalyticsType.REVIEWER_EXTERNAL);
                item.setValue(cooperation.getReviewerMetric().getExternalAffiliationPercentage());
                journalAnalytics.add(item);
            }
            if (cooperation.getReviewerMetric().getForeignAffiliationPercentage() != null){
                JournalAnalytics item = new JournalAnalytics();
                item.setYear(year);
                item.setJournalId(journalId);
                item.setType(JournalAnalyticsType.REVIEWER_FOREIGN);
                item.setValue(cooperation.getReviewerMetric().getForeignAffiliationPercentage());
                journalAnalytics.add(item);
            }
        }
        if (cooperation.getDirectorsMetric() != null){
            if (cooperation.getDirectorsMetric().getUnique() != null){
                JournalAnalytics item = new JournalAnalytics();
                item.setYear(year);
                item.setJournalId(journalId);
                item.setType(JournalAnalyticsType.DIRECTOR_UNIQUE);
                item.setValue(Double.valueOf(cooperation.getDirectorsMetric().getUnique()));
                journalAnalytics.add(item);
            }
            if (cooperation.getDirectorsMetric().getExternalAffiliationPercentage() != null){
                JournalAnalytics item = new JournalAnalytics();
                item.setYear(year);
                item.setJournalId(journalId);
                item.setType(JournalAnalyticsType.DIRECTOR_EXTERNAL);
                item.setValue(cooperation.getDirectorsMetric().getExternalAffiliationPercentage());
                journalAnalytics.add(item);
            }
            if (cooperation.getDirectorsMetric().getForeignAffiliationPercentage() != null){
                JournalAnalytics item = new JournalAnalytics();
                item.setYear(year);
                item.setJournalId(journalId);
                item.setType(JournalAnalyticsType.DIRECTOR_FOREIGN);
                item.setValue(cooperation.getDirectorsMetric().getForeignAffiliationPercentage());
                journalAnalytics.add(item);
            }
        }

        return journalAnalytics;
    }

    private List<JournalAnalytics> buildStabilityAnalitycs(Long journalId, Integer year, Stability stability) {
        if (stability == null){
            return List.of();
        }
        List<JournalAnalytics> journalAnalytics = new ArrayList<>();
        journalAnalytics.addAll(assemblyArticlePublished(journalId, year, stability));




        return journalAnalytics;

    }

    private  List<JournalAnalytics> assemblyArticlePublished(Long journalId, Integer year, Stability stability) {
        if (stability.getArticlePublished() == null){
            return List.of();
        }

        List<JournalAnalytics> journalAnalytics = new ArrayList<>();

        stability.getArticlePublished().stream().forEach(article -> {
            /*
            if (article.getAmount() != null){
                JournalAnalytics item = new JournalAnalytics();
                item.setYear(year);
                item.setJournalId(journalId);
                item.setType(JournalAnalyticsType.ART_AMOUNT);
                item.setValue(Double.valueOf(article.getAmount()));
                journalAnalytics.add(item);
            }
            */
            if (article.getScientificAmount() != null){
                JournalAnalytics item = new JournalAnalytics();
                item.setYear(article.getYear());
                item.setJournalId(journalId);
                item.setType(JournalAnalyticsType.ART_SCIENTIFIC_AMOUNT);
                item.setValue(Double.valueOf(article.getScientificAmount()));
                journalAnalytics.add(item);
            } else {
                JournalAnalytics item = new JournalAnalytics();
                item.setYear(article.getYear());
                item.setJournalId(journalId);
                item.setType(JournalAnalyticsType.ART_SCIENTIFIC_AMOUNT);
                item.setValue(null);
                journalAnalytics.add(item);
            }

            if (article.getReceivedManuscriptCount() != null){
                JournalAnalytics item = new JournalAnalytics();
                item.setYear(article.getYear());
                item.setJournalId(journalId);
                item.setType(JournalAnalyticsType.ART_MANUSCRIPT_RECEIVED);
                item.setValue(Double.valueOf(article.getReceivedManuscriptCount()));
                journalAnalytics.add(item);
            } else {
                JournalAnalytics item = new JournalAnalytics();
                item.setYear(article.getYear());
                item.setJournalId(journalId);
                item.setType(JournalAnalyticsType.ART_MANUSCRIPT_RECEIVED);
                item.setValue(null);
                journalAnalytics.add(item);
            }
        });
        return journalAnalytics;
    }

}
