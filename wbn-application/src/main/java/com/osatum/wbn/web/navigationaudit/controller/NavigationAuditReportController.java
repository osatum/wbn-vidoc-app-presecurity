package com.osatum.wbn.web.navigationaudit.controller;

import com.osatum.wbn.application.shared.exception.NavigationAuditReportGenerationException;
import com.osatum.wbn.application.system.dto.NavigationAuditReportRequest;
import com.osatum.wbn.application.system.dto.PreparedNavigationAuditReport;
import com.osatum.wbn.application.system.service.NavigationAuditReportService;
import com.osatum.wbn.web.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Udostępnia pobieranie raportów audytu w formacie XLS lub XLSX zależnie od typu raportu.
 * Dostęp do endpointów raportowych wymaga uprawnienia ADMIN w konfiguracji zabezpieczeń.
 */
@RestController
@RequestMapping("/api/navigation-audit/reports")
@RequiredArgsConstructor
@Tag(name = "NavigationAuditReports")
public class NavigationAuditReportController {
    private final NavigationAuditReportService reportService;
    public static final String XLSX_MEDIA_TYPE = PreparedNavigationAuditReport.XLSX_MEDIA_TYPE;
    public static final String XLS_MEDIA_TYPE = PreparedNavigationAuditReport.XLS_MEDIA_TYPE;

    /**
     * Generuje wybrany raport za zadany zakres dat i zwraca gotowy plik jako załącznik.
     * Przygotowanie pliku przed wysłaniem odpowiedzi pozwala zgłosić błąd generowania zamiast częściowego raportu.
     * Zamknięcie strumienia odpowiedzi usuwa plik tymczasowy; odpowiedź nie jest przechowywana w pamięci podręcznej.
     */
    @PostMapping("/export")
    @Operation(operationId = "exportNavigationAuditReport", summary = "Export activity report as XLS or XLSX")
    @ApiResponse(responseCode = "200", content = {
            @Content(mediaType = XLSX_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = XLS_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary"))})
    @ApiResponse(responseCode = "400", description = "Invalid input or report limit exceeded",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Administrator authority required", content = @Content)
    @ApiResponse(responseCode = "500", description = "Report generation failed",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<Resource> export(@Valid @RequestBody NavigationAuditReportRequest request) {
        var report = reportService.export(request);
        try {
            long length = Files.size(report.path());
            var resource = new InputStreamResource(report.openStream());
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(report.mediaType()))
                    .contentLength(length).cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(report.filename()).build().toString())
                    .body(resource);
        } catch (IOException exception) {
            // Jeśli nie uda się przygotować odpowiedzi, plik trzeba usunąć bez udziału strumienia HTTP.
            try {
                report.close();
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw new NavigationAuditReportGenerationException(exception);
        }
    }
}
