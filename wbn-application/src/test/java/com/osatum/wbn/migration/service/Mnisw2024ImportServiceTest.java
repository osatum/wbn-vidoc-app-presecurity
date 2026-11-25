package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.dictionary.service.DictionaryMniswDisciplineService;
import com.osatum.wbn.application.journal.event.JournalSavedEvent;
import com.osatum.wbn.domain.model.dictionary.DictionaryMniswDiscipline;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.journal.MniswDiscipline;
import com.osatum.wbn.domain.model.journal.Rating;
import com.osatum.wbn.domain.repository.journal.JournalRepository;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Mnisw2024ImportServiceTest {
    private final JournalRepository journalRepository = mock();
    private final ApplicationEventPublisher publisher = mock();
    private final DictionaryMniswDisciplineService dictionaryService = mock();
    private final Mnisw2024ImportService service = new Mnisw2024ImportService(
            journalRepository,
            publisher,
            dictionaryService
    );

    @TempDir
    Path tempDir;

    @Test
    void readsOnlyRowsWithMeinValue() throws Exception {
        Path file = tempDir.resolve("mnisw-2024.xlsx");
        createMniswFile(file);

        List<Mnisw2024ImportService.Mnisw2024Record> records = service.readRecords(file.toString());

        assertThat(records).hasSize(2);
        assertThat(records.getFirst().journalId()).isEqualTo(123L);
        assertThat(records.getFirst().value()).isEqualTo(100.0);
        assertThat(records.getLast().journalId()).isEqualTo(789L);
        assertThat(records.getLast().value()).isEqualTo(70.5);
        assertThat(records).allSatisfy(record -> assertThat(record.disciplineCodes()).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"xls", "xlsx"})
    void readsDisciplineCodesFromBooleanColumns(String extension) throws Exception {
        knownDisciplines("101", "2021", "2061", "1001");
        Path file = tempDir.resolve("mnisw-2024." + extension);
        createFile(file,
                new Object[]{"2021", "mein_value", "journal_id", 101, "2061", "1001"},
                new Object[]{true, 100, 123, false, true, true},
                new Object[]{true, null, 456, false, false, false},
                new Object[]{false, "70,5", "789", " true ", null, false},
                new Object[]{false, 20, 987, false, false, false}
        );

        var records = service.readRecords(file.toString());

        assertThat(records).hasSize(3);
        assertThat(records.getFirst().journalId()).isEqualTo(123L);
        assertThat(records.getFirst().disciplineCodes()).containsExactlyInAnyOrder("2021", "2061", "1001");
        assertThat(records.getFirst().sourceFile()).isEqualTo(file.getFileName().toString());
        assertThat(records.getFirst().rowNumber()).isEqualTo(2);
        assertThat(records.get(1).journalId()).isEqualTo(789L);
        assertThat(records.get(1).disciplineCodes()).containsExactly("101");
        assertThat(records.getLast().disciplineCodes()).isEmpty();
    }

    @Test
    void rejectsUnknownDisciplineCodeEvenWhenNotSelected() throws Exception {
        Path file = tempDir.resolve("unknown-discipline.xls");
        createFile(file,
                new Object[]{"journal_id", "mein_value", "9999"},
                new Object[]{123, 100, false}
        );

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown MNISW discipline code 9999")
                .hasMessageContaining("unknown-discipline.xls");
    }

    @Test
    void rejectsDuplicateDisciplineColumns() throws Exception {
        knownDisciplines("101");
        Path file = tempDir.resolve("duplicate-discipline.xlsx");
        createFile(file,
                new Object[]{"journal_id", "mein_value", "101", "101"},
                new Object[]{123, 100, true, false}
        );

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate MNISW discipline code 101");
    }

    @Test
    void rejectsInvalidDisciplineFlagWithSourceLocation() throws Exception {
        knownDisciplines("101");
        Path file = tempDir.resolve("invalid-flag.xls");
        createFile(file,
                new Object[]{"journal_id", "mein_value", "101"},
                new Object[]{123, 100, "yes"}
        );

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("discipline flag for 101")
                .hasMessageContaining("invalid-flag.xls:2: yes");
    }

    @Test
    void addsMnisw2024RatingToJournal() {
        Journal journal = new Journal();
        journal.setId(123L);
        Rating existingRating = new Rating();
        existingRating.setJournal(journal);
        existingRating.setRatingCode(Rating.ICV_RATING_CODE);
        journal.setRatings(new HashSet<>(List.of(existingRating)));
        when(journalRepository.findById(123L)).thenReturn(Optional.of(journal));

        service.addRating(new Mnisw2024ImportService.Mnisw2024Record(
                123L, 140.0, Set.of("2021", "2061"), "mnisw-2024.xlsx", 2
        ));

        assertThat(journal.getRatings()).hasSize(2);
        Rating rating = journal.getRatings().stream()
                .filter(item -> Rating.MNW_RATING_CODE.equals(item.getRatingCode()))
                .findFirst()
                .orElseThrow();
        assertThat(rating.getValueNum()).isEqualTo(140.0);
        assertThat(rating.getRatingCode()).isEqualTo(Rating.MNW_RATING_CODE);
        assertThat(rating.getYear()).isEqualTo(2024);
        assertThat(rating.getJournal()).isSameAs(journal);
        assertThat(rating.getMniswDisciplines())
                .extracting(MniswDiscipline::getDisciplineCode)
                .containsExactlyInAnyOrder("2021", "2061");
        assertThat(rating.getMniswDisciplines()).allSatisfy(discipline ->
                assertThat(discipline.getRating()).isSameAs(rating)
        );
        verify(journalRepository).save(journal);
        verify(publisher).publishEvent(any(JournalSavedEvent.class));
    }

    @Test
    void updatesExistingRatingAndReplacesDisciplinesOnRepeatedImport() {
        Journal journal = new Journal();
        journal.setId(123L);
        Rating rating = new Rating();
        rating.setId(456L);
        rating.setJournal(journal);
        rating.setRatingCode(Rating.MNW_RATING_CODE);
        rating.setYear(2024);
        rating.setValueNum(70.0);
        MniswDiscipline oldDiscipline = new MniswDiscipline();
        oldDiscipline.setRating(rating);
        oldDiscipline.setDisciplineCode("101");
        Set<MniswDiscipline> disciplines = new HashSet<>(Set.of(oldDiscipline));
        rating.setMniswDisciplines(disciplines);
        Rating previousYear = new Rating();
        previousYear.setRatingCode(Rating.MNW_RATING_CODE);
        previousYear.setYear(2023);
        previousYear.setValueNum(20.0);
        Rating icvRating = new Rating();
        icvRating.setRatingCode(Rating.ICV_RATING_CODE);
        icvRating.setYear(2024);
        journal.setRatings(new HashSet<>(Set.of(rating, previousYear, icvRating)));
        when(journalRepository.findById(123L)).thenReturn(Optional.of(journal));
        var record = new Mnisw2024ImportService.Mnisw2024Record(
                123L, 140.0, Set.of("2021", "2061"), "mnisw-2024.xls", 2
        );

        service.addRating(record);
        service.addRating(record);

        assertThat(journal.getRatings()).containsExactlyInAnyOrder(rating, previousYear, icvRating);
        assertThat(rating.getId()).isEqualTo(456L);
        assertThat(rating.getValueNum()).isEqualTo(140.0);
        assertThat(previousYear.getValueNum()).isEqualTo(20.0);
        assertThat(rating.getMniswDisciplines()).isSameAs(disciplines).doesNotContain(oldDiscipline);
        assertThat(disciplines).extracting(MniswDiscipline::getDisciplineCode)
                .containsExactlyInAnyOrder("2021", "2061");
        assertThat(disciplines).allSatisfy(discipline -> assertThat(discipline.getRating()).isSameAs(rating));
        verify(journalRepository, times(2)).save(journal);
    }

    @Test
    void clearsExistingDisciplinesWhenNoneSelected() {
        Journal journal = new Journal();
        Rating rating = new Rating();
        rating.setRatingCode(Rating.MNW_RATING_CODE);
        rating.setYear(2024);
        MniswDiscipline discipline = new MniswDiscipline();
        discipline.setRating(rating);
        discipline.setDisciplineCode("101");
        rating.setMniswDisciplines(new HashSet<>(Set.of(discipline)));
        journal.setRatings(new HashSet<>(Set.of(rating)));
        when(journalRepository.findById(123L)).thenReturn(Optional.of(journal));

        service.addRating(new Mnisw2024ImportService.Mnisw2024Record(
                123L, 20.0, Set.of(), "mnisw-2024.xls", 2
        ));

        assertThat(journal.getRatings()).containsExactly(rating);
        assertThat(rating.getMniswDisciplines()).isEmpty();
    }

    @Test
    void skipsMissingJournal() {
        service.addRating(new Mnisw2024ImportService.Mnisw2024Record(
                123L, 140.0, Set.of("101"), "mnisw-2024.xls", 2
        ));

        verify(journalRepository, never()).save(any());
        verify(publisher, never()).publishEvent(any(JournalSavedEvent.class));
    }

    @Test
    void skipsMissingValueWithoutLoadingJournal() {
        service.addRating(new Mnisw2024ImportService.Mnisw2024Record(
                123L, null, Set.of(), "mnisw-2024.xlsx", 2
        ));

        verify(journalRepository, never()).findById(123L);
        verify(journalRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private void createMniswFile(Path file) throws Exception {
        createFile(file,
                new Object[]{"journal_id", "mein_value"},
                new Object[]{123, 100},
                new Object[]{456, null},
                new Object[]{"789", "70,5"}
        );
    }

    private void knownDisciplines(String... codes) {
        for (String code : codes) {
            DictionaryMniswDiscipline discipline = new DictionaryMniswDiscipline();
            discipline.setCode(code);
            when(dictionaryService.getByCode(code)).thenReturn(discipline);
        }
    }

    private void createFile(Path file, Object[] headers, Object[]... rows) throws Exception {
        try (Workbook workbook = file.toString().endsWith(".xls") ? new HSSFWorkbook() : new XSSFWorkbook()) {
            var sheet = workbook.createSheet("mnisw");
            writeRow(sheet.createRow(0), headers);
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                writeRow(sheet.createRow(rowIndex + 1), rows[rowIndex]);
            }

            try (OutputStream outputStream = Files.newOutputStream(file)) {
                workbook.write(outputStream);
            }
        }
    }

    private void writeRow(Row row, Object[] values) {
        for (int column = 0; column < values.length; column++) {
            switch (values[column]) {
                case null -> {
                }
                case Boolean flag -> row.createCell(column).setCellValue(flag);
                case Number number -> row.createCell(column).setCellValue(number.doubleValue());
                case String value -> row.createCell(column).setCellValue(value);
                default -> throw new IllegalArgumentException("Unsupported test cell value: " + values[column]);
            }
        }
    }
}
