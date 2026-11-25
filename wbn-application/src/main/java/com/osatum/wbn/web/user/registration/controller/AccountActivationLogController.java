package com.osatum.wbn.web.user.registration.controller;

import com.osatum.wbn.application.user.dto.AccountActivationLogListDto;
import com.osatum.wbn.application.user.query.AccountActivationLogListQuery;
import com.osatum.wbn.application.user.service.AccountActivationLogService;
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

@Tag(name = "AccountActivations", description = "Przegląd zdarzeń aktywacji kont")
@RestController
@RequestMapping("/api/account-activations")
@RequiredArgsConstructor
public class AccountActivationLogController {
    private final AccountActivationLogService accountActivationLogService;

    @PostMapping("/search")
    @Operation(
            summary = "Wyszukiwanie zdarzeń aktywacji kont",
            description = "Zwraca stronicowaną listę zdarzeń związanych z aktywacją kont użytkowników zgodnie z kryteriami wyszukiwania."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono stronicowaną listę zdarzeń aktywacji kont",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResult.class)))
    @ApiResponse(responseCode = "403", description = "Użytkownik nie ma uprawnień do przeglądania zdarzeń aktywacji kont")
    public PageResult<AccountActivationLogListDto> findActivationLogs(@RequestBody(required = false) AccountActivationLogListQuery query) {
        return accountActivationLogService.findActivationLogs(query);
    }
}
