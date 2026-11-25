package com.osatum.wbn.web.user.datadeletion.controller;

import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.application.user.dto.UserDataDeletionRequestListDto;
import com.osatum.wbn.application.user.query.UserDataDeletionRequestListQuery;
import com.osatum.wbn.application.user.service.UserDataDeletionRequestQueryService;
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

@Tag(name = "UserDataDeletionRequests", description = "Przegląd zgłoszeń usunięcia danych użytkowników")
@RestController
@RequestMapping("/api/user-data-deletion-requests")
@RequiredArgsConstructor
public class UserDataDeletionRequestController {
    private final UserDataDeletionRequestQueryService userDataDeletionRequestQueryService;

    @PostMapping("/search")
    @Operation(
            summary = "Wyszukiwanie zgłoszeń usunięcia danych użytkowników",
            description = "Zwraca stronicowaną listę zgłoszeń usunięcia danych użytkowników wraz ze statusem zgłoszenia, użytkownikiem, instytucją, cyklem życia tokenu, adresem IP, user-agentem i danymi logu e-mail dla operatorów systemu."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono stronicowaną listę zgłoszeń usunięcia danych użytkowników",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResult.class)))
    @ApiResponse(responseCode = "403", description = "Użytkownik nie ma uprawnień do przeglądania zgłoszeń usunięcia danych")
    public PageResult<UserDataDeletionRequestListDto> findUserDataDeletionRequests(
            @RequestBody(required = false) UserDataDeletionRequestListQuery query
    ) {
        return userDataDeletionRequestQueryService.findUserDataDeletionRequests(query);
    }
}
