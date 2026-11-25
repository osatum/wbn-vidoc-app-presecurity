package com.osatum.wbn.web.report.controller;

import com.osatum.wbn.application.report.survey.sevice.QuestionnaireReportFileService;
import com.osatum.wbn.application.report.survey.sevice.QuestionnaireReportFileService.QuestionnaireReportFile;
import com.osatum.wbn.application.report.survey.sevice.QuestionnaireReportFileService.QuestionnaireReportLanguage;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionnaireReportControllerTest {

    @Test
    void downloadsReportWithXlsxHeaders() {
        QuestionnaireReportFileService fileService = mock(QuestionnaireReportFileService.class);
        when(fileService.findReport(QuestionnaireReportLanguage.EN)).thenReturn(Optional.of(
                new QuestionnaireReportFile(
                        new ByteArrayResource(new byte[]{1, 2, 3}),
                        3,
                        "questionnaire-report-2024-en.xlsx"
                )
        ));
        QuestionnaireReportController controller = new QuestionnaireReportController(fileService);

        ResponseEntity<?> response = controller.downloadQuestionnaireReport2024("en");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentLength()).isEqualTo(3);
        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("questionnaire-report-2024-en.xlsx");
    }

    @Test
    void rejectsUnsupportedLanguageAndMissingReport() {
        QuestionnaireReportFileService fileService = mock(QuestionnaireReportFileService.class);
        when(fileService.findReport(QuestionnaireReportLanguage.PL)).thenReturn(Optional.empty());
        QuestionnaireReportController controller = new QuestionnaireReportController(fileService);

        assertThatThrownBy(() -> controller.downloadQuestionnaireReport2024("de"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(400));
        assertThatThrownBy(() -> controller.downloadQuestionnaireReport2024("pl"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(404));
    }
}
