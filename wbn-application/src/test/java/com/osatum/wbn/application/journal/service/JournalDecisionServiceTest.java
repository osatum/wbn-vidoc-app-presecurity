package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.configuration.service.OperatingProfileService;
import com.osatum.wbn.application.journal.assembler.JournalDecisionIdentityMapper;
import com.osatum.wbn.application.journal.authorization.CanJournalView;
import com.osatum.wbn.application.journal.view.JournalDecisionView.AnnualStatus;
import com.osatum.wbn.application.journal.view.DecisionValue.State;
import com.osatum.wbn.domain.entity.RatingStatus;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.journal.Rating;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.repository.journal.JournalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import com.osatum.wbn.application.journal.view.DecisionValue;
import com.osatum.wbn.application.journal.view.JournalDecisionAnnualView;
import com.osatum.wbn.application.journal.view.JournalDecisionView;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;

import java.util.HashSet;
import com.osatum.wbn.application.shared.exception.ResourceNotFoundException;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JournalDecisionServiceTest {
    private final ConfigurationService configuration = spy(new ConfigurationService());
    private final OperatingProfileService profiles = mock();
    private final JournalRepository repository = mock();
    private final JournalDecisionIdentityMapper mapper = mock();
    private final DecisionAnnualReader annualReader = mock();
    private final DecisionRatingReader ratingReader = mock();
    private final DecisionAnalyticsService analytics = DecisionAnalyticsTestSupport.readyAnalytics();
    private final JournalDecisionService service = new JournalDecisionService(configuration, profiles, repository, mapper, annualReader, ratingReader, analytics);

    @ParameterizedTest
    @CsvSource({"false,2024", "true,2024", "false,2025", "true,2025"})
    void presentationSwitchPreservesOwnDataAndCanRestoreComparisonsForEitherYear(boolean visible, int year) {
        var journal = journal(year);
        journal.setRatings(Set.of(rating(year, RatingStatus.INDEXED, 81.125)));
        var own = new JournalDecisionAnnualView(year,
                new JournalDecisionAnnualView.Times(DecisionValue.available(0), DecisionValue.available(15), DecisionValue.available(30)),
                null, null, null, null, null, List.of());
        when(annualReader.read(any(), eq(year))).thenReturn(own);
        var history = List.of(new JournalDecisionView.IcvHistory(year, RatingStatus.INDEXED, 81.125));
        when(ratingReader.history(any())).thenReturn(history);
        var comparisons = new DecisionBenchmarkCalculator().calculate(year, DecisionAnalyticsTestSupport.RUN,
                1L, null, java.util.Map.of(), List.of(), List.of());
        when(analytics.benchmarks(any(), eq(year), eq(1L), same(own), eq(81.125))).thenReturn(comparisons);
        doReturn(visible).when(configuration).isDecisionBenchmarksVisible();

        var result = service.getDecision(1L, year);
        assertThat(result.benchmarksVisible()).isEqualTo(visible);
        assertThat(result.annualStatus()).isEqualTo(AnnualStatus.AVAILABLE);
        assertThat(result.annual()).isSameAs(own);
        assertThat(result.icv()).isEqualTo(81.125);
        assertThat(result.icvHistory()).isEqualTo(history);
        assertThat(result.benchmarks()).isEqualTo(visible ? comparisons : null);
        verify(analytics, times(visible ? 1 : 0)).benchmarks(any(), eq(year), eq(1L), same(own), eq(81.125));

        doReturn(!visible).when(configuration).isDecisionBenchmarksVisible();
        var changed = service.getDecision(1L, year);
        assertThat(changed.benchmarksVisible()).isEqualTo(!visible);
        assertThat(changed.benchmarks()).isEqualTo(visible ? null : comparisons);
        assertThat(changed.annual()).isSameAs(own);
        assertThat(changed.icvHistory()).isEqualTo(history);
    }

    @ParameterizedTest
    @EnumSource(com.osatum.wbn.application.journal.repository.DecisionAnalyticsRepository.Status.class)
    void incompleteGenerationsNeverReadOrExposeSourceRows(com.osatum.wbn.application.journal.repository.DecisionAnalyticsRepository.Status status) {
        when(analytics.snapshot()).thenReturn(new com.osatum.wbn.application.journal.repository.DecisionAnalyticsRepository.Snapshot(
                status, DecisionAnalyticsTestSupport.RUN, Set.of(), java.time.Instant.EPOCH, 0));
        var result = service.getDecision(1L, 2025);
        assertThat(result.surveyYear()).isEqualTo(2025);
        assertThat(result.annualStatus()).isEqualTo(status == com.osatum.wbn.application.journal.repository.DecisionAnalyticsRepository.Status.BUILDING
                ? AnnualStatus.DATA_UPDATING : AnnualStatus.DATA_NOT_READY);
        assertThat(result.annual()).isNull();
        assertThat(result.benchmarks()).isNull();
        assertThat(result.benchmarksVisible()).isFalse();
        assertThat(result.icvHistory()).isEmpty();
        assertThat(result.mnisw()).isNull();
        verifyNoInteractions(repository, annualReader, ratingReader);
    }

    @Test
    void aGenerationChangedDuringTheReadDiscardsTheWholeCard() {
        journal(2024);
        when(analytics.unchanged(any())).thenReturn(false);
        var result = service.getDecision(1L, 2024);
        assertThat(result.annualStatus()).isEqualTo(AnnualStatus.DATA_UPDATING);
        assertThat(result.availableSurveyYears()).isEmpty();
        assertThat(result.icvHistory()).isEmpty();
        assertThat(result.annual()).isNull();
        assertThat(result.benchmarks()).isNull();
    }

    @Test
    void requiresPermissionBeforeAccessingDataAndKeepsMethodSecurity() throws Exception {
        doThrow(new AccessDeniedException("denied")).when(profiles).requireDecisionCard();
        assertThatThrownBy(() -> service.getDecision(1L, 2024)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository, mapper, annualReader, ratingReader);
        assertThat(JournalDecisionService.class.getMethod("getDecision", Long.class, Integer.class)
                .isAnnotationPresent(CanJournalView.class)).isTrue();
    }

    @Test
    void missingSelectedSurveyKeepsTheYearAndCatalogueWithoutFallingBack() {
        Journal journal = journal(2024);
        journal.setRatings(Set.of(rating(2024, RatingStatus.INDEXED, 80.5)));
        var result = service.getDecision(1L, 2025);
        assertThat(result.surveyYear()).isEqualTo(2025);
        assertThat(result.annualStatus()).isEqualTo(AnnualStatus.MISSING_SURVEY);
        assertThat(result.availableSurveyYears()).containsExactly(2024);
        assertThat(result.icv()).isNull();
        assertThat(result.annual()).isNull();
        verifyNoInteractions(annualReader);
        verify(mapper).toView(journal);
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(RatingStatus.class)
    void qualificationUsesOnlyTheSelectedYearAndStatus(RatingStatus status) {
        Journal journal = journal(2024, 2025);
        journal.setRatings(Set.of(rating(2024, RatingStatus.INDEXED, 12.0), rating(2025, status, 81.125)));
        var result = service.getDecision(1L, 2025);
        assertThat(result.annualStatus()).isEqualTo(switch (status) {
            case INDEXED -> AnnualStatus.AVAILABLE;
            case NOT_INDEXED -> AnnualStatus.NOT_INDEXED;
            case NOT_EVALUATED -> AnnualStatus.NOT_EVALUATED;
        });
        assertThat(result.icv()).isEqualTo(status == RatingStatus.INDEXED ? 81.125 : null);
        assertThat(result.availableSurveyYears()).containsExactly(2025, 2024);
        if (status == RatingStatus.INDEXED) {
            var survey = journal.getQuestionnaires().stream().filter(q -> q.getYear() == 2025).findFirst().orElseThrow().getSurveyData().getSurvey();
            verify(annualReader).read(survey, 2025);
        } else {
            verifyNoInteractions(annualReader);
            assertThat(result.annual()).isNull();
        }
    }

    @Test
    void noEvaluationIsDistinctFromNoSurveyAndDefaultIsNotTheNewestYear() {
        journal(2024, 2025);
        var result = service.getDecision(1L, null);
        assertThat(result.surveyYear()).isEqualTo(2024);
        assertThat(result.annualStatus()).isEqualTo(AnnualStatus.NOT_EVALUATED);
    }

    @Test
    void duplicatesAreUnavailableAndNotSilentlyChosen() {
        Journal journal = journal(2024, 2024);
        assertThat(service.getDecision(1L, 2024).annualStatus()).isEqualTo(AnnualStatus.UNAVAILABLE);
        assertThat(service.getDecision(1L, 2024).availableSurveyYears()).isEmpty();
        journal.getQuestionnaires().clear();
        journal.getQuestionnaires().add(survey(2024));
        journal.setRatings(Set.of(rating(2024, RatingStatus.INDEXED, 1.0), rating(2024, RatingStatus.INDEXED, 2.0)));
        assertThat(service.getDecision(1L, 2024).annualStatus()).isEqualTo(AnnualStatus.UNAVAILABLE);
    }

    @Test
    void conflictingSurveyYearDoesNotExposeAnnualData() {
        Journal journal = journal(2024);
        journal.getQuestionnaires().iterator().next().getSurveyData().getSurvey().setEvaluationYear(2025);
        journal.setRatings(Set.of(rating(2024, RatingStatus.INDEXED, 80.5)));
        var result = service.getDecision(1L, 2024);
        assertThat(result.surveyYear()).isEqualTo(2024);
        assertThat(result.annualStatus()).isEqualTo(AnnualStatus.UNAVAILABLE);
        assertThat(result.icv()).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    void aMissingOrInvalidIcvValueDoesNotBlockOtherAnnualData() {
        Journal journal = journal(2024);
        for (Double value : new Double[]{null, Double.NaN, -1.0, Double.POSITIVE_INFINITY}) {
            journal.setRatings(Set.of(rating(2024, RatingStatus.INDEXED, value)));
            var result = service.getDecision(1L, 2024);
            assertThat(result.annualStatus()).isEqualTo(AnnualStatus.AVAILABLE);
            assertThat(result.icv()).isNull();
            assertThat(result.icvStatus()).isEqualTo(value == null ? State.MISSING : State.INVALID);
        }
        journal.setRatings(Set.of(rating(2024, RatingStatus.INDEXED, 0.0)));
        var zero = service.getDecision(1L, 2024);
        assertThat(zero.icv()).isZero();
        assertThat(zero.icvStatus()).isEqualTo(State.AVAILABLE);
    }

    @Test
    void unavailableYearsAreRejectedAndUnknownIdsAreNotADataState() {
        assertThatThrownBy(() -> service.getDecision(1L, 2026)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getDecision(1L, 2024)).isInstanceOf(ResourceNotFoundException.class);
    }

    private Journal journal(int... years) {
        Journal journal = new Journal();
        journal.setId(1L);
        journal.setQuestionnaires(new HashSet<>());
        for (int year : years) journal.getQuestionnaires().add(survey(year));
        when(repository.findById(1L)).thenReturn(Optional.of(journal));
        return journal;
    }

    private Questionnaire survey(int year) {
        Questionnaire result = new Questionnaire();
        result.setYear(year);
        SurveyData data = new SurveyData();
        data.setSurvey(new Survey());
        result.setSurveyData(data);
        return result;
    }

    private Rating rating(int year, RatingStatus status, Double value) {
        Rating rating = new Rating();
        rating.setRatingCode(Rating.ICV_RATING_CODE);
        rating.setYear(year);
        rating.setRatingStatus(status);
        rating.setValueNum(value);
        return rating;
    }
}
