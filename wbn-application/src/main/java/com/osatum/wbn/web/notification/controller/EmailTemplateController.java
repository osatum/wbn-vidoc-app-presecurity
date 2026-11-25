package com.osatum.wbn.web.notification.controller;

import com.osatum.wbn.application.notification.dto.EmailTemplateDto;
import com.osatum.wbn.application.notification.dto.EmailTemplateUpdateDto;
import com.osatum.wbn.application.notification.service.EmailTemplateService;
import com.osatum.wbn.web.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "EmailTemplates", description = "Zarządzanie globalnymi szablonami wiadomości e-mail")
@RequestMapping("/api/email-templates")
@RequiredArgsConstructor
public class EmailTemplateController {
    private final EmailTemplateService emailTemplateService;

    @GetMapping
    @Operation(
            summary = "Lista szablonów e-mail",
            description = "Zwraca listę globalnych szablonów wiadomości e-mail posortowaną według typu wiadomości i języka."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono listę szablonów e-mail")
    public List<EmailTemplateDto> findEmailTemplates() {
        return emailTemplateService.findTemplates();
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Szczegóły szablonu e-mail",
            description = "Zwraca szczegółowe dane wybranego szablonu wiadomości e-mail, w tym temat oraz treść HTML i tekstową."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono szczegóły szablonu e-mail",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = EmailTemplateDto.class)))
    @ApiResponse(responseCode = "404", description = "Nie znaleziono szablonu e-mail",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public EmailTemplateDto findEmailTemplate(@PathVariable(name = "id") Long id) {
        return emailTemplateService.findTemplate(id);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Aktualizacja szablonu e-mail",
            description = "Aktualizuje dane globalnego szablonu wiadomości e-mail, w tym nazwę, język, temat, treść HTML, treść tekstową i stan."
    )
    @ApiResponse(responseCode = "200", description = "Szablon e-mail został zaktualizowany",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = EmailTemplateDto.class)))
    @ApiResponse(responseCode = "400", description = "Błąd walidacji formularza",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Nie znaleziono szablonu e-mail",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Szablon dla tego typu wiadomości i języka już istnieje",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public EmailTemplateDto updateEmailTemplate(
            @PathVariable(name = "id") Long id,
            @Valid @RequestBody EmailTemplateUpdateDto form
    ) {
        return emailTemplateService.updateTemplate(id, form);
    }
}
