package com.osatum.wbn.migration.service;

import com.osatum.jml.migration.dto.DictionaryMigrationRecord;
import com.osatum.jml.migration.dto.dictonary.DictionaryPayload;
import com.osatum.wbn.application.dictionary.service.DictionaryAsjcCategoryService;
import com.osatum.wbn.application.dictionary.service.DictionaryCountryService;
import com.osatum.wbn.application.dictionary.service.DictionaryLanguageService;
import com.osatum.wbn.application.dictionary.service.DictionaryRepositoryService;
import com.osatum.wbn.application.dictionary.service.DictionaryScientificCategoryService;
import com.osatum.wbn.application.dictionary.service.DictionaryTRCategoryService;
import com.osatum.wbn.application.journal.event.JournalSavedEvent;
import com.osatum.wbn.application.journal.service.JournalQuestionnaireSyncService;
import com.osatum.wbn.domain.model.dictionary.*;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.RatingIcv;
import com.osatum.wbn.domain.model.questionnaire.ReasonNotIndexing;
import com.osatum.wbn.domain.repository.dictionary.*;
import com.osatum.wbn.infrastructure.persistence.DictionaryReferenceCleanupRepository;
import com.osatum.wbn.infrastructure.persistence.JournalRepositoryAdapter;
import com.osatum.wbn.migration.assembler.DictionaryMapper;
import com.osatum.wbn.migration.assembler.ToJournalAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

/**
 * Wykonuje operacje bazodanowe importera oraz odświeża słowniki i dane czasopism po wczytaniu eksportu.
 * Oznaczenia rocznych ankiet pozostają w modelu; późniejsza aktualizacja bieżącego czasopisma ich nie zastępuje.
 */
@Service
@RequiredArgsConstructor
public class JournalMigrationImportService {
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final ToJournalAssembler assembler;
    private final JournalRepositoryAdapter repository;
    private final ApplicationEventPublisher publisher;
    private final DictionaryMapper dictionaryMapper;
    private final JournalBuildService journalBuildService;
    private final JournalQuestionnaireSyncService journalQuestionnaireSyncService;
    private final DictionaryReferenceCleanupRepository dictionaryReferenceCleanupRepository;

    private final DictionaryAsjcCategoryRepository dictionaryAsjcCategoryRepository;
    private final DictionaryScientificCategoryRepository dictionaryScientificCategoryRepository;
    private final DictionaryTRCategoryRepository dictionaryTRCategoryRepository;
    private final DictionaryRepositoryRepository dictionaryRepositoryRepository;
    private final DictionaryCountryRepository dictionaryCountryRepository;
    private final DictionaryLanguageRepository dictionaryLanguageRepository;
    private final DictionaryAsjcCategoryService dictionaryAsjcCategoryService;
    private final DictionaryScientificCategoryService dictionaryScientificCategoryService;
    private final DictionaryTRCategoryService dictionaryTRCategoryService;
    private final DictionaryRepositoryService dictionaryRepositoryService;
    private final DictionaryCountryService dictionaryCountryService;
    private final DictionaryLanguageService dictionaryLanguageService;

    public List<Long> findJournalIds() {
        return repository.findAllIds();
    }

    public long countJournals() {
        return repository.countJournals();
    }

    public List<Long> findJournalIdsAfter(Long lastId, int limit) {
        return repository.findIdsAfter(lastId, limit);
    }

    @Transactional
    public void deleteJournalById(Long journalId) {
        repository.deleteById(journalId);
    }

    /**
     * Usuwa najpierw powiązania słownikowe, a następnie słowniki; jest etapem pełnego odtworzenia danych.
     */
    @Transactional
    public void deleteAllDictionaries() {
        dictionaryReferenceCleanupRepository.deleteJournalDictionaryReferences();
        dictionaryAsjcCategoryRepository.deleteAll();
        dictionaryScientificCategoryRepository.deleteAll();
        dictionaryTRCategoryRepository.deleteAll();
        dictionaryRepositoryRepository.deleteAll();
        dictionaryCountryRepository.deleteAll();
        dictionaryLanguageRepository.deleteAll();
    }

