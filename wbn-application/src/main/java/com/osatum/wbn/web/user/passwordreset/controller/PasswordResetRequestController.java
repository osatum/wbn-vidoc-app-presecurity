package com.osatum.wbn.web.user.passwordreset.controller;

import com.osatum.wbn.application.user.dto.PasswordResetRequestListDto;
import com.osatum.wbn.application.user.query.PasswordResetRequestListQuery;
import com.osatum.wbn.application.user.service.PasswordResetRequestService;
import com.osatum.wbn.application.shared.list.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "PasswordResetRequests", description = "Przegląd zgłoszeń resetu hasła")
@RestController
@RequestMapping("/api/password-reset-requests")
@RequiredArgsConstructor
public class PasswordResetRequestController {
    private final PasswordResetRequestService passwordResetRequestService;

    @PostMapping("/search")
    @Operation(
            summary = "Wyszukiwanie zgłoszeń resetu hasła",
            description = "Zwraca stronicowaną listę zgłoszeń resetu hasła wraz ze statusem, użytkownikiem, datami obsługi tokenu oraz metadanymi zgłoszenia."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono stronicowaną listę zgłoszeń resetu hasła",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResult.class)))
    @ApiResponse(responseCode = "403", description = "Użytkownik nie ma uprawnień do przeglądania zgłoszeń resetu hasła")
    public PageResult<PasswordResetRequestListDto> findPasswordResetRequests(
            @RequestBody(required = false) PasswordResetRequestListQuery query
    ) {
        return passwordResetRequestService.findPasswordResetRequests(query);
    }
}
