package com.osatum.wbn.web.auth.controller;

import com.osatum.wbn.application.auth.dto.LoginSessionViewDto;
import com.osatum.wbn.application.auth.query.LoginSessionListQuery;
import com.osatum.wbn.application.auth.service.LoginSessionService;
import com.osatum.wbn.application.shared.list.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AuthSessions", description = "Przegląd sesji logowania użytkowników i instytucji")
@RestController
@RequestMapping("/api/auth-sessions")
@RequiredArgsConstructor
@Slf4j
public class AuthSessionController {

    private final LoginSessionService loginSessionService;

    @PostMapping("/search")
    @Operation(
            summary = "Wyszukiwanie sesji logowania",
            description = "Zwraca stronicowaną listę sesji logowania użytkowników i instytucji zgodnie z kryteriami wyszukiwania."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono stronicowaną listę sesji logowania",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResult.class)))
    @ApiResponse(responseCode = "403", description = "Użytkownik nie ma uprawnień do przeglądania sesji logowania")
    public PageResult<LoginSessionViewDto> findLoginSessions(@RequestBody(required = false) LoginSessionListQuery query) {
        return loginSessionService.getLoginSessions(query);
    }

    @PostMapping("/search-for-user/{id}")
    @Operation(
            summary = "Wyszukiwanie sesji logowania użytkownika",
            description = "Zwraca stronicowaną listę sesji logowania przypisanych do wskazanego użytkownika."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono stronicowaną listę sesji logowania użytkownika",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResult.class)))
    @ApiResponse(responseCode = "403", description = "Użytkownik nie ma uprawnień do przeglądania sesji logowania")
    public PageResult<LoginSessionViewDto> findLoginSessionsForUser(@PathVariable(name = "id") Long id,  @RequestBody(required = false) LoginSessionListQuery query) {
        return loginSessionService.getLoginSessionsForUser(id, query);
    }

    @PostMapping("/search-for-institution/{id}")
    @Operation(
            summary = "Wyszukiwanie sesji logowania instytucji",
            description = "Zwraca stronicowaną listę sesji logowania powiązanych ze wskazaną instytucją."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono stronicowaną listę sesji logowania instytucji",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResult.class)))
    @ApiResponse(responseCode = "403", description = "Użytkownik nie ma uprawnień do przeglądania sesji logowania")
    public PageResult<LoginSessionViewDto> findLoginSessionsForInstitution(@PathVariable(name = "id") Long id,  @RequestBody(required = false) LoginSessionListQuery query) {
        return loginSessionService.getLoginSessionsForInstitution(id, query);
    }


    @PostMapping("/active/search")
    @Operation(
            summary = "Wyszukiwanie aktywnych sesji logowania",
            description = "Oznacza wygasłe sesje jako zakończone i zwraca stronicowaną listę aktualnie aktywnych sesji logowania."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono stronicowaną listę aktywnych sesji logowania",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResult.class)))
    @ApiResponse(responseCode = "403", description = "Użytkownik nie ma uprawnień do przeglądania aktywnych sesji logowania")
    public PageResult<LoginSessionViewDto> findActiveSessions(@RequestBody(required = false) LoginSessionListQuery query) {
        return loginSessionService.getActiveSessions(query);
    }
}
