package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.configuration.service.OperatingProfileService;
import com.osatum.wbn.application.journal.assembler.JournalDecisionIdentityMapper;
import com.osatum.wbn.application.journal.authorization.CanJournalView;
import com.osatum.wbn.application.journal.view.JournalDecisionView;
import com.osatum.wbn.application.journal.view.JournalDecisionView.AnnualStatus;
import com.osatum.wbn.application.journal.view.DecisionValue.State;
import com.osatum.wbn.application.shared.exception.ResourceNotFoundException;
import com.osatum.wbn.domain.entity.RatingStatus;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.journal.Rating;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.repository.journal.JournalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.List;
import java.util.Objects;

/**
 * Łączy bieżący katalog z ankietą wybranego roku, bez zastępowania braków innym rocznikiem.
 * Dane roczne i zapisane benchmarki muszą pochodzić z tego samego, gotowego stanu analityki.
 * Brak przygotowanego rocznika porównań nie uruchamia obliczeń w żądaniu i nie zastępuje danych innym rokiem.
 * Wyłączenie prezentacji pomija odczyt benchmarków, zachowując dane własne i kontrolę gotowości.
 * Jawna flaga w odpowiedzi odróżnia ten tryb od braku dostępnego porównania pojedynczego parametru.
 */
@Service
@RequiredArgsConstructor
public class JournalDecisionService {
    private final ConfigurationService configuration;
    private final OperatingProfileService profiles;
    private final JournalRepository repository;
    private final JournalDecisionIdentityMapper mapper;
    private final DecisionAnnualReader annualReader;
    private final DecisionRatingReader ratingReader;
    private final DecisionAnalyticsService analytics;

    @CanJournalView
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public JournalDecisionView getDecision(Long id, Integer surveyYear) {
        profiles.requireDecisionCard();
        Integer year = configuration.resolveSearchYear(surveyYear);
        if (id == null || id <= 0) throw new IllegalArgumentException("Invalid journal identifier");
        var snapshot = analytics.snapshot();
        if (!snapshot.ready(year)) return updating(id, year,
                snapshot.status() == com.osatum.wbn.application.journal.repository.DecisionAnalyticsRepository.Status.BUILDING
                        ? AnnualStatus.DATA_UPDATING : AnnualStatus.DATA_NOT_READY);
        Journal journal = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Journal not found"));
        List<Questionnaire> surveys = journal.getQuestionnaires() == null ? List.of()
                : journal.getQuestionnaires().stream().filter(Objects::nonNull)
                .filter(value -> value.getSurveyData() != null && value.getSurveyData().getSurvey() != null).toList();
        var availableYears = configuration.getAvailableSearchYears().stream()
                .filter(value -> surveys.stream().filter(q -> value.equals(q.getYear())).count() == 1).toList();
        var ratings = journal.getRatings() == null ? List.<Rating>of() : journal.getRatings().stream()
                .filter(Objects::nonNull).filter(value -> Rating.ICV_RATING_CODE.equals(value.getRatingCode()))
                .filter(value -> year.equals(value.getYear())).toList();
        AnnualStatus status = annualStatus(surveys, ratings, year);
        Double points = status == AnnualStatus.AVAILABLE ? ratings.getFirst().getValueNum() : null;
        var icvStatus = points == null ? State.MISSING
                : !Double.isFinite(points) || points < 0 ? State.INVALID : State.AVAILABLE;
        if (points != null && (!Double.isFinite(points) || points < 0)) points = null;
        var annual = status == AnnualStatus.AVAILABLE ? annualReader.read(surveys.stream()
                .filter(value -> year.equals(value.getYear())).findFirst().orElseThrow().getSurveyData().getSurvey(), year) : null;
        boolean benchmarksVisible = configuration.isDecisionBenchmarksVisible();
        var benchmarks = !benchmarksVisible || annual == null ? null : analytics.benchmarks(snapshot, year, id, annual, points);
        var result = new JournalDecisionView(year, status, availableYears, mapper.toView(journal), points,
                icvStatus, ratingReader.mnisw(journal.getRatings()), ratingReader.history(journal.getRatings()), annual, benchmarks, benchmarksVisible);
        // Ponowny odczyt stanu chroni także słowniki w pamięci, gdy import ruszy w trakcie odczytu starszych danych.
        return analytics.unchanged(snapshot) ? result : updating(id, year, AnnualStatus.DATA_UPDATING);
    }

    private JournalDecisionView updating(Long id, Integer year, AnnualStatus status) {
        var identity = new Journal();
        identity.setId(id);
        return new JournalDecisionView(year, status, List.of(), mapper.toView(identity), null,
                State.MISSING, null, List.of(), null, null, configuration.isDecisionBenchmarksVisible());
    }

    private AnnualStatus annualStatus(List<Questionnaire> surveys, List<Rating> ratings, Integer year) {
        var selected = surveys.stream().filter(value -> year.equals(value.getYear())).toList();
        if (selected.isEmpty()) return AnnualStatus.MISSING_SURVEY;
        if (selected.size() != 1 || ratings.size() > 1) return AnnualStatus.UNAVAILABLE;
        Integer evaluationYear = selected.getFirst().getSurveyData().getSurvey().getEvaluationYear();
        if (evaluationYear != null && !year.equals(evaluationYear)) return AnnualStatus.UNAVAILABLE;
        if (ratings.isEmpty() || ratings.getFirst().getRatingStatus() == null
                || ratings.getFirst().getRatingStatus() == RatingStatus.NOT_EVALUATED) return AnnualStatus.NOT_EVALUATED;
        return ratings.getFirst().getRatingStatus() == RatingStatus.INDEXED
                ? AnnualStatus.AVAILABLE : AnnualStatus.NOT_INDEXED;
    }
}
