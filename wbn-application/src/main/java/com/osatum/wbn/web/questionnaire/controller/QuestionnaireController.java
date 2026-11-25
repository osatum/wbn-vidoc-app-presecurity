package com.osatum.wbn.web.questionnaire.controller;

import com.osatum.wbn.application.questionnaire.service.QuestionnaireService;
import com.osatum.wbn.application.questionnaire.view.QuestionnaireView;
import com.osatum.wbn.application.report.survey.dto.SurveyReportResult;
import com.osatum.wbn.web.error.ApiErrorResponse;
import com.osatum.wbn.web.questionnaire.response.QuestionnaireResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Questionnaire", description = "Udostępnianie ankiet czasopism")
public class QuestionnaireController {

    private final QuestionnaireService questionnaireService;

    @GetMapping(value = "/journal/{id}/questionnaire", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Pobranie aktualnej ankiety czasopisma",
            description = "Zwraca ankietę opublikowanego surveyYear; brak parametru oznacza aktualny rok oceny."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono aktualną ankietę czasopisma",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuestionnaireResponse.class)))
    @ApiResponse(responseCode = "404", description = "Nie znaleziono aktualnej ankiety dla czasopisma",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "500", description = "Błąd podczas pobierania ankiety czasopisma",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<?> findCurrentQuestionnaire(@PathVariable(name = "id") Long id,
                                                     @RequestParam(name = "surveyYear", required = false) Integer surveyYear) {
        try {
            Optional<QuestionnaireView> oQuestionnaireView = surveyYear == null ? questionnaireService.findCurrentQuestionnaire(id)
                    : questionnaireService.findQuestionnaire(id, surveyYear);
            if (oQuestionnaireView.isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(ApiErrorResponse.of(
                                "QUESTIONNAIRE_NOT_FOUND",
                                "No current questionnaire found for journal " + id
                        ));
            }
            return ResponseEntity.ok(new QuestionnaireResponse(oQuestionnaireView.get()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiErrorResponse.of("INVALID_REQUEST", exception.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiErrorResponse.of(
                            "QUESTIONNAIRE_LOAD_FAILED",
                            "Unable to load current questionnaire"
                    ));
        }
    }

    @GetMapping(value = "/journal/{id}/questionnaire/report", produces = "application/vnd.ms-excel")
    @Operation(
            summary = "Pobranie raportu ankiety czasopisma",
            description = "Generuje raport XLS ankiety opublikowanego surveyYear; brak parametru oznacza aktualny rok oceny."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono raport ankiety czasopisma")
    @ApiResponse(responseCode = "404", description = "Nie znaleziono aktualnej ankiety dla czasopisma",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "500", description = "Błąd podczas generowania raportu ankiety",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<?> generateCurrentQuestionnaireReport(
            @PathVariable(name = "id") Long id,
            @RequestParam(name = "language", defaultValue = "pl") String language,
            @RequestParam(name = "surveyYear", required = false) Integer surveyYear
    ) {
        try {
            Optional<SurveyReportResult> report = surveyYear == null ? questionnaireService.generateCurrentQuestionnaireReport(id, language)
                    : questionnaireService.generateQuestionnaireReport(id, language, surveyYear);
            if (report.isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(ApiErrorResponse.of(
                                "QUESTIONNAIRE_NOT_FOUND",
                                "No current questionnaire found for journal " + id
                        ));
            }

            SurveyReportResult result = report.get();
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.filename() + "\"")
                    .body(result.content());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiErrorResponse.of("INVALID_REQUEST", exception.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiErrorResponse.of(
                            "QUESTIONNAIRE_REPORT_GENERATION_FAILED",
                            "Unable to generate current questionnaire report"
                    ));
        }
    }

    @GetMapping(value = "/questionnaires/years", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Lista lat ankiet",
            description = "Zwraca listę lat, dla których w systemie istnieją ankiety czasopism."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono listę dostępnych lat ankiet")
    public List<Integer> findAvailableYears() {
        return questionnaireService.findAvailableYears();
    }

}
