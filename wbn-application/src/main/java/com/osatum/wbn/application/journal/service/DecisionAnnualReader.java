package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.journal.view.DecisionValue;
import com.osatum.wbn.application.journal.view.DecisionValue.State;
import com.osatum.wbn.application.journal.view.JournalDecisionAnnualView;
import com.osatum.wbn.application.journal.view.JournalDecisionAnnualView.*;
import com.osatum.wbn.application.journal.view.JournalDecisionAnnualView.Process;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * Odczytuje końcowe deklaracje ankiety danego roku, rozróżniając brak, błąd, zero i odpowiedź przeczącą.
 * Udziały pochodzą z metryk ankiety w skali 0–100, a nie z przeliczania list osób.
 */
@Service
@RequiredArgsConstructor
public class DecisionAnnualReader {
    private final DecisionProfileReader profiles;
    private final DecisionFeeReader fees;

    public JournalDecisionAnnualView read(Survey survey, int year) {
        var standards = survey.getStandards();
        var times = standards == null ? null : standards.getAvgManuscriptTimes();
        var stability = survey.getStability();
        var rows = annualRows(survey, year);
        // Niejednoznaczny roczny wiersz unieważnia tylko zależne pola, bez wyboru pierwszego duplikatu.
        var row = rows.size() == 1 ? rows.getFirst() : null;
        var cooperation = survey.getCooperation();
        var digital = survey.getDigitalization();
        var supplementary = survey.getSupplementaryData();
        var tools = digital == null ? null : digital.getEditorialTools();
        var reviewRules = standards == null ? null : standards.getReviewRules();
        return new JournalDecisionAnnualView(year,
                new Times(nonNegative(times == null ? null : times.getInitialAssessment()), nonNegative(times == null ? null : times.getReview()),
                        nonNegative(times == null ? null : times.getPublication())),
                new Process(validYear(stability == null ? null : stability.getFirstIssueYear(), year),
                        validYear(stability == null ? null : stability.getContinuedYear(), year),
                        rows.size() > 1 ? DecisionValue.invalid() : value(row == null ? null : row.getFrequencyCode()),
                        value(survey.getPublishingVersions() == null ? null : survey.getPublishingVersions().getAop()),
                        rows.size() > 1 ? DecisionValue.invalid() : rejection(row), value(stability == null ? null : stability.getPublishingBreak())),
                new International(percent(cooperation == null ? null : cooperation.getAuthorMetric()),
                        percent(cooperation == null ? null : cooperation.getReviewerMetric()), percent(cooperation == null ? null : cooperation.getDirectorsMetric())),
                new Standards(declaration(tools, EditorialTool.ANTI_PLAGIARISM_SYSTEM, EditorialTool.NONE),
                        declaration(reviewRules, ReviewRule.PUBLIC_CRITERIA, ReviewRule.NONE),
                        declaration(tools, EditorialTool.EDITORIAL_SYSTEM, EditorialTool.NONE),
                        review(standards == null ? null : standards.getInfoReviewProcedure()),
                        value(supplementary == null ? null : supplementary.getPaidArticle()),
                        strings(supplementary == null ? null : supplementary.getInfoFullTextAccess()),
                        value(digital == null ? null : digital.getArticlePublishedLicense()), social(digital == null ? null : digital.getSocialMedias())),
                profiles.profile(survey), profiles.indexing(survey), fees.read(standards));
    }