    /**
     * Odświeża pamięci podręczne po imporcie słowników, zanim mapowanie czasopism zacznie używać ich referencji.
     */
    public void reloadDictionaryCaches() {
        dictionaryAsjcCategoryService.reload();
        dictionaryScientificCategoryService.reload();
        dictionaryTRCategoryService.reload();
        dictionaryRepositoryService.reload();
        dictionaryCountryService.reload();
        dictionaryLanguageService.reload();
    }

    /**
     * Odtwarza czasopismo i jego ankiety z eksportu JSON.
     */
    @Transactional
    public void importJournalJson(File file) throws Exception {
        journalBuildService.importJournalJson(file);
    }

    /**
     * Uzupełnia przyczynę nieindeksowania w istniejącej ankiecie wskazanego rocznika.
     */
    @Transactional
    public void updateReasonNotIndexing(Long journalId, int year, ReasonNotIndexing reason) {
        repository.findById(journalId)
                .ifPresent(journal -> applyReasonNotIndexing(journal, year, reason));
    }

    /**
     * Synchronizuje widok czasopisma z obowiązującą ankietą, a zdarzeniem zleca aktualizację projekcji indeksu.
     */
    @Transactional
    public void updateJournalFromCurrentQuestionnaire(Long journalId) {
        journalQuestionnaireSyncService.updateJournalFromCurrentQuestionnaire(journalId);
        publisher.publishEvent(new JournalSavedEvent(journalId));
    }

    private void applyReasonNotIndexing(Journal journal, int year, ReasonNotIndexing reason) {
        if (journal.getQuestionnaires() == null) {
            return;
        }

        journal.getQuestionnaires().stream()
                .filter(questionnaire -> Integer.valueOf(year).equals(questionnaire.getYear()))
                .findFirst()
                .ifPresent(questionnaire -> setReasonNotIndexing(questionnaire, reason));
    }

    private void setReasonNotIndexing(Questionnaire questionnaire, ReasonNotIndexing reason) {
        RatingIcv rating = questionnaire.getRating();
        if (rating == null) {
            rating = new RatingIcv();
            questionnaire.setRating(rating);
        }
        rating.setReasonNotIndexing(reason);
    }

    /**
     * Wczytuje paczkę słowników z JSON i zachowuje jej identyfikatory używane później przez eksport czasopism.
     */
    @Transactional
    public void importDictionaryJson(File file) throws Exception {

        try (FileInputStream fis = new FileInputStream(file)) {
            DictionaryMigrationRecord record = mapper.readValue(fis, DictionaryMigrationRecord.class);
            importDictionaryPayload(record.getPayload());
        }
    }

    private void importDictionaryPayload(DictionaryPayload payload) {
        if (payload == null) {
            return;
        }

        if (payload.getAsjcCategories() != null) {
            List<DictionaryAsjcCategory> items = payload.getAsjcCategories().stream().map(dictionaryMapper::toEntity).toList();
            dictionaryAsjcCategoryRepository.saveAll(items);
        }

        if (payload.getScientificCategories() != null) {
            List<DictionaryScientificCategory> items = payload.getScientificCategories().stream().map(dictionaryMapper::toEntity).toList();
            dictionaryScientificCategoryRepository.saveAll(items);
        }
        if (payload.getTrCategories() != null) {
            List<DictionaryTRCategory> items = payload.getTrCategories().stream().map(dictionaryMapper::toEntity).toList();
            dictionaryTRCategoryRepository.saveAll(items);
        }
        if (payload.getRepositories() != null) {
            List<DictionaryRepository> items = payload.getRepositories().stream().map(dictionaryMapper::toEntity).toList();
            dictionaryRepositoryRepository.saveAll(items);
        }
        if (payload.getCountries() != null) {
            List<DictionaryCountry> items = payload.getCountries().stream().map(dictionaryMapper::toEntity).toList();
            dictionaryCountryRepository.saveAll(items);
        }

        if (payload.getLanguages() != null) {
            List<DictionaryLanguage> items = payload.getLanguages().stream().map(dictionaryMapper::toEntity).toList();
            dictionaryLanguageRepository.saveAll(items);
        }
    }

}
