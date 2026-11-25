package com.osatum.wbn.web.migration;

import com.osatum.wbn.migration.dto.MigrationImportStatusDto;
import com.osatum.wbn.migration.service.MigrationImportRunnerService;
import com.osatum.wbn.migration.service.QuestionnaireReportJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Uruchamia i monitoruje zadania zasilenia danych oraz niezależne generowanie raportów ankiet.
 * Pełny import zastępuje dane i odbudowuje indeks; odczyt statusu nie uruchamia kolejnego przebiegu.
 */
@Tag(name = "Migration", description = "Ręczne uruchamianie i monitorowanie importu danych migracyjnych")
@RestController
@RequestMapping("/api/migration")
@RequiredArgsConstructor
public class MigrationController {

    private final ObjectProvider<MigrationImportRunnerService> migrationImportRunnerService;
    private final ObjectProvider<QuestionnaireReportJobService> questionnaireReportJobService;

    @PostMapping("/import")
    @Operation(
            summary = "Uruchomienie importu migracyjnego",
            description = "Rozpoczyna asynchroniczny import danych migracyjnych, jeżeli mechanizm migracji jest dostępny w aktualnej konfiguracji aplikacji."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono status uruchomienia importu",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MigrationImportStatusDto.class)))
    @ApiResponse(responseCode = "500", description = "Import migracyjny jest niedostępny lub nie mógł zostać uruchomiony")
    public MigrationImportStatusDto startImport() {
        return migrationService().startImport();
    }

    @GetMapping("/import")
    @Operation(
            summary = "Uruchomienie importu migracyjnego metodą GET",
            description = "Uruchamia import migracyjny przez endpoint GET zachowany dla zgodności z istniejącymi wywołaniami administracyjnymi."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono status uruchomienia importu",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MigrationImportStatusDto.class)))
    @ApiResponse(responseCode = "500", description = "Import migracyjny jest niedostępny lub nie mógł zostać uruchomiony")
    public MigrationImportStatusDto startImportFromGet() {
        return migrationService().startImport();
    }

    @GetMapping("/import/status")
    @Operation(
            summary = "Status importu migracyjnego",
            description = "Zwraca bieżący status importu migracyjnego, w tym informację o aktywnym lub zakończonym przebiegu."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono status importu",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MigrationImportStatusDto.class)))
    @ApiResponse(responseCode = "500", description = "Import migracyjny jest niedostępny")
    public MigrationImportStatusDto status() {
        return migrationService().status();
    }

    @PostMapping("/questionnaire-report-2024")
    @Operation(
            summary = "Uruchomienie raportu ankiet 2024",
            description = "Rozpoczyna asynchroniczne generowanie polskiej i angielskiej wersji raportu XLSX bez uruchamiania importu migracyjnego."
    )
    @ApiResponse(responseCode = "200", description = "Zwrocono status uruchomienia raportu",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MigrationImportStatusDto.class)))
    @ApiResponse(responseCode = "500", description = "Job raportu jest niedostepny lub nie mogl zostac uruchomiony")
    public MigrationImportStatusDto startQuestionnaireReport2024() {
        return questionnaireReportService().startQuestionnaireReport2024();
    }

    @GetMapping("/questionnaire-report-2024/status")
    @Operation(
            summary = "Status raportu ankiet 2024",
            description = "Zwraca biezacy status oddzielnego jobu generujacego raporty XLSX ankiet 2024."
    )
    @ApiResponse(responseCode = "200", description = "Zwrocono status raportu",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MigrationImportStatusDto.class)))
    @ApiResponse(responseCode = "500", description = "Job raportu jest niedostepny")
    public MigrationImportStatusDto questionnaireReport2024Status() {
        return questionnaireReportService().status();
    }

    private MigrationImportRunnerService migrationService() {
        MigrationImportRunnerService service = migrationImportRunnerService.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("Migration import is unavailable.");
        }
        return service;
    }

    private QuestionnaireReportJobService questionnaireReportService() {
        QuestionnaireReportJobService service = questionnaireReportJobService.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("Questionnaire report job is unavailable.");
        }
        return service;
    }
}