    static <T> DecisionValue<T> value(T input) { return input == null ? DecisionValue.missing() : DecisionValue.available(input); }
    static DecisionValue<Integer> nonNegative(Integer value) { return value == null ? DecisionValue.missing() : value < 0 ? DecisionValue.invalid() : DecisionValue.available(value); }
    private DecisionValue<Integer> validYear(Integer value, int year) {
        return value == null ? DecisionValue.missing() : value < 1 || value > year ? DecisionValue.invalid() : DecisionValue.available(value);
    }
    private DecisionValue<Double> percent(ScientistMetric metric) { return percentage(metric == null ? null : metric.getForeignAffiliationPercentage(), false); }
    static DecisionValue<Double> percentage(Double value, boolean positive) {
        return value == null ? DecisionValue.missing() : !Double.isFinite(value) || value < 0 || value > 100 || (positive && value == 0)
                ? DecisionValue.invalid() : DecisionValue.available(value);
    }
    static DecisionValue<Double> rejection(ArticlePublished row) {
        if (row == null || row.getReceivedManuscriptCount() == null || row.getRejectedManuscriptCount() == null) return DecisionValue.missing();
        int received = row.getReceivedManuscriptCount(), rejected = row.getRejectedManuscriptCount();
        return received <= 0 || rejected < 0 || rejected > received ? DecisionValue.invalid() : DecisionValue.available(100.0 * rejected / received);
    }
    static List<ArticlePublished> annualRows(Survey survey, int year) {
        var stability = survey.getStability();
        return stability == null || stability.getArticlePublished() == null ? List.of()
                : stability.getArticlePublished().stream().filter(Objects::nonNull)
                .filter(value -> Objects.equals(value.getYear(), year)).toList();
    }
    private <E> DecisionValue<Declaration> declaration(Set<E> choices, E indicated, E none) {
        if (choices == null || choices.isEmpty()) return DecisionValue.missing();
        if (choices.stream().anyMatch(Objects::isNull) || (choices.contains(none) && choices.size() > 1)) return DecisionValue.invalid();
        return DecisionValue.available(choices.contains(indicated) ? Declaration.INDICATED : choices.contains(none) ? Declaration.NONE : Declaration.NOT_INDICATED);
    }
    private DecisionValue<List<String>> strings(List<String> choices) {
        if (choices == null || choices.isEmpty()) return DecisionValue.missing();
        if (choices.stream().anyMatch(value -> value == null || value.isBlank())) return DecisionValue.invalid();
        return DecisionValue.available(choices.stream().distinct().toList());
    }
    private Review review(InfoReviewProcedure source) {
        if (source == null) return new Review(List.of(), null, State.MISSING);
        var models = List.of(model("DOUBLE_BLIND_REVIEW", source.getDoubleBlindReview()), model("SINGLE_BLIND_REVIEW", source.getSingleBlindReview()),
                model("OPEN_PEER_REVIEW", source.getOpenPeerReview()));
        var selected = models.stream().filter(value -> Boolean.TRUE.equals(value.confirmed())).toList();
        boolean invalid = models.stream().anyMatch(value -> value.share().status() == State.INVALID
                || (!Boolean.TRUE.equals(value.confirmed()) && value.share().value() != null))
                || (Boolean.TRUE.equals(source.getNone()) && !selected.isEmpty())
                || selected.stream().map(ReviewModel::share).map(DecisionValue::value).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum() > 100;
        return new Review(models, source.getNone(), invalid ? State.INVALID
                : selected.isEmpty() && !Boolean.TRUE.equals(source.getNone()) ? State.MISSING : State.AVAILABLE);
    }
    private ReviewModel model(String code, BooleanDouble value) {
        return new ReviewModel(code, value == null ? null : value.getConfirm(), percentage(value == null ? null : value.getValue(), true));
    }
    private DecisionValue<Social> social(SocialMedias source) {
        if (source == null) return DecisionValue.missing();
        var channels = source.getIds() == null ? List.<SocialMedia>of() : source.getIds().stream().filter(Objects::nonNull).distinct().toList();
        boolean hasChannels = !channels.isEmpty() || (source.getOther() != null && !source.getOther().isBlank());
        var state = Boolean.FALSE.equals(source.getUsed()) && hasChannels ? State.INVALID
                : source.getIds() != null && source.getIds().stream().anyMatch(Objects::isNull) ? State.INVALID
                : source.getUsed() == null && !hasChannels ? State.MISSING : State.AVAILABLE;
        return new DecisionValue<>(state, new Social(source.getUsed(), channels, source.getOther()));
    }
}
