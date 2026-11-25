package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.dictionary.service.DictionaryMniswDisciplineService;
import com.osatum.wbn.application.journal.event.JournalSavedEvent;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.journal.MniswDiscipline;
import com.osatum.wbn.domain.model.journal.Rating;
import com.osatum.wbn.domain.repository.journal.JournalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Zasila obowiązującą punktację MNiSW z osobnego pliku Excel, niezależnie od importu ankiet rocznych.
 * Zapisuje ocenę MNW i przypisania dyscyplin dla roku wykazu 2024; nie wyznacza wykazu z najnowszej ankiety.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Mnisw2024ImportService {
    private static final int RATING_YEAR = 2024;

    private final JournalRepository journalRepository;
    private final ApplicationEventPublisher publisher;
    private final DictionaryMniswDisciplineService dictionaryMniswDisciplineService;

    /**
     * Odczytuje pierwszy arkusz po nagłówkach journal_id, mein_value i kodach dyscyplin; pomija wiersze bez ID lub punktacji.
     * Przy powtórzonym ID zachowuje ostatni rekord z pliku.
     */
    public List<Mnisw2024Record> readRecords(String sourcePath) throws Exception {
        File file = Path.of(sourcePath).toFile();
        if (!file.isFile()) {
            throw new IllegalStateException("Cannot find MNISW 2024 Excel file: " + sourcePath);
        }

        Map<Long, Mnisw2024Record> records = new LinkedHashMap<>();
        HeaderColumns[] columns = new HeaderColumns[1];
        MigrationExcelReader.readFirstSheet(file, row -> {
            if (columns[0] == null) {
                columns[0] = headerColumns(row, file);
                return;
            }
            Long journalId = readLong(row.value(columns[0].journalId()));
            if (journalId == null) {
                return;
            }
            Double value = readDouble(row.value(columns[0].value()));
            if (value == null) {
                return;
            }
            records.put(journalId, new Mnisw2024Record(
                    journalId,
                    value,
                    disciplineCodes(row, columns[0], file),
                    file.getName(),
                    row.rowIndex() + 1
            ));
        });
        return List.copyOf(records.values());
    }

    /**
     * Tworzy lub aktualizuje ocenę MNW za rok 2024, zastępuje jej dyscypliny i publikuje zdarzenie odświeżenia indeksu.
     * Brak czasopisma nie tworzy nowego rekordu; metoda zachowuje oceny innych typów i lat.
     */
    @Transactional
    public void addRating(Mnisw2024Record record) {
        if (record.value() == null) {
            return;
        }

        Optional<Journal> journalResult = journalRepository.findById(record.journalId());
        if (journalResult.isEmpty()) {
            log.debug("Skipping MNISW 2024 rating for missing journal {}", record.journalId());
            return;
        }

        Journal journal = journalResult.get();
        Set<Rating> ratings = journal.getRatings();
        if (ratings == null) {
            ratings = new HashSet<>();
            journal.setRatings(ratings);
        }
        Rating rating = ratings.stream()
                .filter(item -> Rating.MNW_RATING_CODE.equals(item.getRatingCode()))
                .filter(item -> Integer.valueOf(RATING_YEAR).equals(item.getYear()))
                .findFirst()
                .orElseGet(Rating::new);
        rating.setJournal(journal);
        rating.setValueNum(record.value());
        rating.setRatingCode(Rating.MNW_RATING_CODE);
        rating.setYear(RATING_YEAR);
        if (rating.getMniswDisciplines() == null) {
            rating.setMniswDisciplines(new HashSet<>());
        } else {
            rating.getMniswDisciplines().clear();
        }
        for (String code : record.disciplineCodes()) {
            MniswDiscipline discipline = new MniswDiscipline();
            discipline.setRating(rating);
            discipline.setDisciplineCode(code);
            rating.getMniswDisciplines().add(discipline);
        }
        ratings.add(rating);
        journalRepository.save(journal);
        publisher.publishEvent(new JournalSavedEvent(journal.getId()));
    }

    private HeaderColumns headerColumns(
            MigrationExcelReader.RowData header,
            File file
    ) {
        Integer journalId = null;
        Integer value = null;
        Map<Integer, String> disciplines = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> cell : header.values().entrySet()) {
            int cellIndex = cell.getKey();
            String headerValue = normalizedHeader(cell.getValue());
            if ("journalid".equals(headerValue)) {
                journalId = cellIndex;
            } else if ("meinvalue".equals(headerValue)) {
                value = cellIndex;
            } else {
                String code = cell.getValue().trim();
                if (dictionaryMniswDisciplineService.getByCode(code) == null) {
                    throw new IllegalStateException("Unknown MNISW discipline code " + code + " in " + file.getName());
                }
                if (disciplines.containsValue(code)) {
                    throw new IllegalStateException("Duplicate MNISW discipline code " + code + " in " + file.getName());
                }
                disciplines.put(cellIndex, code);
            }
        }

        if (journalId == null || value == null) {
            throw new IllegalStateException("MNISW 2024 file has invalid header: " + file.getName());
        }
        return new HeaderColumns(journalId, value, disciplines);
    }

    private Set<String> disciplineCodes(MigrationExcelReader.RowData row, HeaderColumns columns, File file) {
        Set<String> codes = new HashSet<>();
        for (Map.Entry<Integer, String> column : columns.disciplines().entrySet()) {
            String flag = row.value(column.getKey());
            if (flag == null || flag.isBlank() || "FALSE".equalsIgnoreCase(flag.trim())) {
                continue;
            }
            if (!"TRUE".equalsIgnoreCase(flag.trim())) {
                throw new IllegalArgumentException(
                        "Invalid MNISW discipline flag for " + column.getValue() + " in "
                                + file.getName() + ":" + (row.rowIndex() + 1) + ": " + flag
                );
            }
            codes.add(column.getValue());
        }
        return codes;
    }

    private Long readLong(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(normalizeNumber(value)).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid journal_id value: " + value, e);
        }
    }

    private Double readDouble(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(normalizeNumber(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid mein_value value: " + value, e);
        }
    }

    private String normalizeNumber(String value) {
        return value.trim()
                .replace("\u00A0", "")
                .replace(" ", "")
                .replace(',', '.');
    }

    private String normalizedHeader(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public record Mnisw2024Record(
            Long journalId,
            Double value,
            Set<String> disciplineCodes,
            String sourceFile,
            int rowNumber
    ) {
        public Mnisw2024Record {
            disciplineCodes = Set.copyOf(disciplineCodes);
        }

        public String itemName() {
            return sourceFile + ":" + rowNumber + ":journal-" + journalId;
        }
    }

    private record HeaderColumns(int journalId, int value, Map<Integer, String> disciplines) {
    }
}
