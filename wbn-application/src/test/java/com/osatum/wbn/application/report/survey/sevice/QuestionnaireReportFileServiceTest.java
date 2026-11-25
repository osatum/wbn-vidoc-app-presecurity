package com.osatum.wbn.application.report.survey.sevice;

import com.osatum.wbn.application.report.survey.dto.QuestionnaireReportAvailabilityDto;
import com.osatum.wbn.application.report.survey.sevice.QuestionnaireReportFileService.QuestionnaireReportFile;
import com.osatum.wbn.application.report.survey.sevice.QuestionnaireReportFileService.QuestionnaireReportLanguage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionnaireReportFileServiceTest {

    @TempDir
    private Path tempDir;

    @Test
    void exposesOnlyExistingReadableReports() throws Exception {
        Path polishReport = tempDir.resolve("questionnaires-2024-pl.xlsx");
        byte[] content = {1, 2, 3, 4};
        Files.write(polishReport, content);
        QuestionnaireReportFileService service = new QuestionnaireReportFileService();
        ReflectionTestUtils.setField(service, "outputQuestionaries2024xls", polishReport.toString());
        ReflectionTestUtils.setField(
                service,
                "outputQuestionaries2024xlsEn",
                tempDir.resolve("questionnaires-2024-en.xlsx").toString()
        );

        QuestionnaireReportAvailabilityDto availability = service.availability();
        QuestionnaireReportFile report = service.findReport(QuestionnaireReportLanguage.PL).orElseThrow();

        assertThat(availability.pl()).isTrue();
        assertThat(availability.en()).isFalse();
        assertThat(report.contentLength()).isEqualTo(content.length);
        assertThat(report.fileName()).isEqualTo("questionnaire-report-2024-pl.xlsx");
        try (InputStream inputStream = report.resource().getInputStream()) {
            assertThat(inputStream.readAllBytes()).isEqualTo(content);
        }
        assertThat(service.findReport(QuestionnaireReportLanguage.EN)).isEmpty();
    }

    @Test
    void treatsBlankAndInvalidPathsAsUnavailable() {
        QuestionnaireReportFileService service = new QuestionnaireReportFileService();
        ReflectionTestUtils.setField(service, "outputQuestionaries2024xls", " ");
        ReflectionTestUtils.setField(service, "outputQuestionaries2024xlsEn", "\u0000invalid");

        assertThat(service.availability()).isEqualTo(new QuestionnaireReportAvailabilityDto(false, false));
    }
}
