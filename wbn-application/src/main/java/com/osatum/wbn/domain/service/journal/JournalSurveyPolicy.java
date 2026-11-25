package com.osatum.wbn.domain.service.journal;

import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.ArticlePublished;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Ustala jednoznaczne ankiety roczne i sprawdza ich reguły niezależnie od sposobu zapisu i indeksowania.
 * Braki danych pozostają brakami; błędny lub niejednoznaczny rok poza P1 nie zastępuje wybranej ankiety.
 */
@Service
@Slf4j
public class JournalSurveyPolicy {

    /**
     * Odrzuca niepoprawny rok P1 przed zapisem; pozostałe błędne lata zachowuje w danych, pomijając ich projekcje.
     */
    public void validate(Journal journal, Integer currentRatingYear) {
        resolve(journal, currentRatingYear);
    }

    public List<AnnualSurvey> resolvePublished(Journal journal, Integer currentRatingYear, List<Integer> publishedYears) {
        for (Integer year : publishedYears) {
            if (!year.equals(currentRatingYear)) validate(journal, year);
        }
        return resolve(journal, currentRatingYear);
    }

    public List<AnnualSurvey> resolve(Journal journal, Integer currentRatingYear) {
        var surveys = new ArrayList<AnnualSurvey>();
        var questionnairesByYear = new TreeMap<Integer, List<Questionnaire>>();
        if (journal.getQuestionnaires() == null) {
            return List.of();
        }
        for (Questionnaire questionnaire : journal.getQuestionnaires()) {
            if (questionnaire.getSurveyData() == null || questionnaire.getSurveyData().getSurvey() == null) {
                continue;
            }
            Integer year = questionnaire.getYear();
            if (year == null) {
                throw new IllegalArgumentException("Missing survey year for journal " + journal.getId()
                        + "; questionnaireId=" + questionnaire.getId());
            }
            questionnairesByYear.computeIfAbsent(year, ignored -> new ArrayList<>()).add(questionnaire);
        }
        for (var entry : questionnairesByYear.entrySet()) {
            Integer year = entry.getKey();
            List<Questionnaire> questionnaires = entry.getValue();
            if (questionnaires.size() > 1) {
                List<Long> questionnaireIds = questionnaires.stream().map(Questionnaire::getId).toList();
                if (year.equals(currentRatingYear)) {
                    throw new IllegalArgumentException("Duplicate survey year " + year + " for journal " + journal.getId()
                            + "; questionnaireIds=" + questionnaireIds);
                }
                log.warn("Skipping ambiguous survey year {} for journal {} outside configured P1 year {}; questionnaireIds={}",
                        year, journal.getId(), currentRatingYear, questionnaireIds);
                continue;
            }
            Survey survey = questionnaires.getFirst().getSurveyData().getSurvey();
            try {
                surveys.add(resolve(survey, year));
            } catch (IllegalArgumentException exception) {
                if (currentRatingYear != null && !year.equals(currentRatingYear)) {
                    log.warn("Skipping invalid survey year {} for journal {} outside configured P1 year {}; reason={}",
                            year, journal.getId(), currentRatingYear, exception.getMessage());
                    continue;
                }
                throw new IllegalArgumentException("Invalid survey for journal " + journal.getId() + ", year " + year
                        + ": " + exception.getMessage(), exception);
            }
        }
        return List.copyOf(surveys);
    }

    private AnnualSurvey resolve(Survey survey, Integer year) {
        if (survey.getEvaluationYear() != null && !year.equals(survey.getEvaluationYear())) {
            throw new IllegalArgumentException("Inconsistent survey year: " + year);
        }
        ArticlePublished publication = null;
        if (survey.getStability() != null) {
            var stability = survey.getStability();
            if (stability.getFirstIssueYear() != null && stability.getFirstIssueYear() < 0) {
                throw new IllegalArgumentException("First issue year must be non-negative");
            }
            if (stability.getArticlePublished() != null) {
                var annualRows = stability.getArticlePublished().stream().filter(Objects::nonNull)
                        .filter(row -> year.equals(row.getYear())).toList();
                if (annualRows.size() > 1) {
                    throw new IllegalArgumentException("Duplicate publication data for survey year " + year);
                }
                if (!annualRows.isEmpty()) {
                    publication = annualRows.getFirst();
                }
            }
        }
        if (survey.getStandards() != null && survey.getStandards().getAvgManuscriptTimes() != null) {
            var times = survey.getStandards().getAvgManuscriptTimes();
            validateDays(times.getReview());
            validateDays(times.getInitialAssessment());
            validateDays(times.getPublication());
        }
        if (survey.getCooperation() != null) {
            var cooperation = survey.getCooperation();
            if (cooperation.getAuthorMetric() != null) {
                validatePercentage(cooperation.getAuthorMetric().getForeignAffiliationPercentage());
            }
            if (cooperation.getReviewerMetric() != null) {
                validatePercentage(cooperation.getReviewerMetric().getForeignAffiliationPercentage());
            }
        }
        return new AnnualSurvey(year, survey, publication, rejectionRate(publication));
    }

    private Double rejectionRate(ArticlePublished publication) {
        if (publication == null || publication.getReceivedManuscriptCount() == null || publication.getRejectedManuscriptCount() == null) {
            return null;
        }
        Integer received = publication.getReceivedManuscriptCount();
        Integer rejected = publication.getRejectedManuscriptCount();
        if (received <= 0 || rejected < 0 || rejected > received) {
            throw new IllegalArgumentException("Invalid manuscript counts for survey year " + publication.getYear()
                    + "; receivedManuscriptCount=" + received + "; rejectedManuscriptCount=" + rejected);
        }
        return BigDecimal.valueOf(rejected).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(received), MathContext.DECIMAL128).doubleValue();
    }

    private void validateDays(Integer days) {
        if (days != null && days < 0) {
            throw new IllegalArgumentException("Manuscript processing days must be non-negative");
        }
    }

    private void validatePercentage(Double value) {
        if (value != null && (!Double.isFinite(value) || value < 0 || value > 100)) {
            throw new IllegalArgumentException("Affiliation percentage must be between 0 and 100");
        }
    }

    public record AnnualSurvey(Integer year, Survey survey, ArticlePublished publication, Double rejectionRate) {
    }
}
