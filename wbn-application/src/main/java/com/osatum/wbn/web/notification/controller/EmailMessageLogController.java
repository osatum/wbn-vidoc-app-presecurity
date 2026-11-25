package com.osatum.wbn.web.notification.controller;

import com.osatum.wbn.application.notification.dto.EmailMessageLogListDto;
import com.osatum.wbn.application.notification.dto.EmailMessageResendResultDto;
import com.osatum.wbn.application.notification.query.EmailMessageLogListQuery;
import com.osatum.wbn.application.notification.service.EmailMessageLogService;
import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.web.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "EmailMessages", description = "Przegląd i ponawianie wysyłek wiadomości e-mail")
@RestController
@RequestMapping("/api/email-messages")
@RequiredArgsConstructor
public class EmailMessageLogController {
    private final EmailMessageLogService emailMessageLogService;

    @PostMapping("/search")
    @Operation(
            summary = "Wyszukiwanie wysyłek e-mail",
            description = "Zwraca stronicowaną listę logów wiadomości e-mail zgodnie z kryteriami wyszukiwania."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono stronicowaną listę wysyłek e-mail",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResult.class)))
    @ApiResponse(responseCode = "403", description = "Użytkownik nie ma uprawnień do przeglądania wysyłek e-mail")
    public PageResult<EmailMessageLogListDto> findEmailMessages(@RequestBody(required = false) EmailMessageLogListQuery query) {
        return emailMessageLogService.findEmailMessages(query);
    }

    @PostMapping("/{id}/resend")
    @Operation(
            summary = "Ponowienie wysyłki e-mail",
            description = "Zleca ponowną wysyłkę wiadomości e-mail na podstawie istniejącego logu. Dla linków aktywacyjnych generuje nowy link, a dla pozostałych wiadomości ponownie kolejkuje wiadomość."
    )
    @ApiResponse(responseCode = "200", description = "Ponowna wysyłka e-mail została zakolejkowana",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = EmailMessageResendResultDto.class)))
    @ApiResponse(responseCode = "400", description = "Wiadomości e-mail nie można ponowić",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Nie znaleziono logu wiadomości e-mail",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public EmailMessageResendResultDto resendEmailMessage(@PathVariable(name = "id") Long id) {
        return emailMessageLogService.resendEmailMessage(id);
    }
}
