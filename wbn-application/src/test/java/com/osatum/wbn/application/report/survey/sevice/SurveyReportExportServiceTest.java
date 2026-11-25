package com.osatum.wbn.application.report.survey.sevice;

import com.osatum.wbn.application.dictionary.service.DictionaryCountryService;
import com.osatum.wbn.application.report.survey.assembler.SurveyReportDtoAssembler;
import com.osatum.wbn.application.report.survey.dto.JournalReportDto;
import com.osatum.wbn.application.report.survey.dto.SurveyReportDto;
import com.osatum.wbn.domain.service.journal.AuthorFeeSelectionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.context.MessageSource;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SurveyReportExportServiceTest {

    @TempDir
    private Path tempDir;

    private final SurveyReportJournalService journalService = mock(SurveyReportJournalService.class);
    private final SurveyReportDtoAssembler assembler = new SurveyReportDtoAssembler(
            mock(MessageSource.class),
            mock(DictionaryCountryService.class),
            new AuthorFeeSelectionPolicy()
    );
    private final SurveyReportGenerator generator = mock(SurveyReportGenerator.class);
    private final SurveyReportExportService service = new SurveyReportExportService(
            journalService,
            assembler,
            generator
    );

    @Test
    void exportsBothLanguagesUsingTenThreadsAndJournalIdOrder() throws Exception {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        doAnswer(invocation -> {
            int running = active.incrementAndGet();
            maxActive.accumulateAndGet(running, Math::max);
            try {
                Thread.sleep(75);
                Long journalId = invocation.getArgument(0);
                Locale locale = invocation.getArgument(2);
                return Optional.of(report(journalId, locale.getLanguage()));
            } finally {
                active.decrementAndGet();
            }
        }).when(journalService).assemble(any(Long.class), eq(2024), any(Locale.class));
        doAnswer(invocation -> {
            String language = invocation.getArgument(2);
            OutputStream output = invocation.getArgument(3);
            output.write((language.equals("pl") ? "polish" : "english").getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(generator).generate(
                any(SurveyReportDto.class),
                eq(2024),
                any(String.class),
                any(OutputStream.class)
        );
        Path polish = tempDir.resolve("report-pl.xlsx");
        Path english = tempDir.resolve("report-en.xlsx");
        List<Long> journalIds = List.of(12L, 1L, 8L, 3L, 11L, 2L, 9L, 4L, 10L, 5L, 7L, 6L);

        var summary = service.export(journalIds, 2024, polish.toString(), english.toString());

        assertThat(summary.processedJournals()).isEqualTo(12);
        assertThat(summary.includedJournals()).isEqualTo(12);
        assertThat(maxActive).hasValue(10);
        assertThat(Files.readString(polish)).isEqualTo("polish");
        assertThat(Files.readString(english)).isEqualTo("english");
        ArgumentCaptor<SurveyReportDto> polishReport = ArgumentCaptor.forClass(SurveyReportDto.class);
        ArgumentCaptor<SurveyReportDto> englishReport = ArgumentCaptor.forClass(SurveyReportDto.class);
        verify(generator).generate(
                polishReport.capture(),
                eq(2024),
                eq("pl"),
                any(OutputStream.class)
        );
        verify(generator).generate(
                englishReport.capture(),
                eq(2024),
                eq("en"),
                any(OutputStream.class)
        );
        List<String> expectedIds = LongStream.rangeClosed(1, 12).mapToObj(Long::toString).toList();
        assertThat(polishReport.getValue().getJournals())
                .extracting(JournalReportDto::getJournalId)
                .containsExactlyElementsOf(expectedIds);
        assertThat(englishReport.getValue().getJournals())
                .extracting(JournalReportDto::getJournalId)
                .containsExactlyElementsOf(expectedIds);
    }

    @Test
    void keepsExistingReportsWhenAnyJournalFails() throws Exception {
        Path polish = tempDir.resolve("report-pl.xlsx");
        Path english = tempDir.resolve("report-en.xlsx");
        Files.writeString(polish, "old-polish");
        Files.writeString(english, "old-english");
        when(journalService.assemble(eq(1L), eq(2024), any(Locale.class))).thenReturn(Optional.empty());
        when(journalService.assemble(eq(2L), eq(2024), any(Locale.class)))
                .thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> service.export(
                List.of(1L, 2L),
                2024,
                polish.toString(),
                english.toString()
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("journal 2");

        assertThat(Files.readString(polish)).isEqualTo("old-polish");
        assertThat(Files.readString(english)).isEqualTo("old-english");
        verify(generator, never()).generate(any(), any(), any(), any());
    }

    private SurveyReportDto report(Long journalId, String language) {
        JournalReportDto row = new JournalReportDto();
        row.setJournalId(journalId.toString());
        row.setTitleOrginal(language);
        SurveyReportDto report = new SurveyReportDto();
        report.setJournals(List.of(row));
        return report;
    }
}
