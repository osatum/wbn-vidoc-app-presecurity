package com.osatum.wbn.application.journal.service;


import com.osatum.wbn.application.journal.assembler.JournalViewMapper;
import com.osatum.wbn.application.dictionary.service.DictionaryMniswDisciplineService;
import com.osatum.wbn.application.journal.assembler.JournalSearchCriteriaMapper;
import com.osatum.wbn.application.journal.authorization.CanJournalView;
import com.osatum.wbn.application.journal.query.JournalListQuery;
import com.osatum.wbn.application.journal.query.JournalSearchCriteria;
import com.osatum.wbn.application.journal.query.JournalSearchViewRules;
import com.osatum.wbn.application.configuration.service.OperatingProfileService;
import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.institution.SearchView;
import com.osatum.wbn.application.journal.view.JournalListView;
import com.osatum.wbn.application.journal.view.JournalSearchMetadata;
import com.osatum.wbn.application.journal.view.JournalSearchCounts;
import com.osatum.wbn.application.journal.view.JournalView;
import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.repository.journal.JournalRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.List;
import java.util.Map;

/**
 * Stanowi punkt autoryzacji dostępu do wyszukiwarki i karty czasopisma.
 * Lista i metadane pochodzą z indeksu wyszukiwania, natomiast szczegóły są odczytywane z modelu bazodanowego.
 */
@Service
@RequiredArgsConstructor
public class JournalService {

    private final JournalListQueryHandler journalListQueryHandler;
    private final JournalRepository journalRepository;
    private final JournalViewMapper mapper;
    private final Validator validator;
    private final JournalSearchCriteriaMapper criteriaMapper;
    private final DictionaryMniswDisciplineService mniswDisciplineService;
    private final OperatingProfileService operatingProfileService;
    private final ConfigurationService configurationService;

    /**
     * Sprawdza kryteria również dla wywołań spoza REST, zanim adapter odczytu rozpocznie wyszukiwanie.
     */
    @CanJournalView
    public PageResult<JournalListView> getJournals(JournalListQuery query) {
        return journalListQueryHandler.execute(validatedCriteria(query).criteria());
    }

    @CanJournalView
    public JournalSearchCounts getSearchCounts(JournalListQuery query) {
        var validated = validatedCriteria(query);
        var counts = journalListQueryHandler.getSearchCounts(validated.criteria());
        if (validated.view() == SearchView.BASIC) {
            return new JournalSearchCounts(counts.countryCounts(), counts.languageCounts(), counts.disciplineCounts(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), counts.mniswDisciplineCounts(), Map.of());
        }
        return counts;
    }

    private ValidatedSearch validatedCriteria(JournalListQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("Journal search criteria must not be null");
        }
        var violations = validator.validate(query);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        configurationService.resolveSearchYear(query.getSurveyYear());
        var criteria = criteriaMapper.toCriteria(query);
        var searchView = operatingProfileService.requireSearchView(query.getSearchView());
        if (searchView == SearchView.BASIC && !JournalSearchViewRules.isBasic(criteria)) {
            throw new IllegalArgumentException("Advanced criteria are not available in basic search");
        }
        for (String code : criteria.mniswDisciplineCodes()) {
            if (mniswDisciplineService.getByCode(code) == null) {
                throw new IllegalArgumentException("Unknown MNiSW discipline code: " + code);
            }
        }
        return new ValidatedSearch(criteria, searchView);
    }

    private record ValidatedSearch(JournalSearchCriteria criteria, SearchView view) { }

    /**
     * Pobiera zakresy i liczniki niezależne od roboczych filtrów użytkownika.
     */
    @CanJournalView
    public JournalSearchMetadata getSearchMetadata() {
        return getSearchMetadata(null);
    }

    @CanJournalView
    public JournalSearchMetadata getSearchMetadata(SearchView requestedView) {
        return getSearchMetadata(requestedView, null);
    }

    @CanJournalView
    public JournalSearchMetadata getSearchMetadata(SearchView requestedView, Integer surveyYear) {
        configurationService.resolveSearchYear(surveyYear);
        var searchView = operatingProfileService.requireSearchView(requestedView);
        var metadata = surveyYear == null ? journalListQueryHandler.getSearchMetadata()
                : journalListQueryHandler.getSearchMetadata(surveyYear);
        if (searchView == SearchView.BASIC) {
            return new JournalSearchMetadata(metadata.mniswPointClasses(), metadata.icvMin(), metadata.icvMax(),
                    metadata.countryCounts(), metadata.languageCounts(), metadata.disciplineCounts(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), metadata.mniswDisciplineCounts(), Map.of());
        }
        return metadata;
    }

    /**
     * Przygotowuje bazową paginację: stronę 0 oraz 10 wyników. Startowy wybór Polski ustala frontend P1.
     */
    @CanJournalView
    public JournalListQuery getDefaultJournalListQuery() {
        JournalListQuery query = new JournalListQuery();
        query.setPage(0);
        query.setSize(10);
        query.setFirst(0);
        return query;
    }

    /**
     * Pobiera szczegóły czasopisma i mapuje je do widoku aplikacyjnego.
     */
    @CanJournalView
    @Transactional(readOnly = true)
    public JournalView getJournal(Long id) {
        return getJournal(id, null);
    }

    @CanJournalView
    @Transactional(readOnly = true)
    public JournalView getJournal(Long id, Integer surveyYear) {
        Integer year = configurationService.resolveSearchYear(surveyYear);
        Optional<Journal> optional = journalRepository.findById(id);
        if (optional.isEmpty()) {
            throw new NoSuchElementException("Journal not found for id: " + id + "");
        }
        Journal journal = optional.get();
        var usableQuestionnaires = journal.getQuestionnaires() == null ? List.<Questionnaire>of()
                : journal.getQuestionnaires().stream()
                .filter(value -> value.getSurveyData() != null && value.getSurveyData().getSurvey() != null).toList();
        var questionnaires = usableQuestionnaires.stream().filter(value -> year.equals(value.getYear())).toList();
        if (questionnaires.size() > 1) {
            throw new IllegalArgumentException("Ambiguous questionnaire for year " + year);
        }
        JournalView view = mapper.toView(journal, year);
        view.setSurveyYear(year);
        view.setQuestionnaireAvailable(!questionnaires.isEmpty());
        view.setAvailableSurveyYears(configurationService.getAvailableSearchYears().stream()
                .filter(availableYear -> usableQuestionnaires.stream()
                        .filter(value -> availableYear.equals(value.getYear())).count() == 1)
                .toList());
        return view;
    }
}
