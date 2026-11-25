package com.osatum.wbn.web.home.controller;

import com.osatum.wbn.application.institution.dto.InstitutionsDetectionDto;
import com.osatum.wbn.application.institution.service.InstitutionDetectionService;
import com.osatum.wbn.web.shared.request.ClientRequestMetadataResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Home", description = "Dane startowe aplikacji i diagnostyka")
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {
    private final InstitutionDetectionService detectionService;
    private final ClientRequestMetadataResolver requestMetadataResolver;

    @GetMapping
    @Operation(
            summary = "Wykrywanie instytucji dla adresu IP",
            description = "Zwraca informacje o adresie IP klienta oraz instytucjach dopasowanych do aktywnych reguł IP, razem z informacją o dostępności logowania globalnego."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono dane wykrywania instytucji",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = InstitutionsDetectionDto.class)))
    public InstitutionsDetectionDto home(HttpServletRequest request) {
        String clientIp = requestMetadataResolver.resolve(request).ipAddress();
        return detectionService.findInstitutionsForIp(clientIp);
    }

    @GetMapping("/health")
    @Operation(
            summary = "Sprawdzenie działania aplikacji",
            description = "Zwraca prostą odpowiedź diagnostyczną potwierdzającą dostępność endpointu."
    )
    @ApiResponse(responseCode = "200", description = "Aplikacja odpowiada poprawnie")
    public String health() {
        return "OK";
    }
}
