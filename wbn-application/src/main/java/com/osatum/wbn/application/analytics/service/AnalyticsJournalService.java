package com.osatum.wbn.application.analytics.service;

import com.osatum.wbn.application.analytics.assembler.AnalyticsJournalViewAssembler;
import com.osatum.wbn.application.analytics.assembler.AnalyticsJournalViewParameter;
import com.osatum.wbn.application.analytics.assembler.ToJournalAnalyticsAssembler;
import com.osatum.wbn.application.analytics.assembler.ToJournalAnalyticsParameters;
import com.osatum.wbn.domain.repository.journal.JournalRepository;
import com.osatum.wbn.application.shared.exception.ResourceNotFoundException;
import com.osatum.wbn.application.analytics.repository.AnalyticsJournalRepository;
import com.osatum.wbn.application.analytics.view.AnalyticsJournalView;
import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.journal.authorization.CanJournalView;
import com.osatum.wbn.domain.model.analytics.JournalAnalytics;
import com.osatum.wbn.domain.model.analytics.JournalAnalyticsType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsJournalService {
    private final AnalyticsJournalViewAssembler assembler;
    private final AnalyticsJournalRepository repository;
    private final ConfigurationService configurationService;
    private final JournalRepository journalRepository;

    /**
     * Pobiera dane analityczne wybranego czasopisma i składa je do widoku używanego przez warstwę prezentacji.
     */
    @CanJournalView
    @Transactional(readOnly = true)
    public AnalyticsJournalView getAnalyticsJournal(Long journalId){
        return getAnalyticsJournal(journalId, null);
    }

    @CanJournalView
    @Transactional(readOnly = true)
    public AnalyticsJournalView getAnalyticsJournal(Long journalId, Integer surveyYear) {
        Integer year = configurationService.resolveSearchYear(surveyYear);
        List<JournalAnalytics> items;
        if (surveyYear != null) {
            var journal = journalRepository.findById(journalId)
                    .orElseThrow(() -> new ResourceNotFoundException("Journal not found"));
            items = new ToJournalAnalyticsAssembler().toDto(new ToJournalAnalyticsParameters(journal, null, year));
            items = items.stream().filter(item -> item.getYear() != null && item.getYear() <= year)
                    .filter(item -> item.getType() != JournalAnalyticsType.REVIEWER_COUNTRY
                            || year.equals(item.getYear())).toList();
        } else items = repository.findAllByJournalId(journalId);
        AnalyticsJournalViewParameter parameter = new AnalyticsJournalViewParameter(items, null, journalId);
        return assembler.toDto(parameter);
    }
}
