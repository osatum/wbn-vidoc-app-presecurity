package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.dictionary.service.DictionaryAsjcCategoryService;
import com.osatum.wbn.application.dictionary.service.DictionaryCountryService;
import com.osatum.wbn.application.dictionary.service.DictionaryLanguageService;
import com.osatum.wbn.application.dictionary.service.DictionaryRepositoryService;
import com.osatum.wbn.application.dictionary.service.DictionaryScientificCategoryService;
import com.osatum.wbn.application.dictionary.service.DictionaryTRCategoryService;
import com.osatum.wbn.application.journal.event.JournalSavedEvent;
import com.osatum.wbn.application.journal.service.JournalQuestionnaireSyncService;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.ReasonNotIndexing;
import com.osatum.wbn.domain.repository.dictionary.DictionaryAsjcCategoryRepository;
import com.osatum.wbn.domain.repository.dictionary.DictionaryCountryRepository;
import com.osatum.wbn.domain.repository.dictionary.DictionaryLanguageRepository;
import com.osatum.wbn.domain.repository.dictionary.DictionaryRepositoryRepository;
import com.osatum.wbn.domain.repository.dictionary.DictionaryScientificCategoryRepository;
import com.osatum.wbn.domain.repository.dictionary.DictionaryTRCategoryRepository;
import com.osatum.wbn.infrastructure.persistence.DictionaryReferenceCleanupRepository;
import com.osatum.wbn.infrastructure.persistence.JournalRepositoryAdapter;
import com.osatum.wbn.migration.assembler.DictionaryMapper;
import com.osatum.wbn.migration.assembler.ToJournalAssembler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.io.File;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JournalMigrationImportServiceTest {

    @Mock
    private ToJournalAssembler assembler;
    @Mock
    private JournalRepositoryAdapter repository;
    @Mock
    private ApplicationEventPublisher publisher;
    @Mock
    private DictionaryMapper dictionaryMapper;
    @Mock
    private JournalBuildService journalBuildService;
    @Mock
    private JournalQuestionnaireSyncService journalQuestionnaireSyncService;
    @Mock
    private DictionaryReferenceCleanupRepository dictionaryReferenceCleanupRepository;
    @Mock
    private DictionaryAsjcCategoryRepository dictionaryAsjcCategoryRepository;
    @Mock
    private DictionaryScientificCategoryRepository dictionaryScientificCategoryRepository;
    @Mock
    private DictionaryTRCategoryRepository dictionaryTRCategoryRepository;
    @Mock
    private DictionaryRepositoryRepository dictionaryRepositoryRepository;
    @Mock
    private DictionaryCountryRepository dictionaryCountryRepository;
    @Mock
    private DictionaryLanguageRepository dictionaryLanguageRepository;
    @Mock
    private DictionaryAsjcCategoryService dictionaryAsjcCategoryService;
    @Mock
    private DictionaryScientificCategoryService dictionaryScientificCategoryService;
    @Mock
    private DictionaryTRCategoryService dictionaryTRCategoryService;
    @Mock
    private DictionaryRepositoryService dictionaryRepositoryService;
    @Mock
    private DictionaryCountryService dictionaryCountryService;
    @Mock
    private DictionaryLanguageService dictionaryLanguageService;

    @InjectMocks
    private JournalMigrationImportService service;

    @Test
    void deleteAllDictionariesClearsJournalReferencesBeforeDeletingDictionaryItems() {
        service.deleteAllDictionaries();

        InOrder order = inOrder(
                dictionaryReferenceCleanupRepository,
                dictionaryAsjcCategoryRepository,
                dictionaryScientificCategoryRepository,
                dictionaryTRCategoryRepository,
                dictionaryRepositoryRepository,
                dictionaryCountryRepository,
                dictionaryLanguageRepository
        );
        order.verify(dictionaryReferenceCleanupRepository).deleteJournalDictionaryReferences();
        order.verify(dictionaryAsjcCategoryRepository).deleteAll();
        order.verify(dictionaryScientificCategoryRepository).deleteAll();
        order.verify(dictionaryTRCategoryRepository).deleteAll();
        order.verify(dictionaryRepositoryRepository).deleteAll();
        order.verify(dictionaryCountryRepository).deleteAll();
        order.verify(dictionaryLanguageRepository).deleteAll();
    }

    @Test
    void importJournalJsonDelegatesWithoutSyncOrEvent() throws Exception {
        File file = new File("journal.json");

        service.importJournalJson(file);

        verify(journalBuildService).importJournalJson(file);
        verifyNoInteractions(journalQuestionnaireSyncService, publisher);
    }

    @ParameterizedTest
    @CsvSource({"2024, 2025", "2025, 2024"})
    void updateReasonNotIndexingSetsAndClearsOnlyTheMatchingQuestionnaire(int year, int otherYear) {
        Journal journal = new Journal();
        journal.setId(123L);
        Questionnaire matching = questionnaire(journal, year);
        Questionnaire other = questionnaire(journal, otherYear);
        journal.setQuestionnaires(Set.of(matching, other));
        when(repository.findById(123L)).thenReturn(Optional.of(journal));

        service.updateReasonNotIndexing(123L, year, ReasonNotIndexing.dec07);
        assertThat(matching.getRating().getReasonNotIndexing()).isEqualTo(ReasonNotIndexing.dec07);

        service.updateReasonNotIndexing(123L, year, null);
        assertThat(matching.getRating().getReasonNotIndexing()).isNull();
        assertThat(other.getRating()).isNull();
        verifyNoInteractions(journalQuestionnaireSyncService, publisher);
    }

    @Test
    void updateReasonNotIndexingSkipsMissingQuestionnaires() {
        Journal journal = new Journal();
        journal.setId(123L);
        Questionnaire questionnaire = questionnaire(journal, 2024);
        journal.setQuestionnaires(Set.of(questionnaire));
        when(repository.findById(123L)).thenReturn(Optional.of(journal));
        when(repository.findById(456L)).thenReturn(Optional.empty());

        service.updateReasonNotIndexing(123L, 2025, ReasonNotIndexing.dec07);
        service.updateReasonNotIndexing(456L, 2025, ReasonNotIndexing.dec07);

        assertThat(questionnaire.getRating()).isNull();
    }

    @Test
    void updateJournalFromCurrentQuestionnairePublishesEventAfterSync() {
        service.updateJournalFromCurrentQuestionnaire(123L);

        InOrder order = inOrder(journalQuestionnaireSyncService, publisher);
        order.verify(journalQuestionnaireSyncService).updateJournalFromCurrentQuestionnaire(123L);
        order.verify(publisher).publishEvent(new JournalSavedEvent(123L));
    }

    private Questionnaire questionnaire(Journal journal, int questionnaireYear) {
        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setJournal(journal);
        questionnaire.setYear(questionnaireYear);
        return questionnaire;
    }
}
