package com.osatum.wbn.web.report.controller;

import com.osatum.wbn.application.report.survey.dto.QuestionnaireReportAvailabilityDto;
import com.osatum.wbn.application.report.survey.sevice.QuestionnaireReportFileService;
import com.osatum.wbn.application.report.survey.sevice.QuestionnaireReportFileService.QuestionnaireReportFile;
import com.osatum.wbn.application.report.survey.sevice.QuestionnaireReportFileService.QuestionnaireReportLanguage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Tag(name = "Questionnaire Reports", description = "Dostepnosc i pobieranie raportow ankiet")
@RestController
@RequestMapping("/api/reports/questionnaires/2024")
@RequiredArgsConstructor
public class QuestionnaireReportController {
    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final QuestionnaireReportFileService questionnaireReportFileService;

    @GetMapping("/availability")
    @Operation(summary = "Dostepnosc raportow ankiet 2024")
    @ApiResponse(responseCode = "200", description = "Zwrocono dostepnosc raportow PL i EN",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = QuestionnaireReportAvailabilityDto.class)))
    public QuestionnaireReportAvailabilityDto questionnaireReportAvailability() {
        return questionnaireReportFileService.availability();
    }

    @GetMapping(value = "/{language}", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(summary = "Pobranie raportu ankiet 2024")
    @ApiResponse(responseCode = "200", description = "Zwrocono raport XLSX",
            content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "400", description = "Nieobslugiwana wersja jezykowa")
    @ApiResponse(responseCode = "404", description = "Raport nie istnieje")
    public ResponseEntity<Resource> downloadQuestionnaireReport2024(
            @PathVariable("language")
            @Parameter(
                    name = "language",
                    in = ParameterIn.PATH,
                    required = true,
                    description = "Wersja jezykowa: pl lub en"
            )
            String language
    ) {
        QuestionnaireReportLanguage reportLanguage = QuestionnaireReportLanguage.fromCode(language)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unsupported report language."));
        QuestionnaireReportFile report = questionnaireReportFileService.findReport(reportLanguage)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Questionnaire report is unavailable."));

        String contentDisposition = ContentDisposition.attachment()
                .filename(report.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString();
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .contentLength(report.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(report.resource());
    }
}
