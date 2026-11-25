package com.osatum.wbn.migration.service;

import com.osatum.jml.migration.dto.JournalMigrationRecord;
import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.service.journal.JournalSurveyPolicy;
import com.osatum.wbn.infrastructure.persistence.JournalRepositoryAdapter;
import com.osatum.wbn.migration.assembler.ToJournalAssembler;
import com.osatum.wbn.migration.assembler.ToJournalParameters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.FileInputStream;

/**
 * Odtwarza model czasopisma z eksportu JSON i zapisuje go w bazie w transakcji jednego pliku.
 * Interpretację ankiet i oznaczeń opłat deleguje do istniejącego asemblera migracyjnego.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JournalBuildService {
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final ToJournalAssembler assembler;
    private final JournalRepositoryAdapter repository;
    private final JournalSurveyPolicy surveyPolicy;
    private final ConfigurationService configurationService;


    /**
     * Dopuszcza schematy eksportu 1.0 i 1.1 oraz sprawdza reguły ankiet przed zapisem,
     * aby błąd danych nie ujawnił się dopiero przy indeksowaniu po zatwierdzeniu transakcji.
     */
    @Transactional
    public Journal importJournalJson(File file) throws Exception {

        try (FileInputStream fis = new FileInputStream(file)) {
            JournalMigrationRecord record = mapper.readValue(fis, JournalMigrationRecord.class);
            if (!"1.0".equals(record.getSchemaVersion()) && !"1.1".equals(record.getSchemaVersion())) {
                throw new IllegalArgumentException("Unsupported journal schemaVersion: " + record.getSchemaVersion());
            }
            if (record.getJournal() == null) {
                throw new IllegalArgumentException("Missing journal payload");
            }
            ToJournalParameters parameters = new ToJournalParameters(null, record.getJournal(), record.getSchemaVersion());
            Journal journal = assembler.toEntity(parameters);
            surveyPolicy.resolvePublished(journal, configurationService.getCurrentRatingYear(), configurationService.getAvailableSearchYears());
            return repository.save(journal);
        }
    }

}
