package com.osatum.wbn.web.user.datadeletion.controller;

import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.application.security.UserActor;
import com.osatum.wbn.application.user.dto.UserDataDeletionRequestAcceptedDto;
import com.osatum.wbn.application.user.dto.UserDataDeletionTokenStatusDto;
import com.osatum.wbn.application.user.service.UserDataDeletionService;
import com.osatum.wbn.web.error.ApiErrorResponse;
import com.osatum.wbn.web.shared.request.ClientRequestMetadataResolver;
import com.osatum.wbn.web.user.datadeletion.form.UserDataDeletionRequestForm;
import com.osatum.wbn.web.user.datadeletion.mapper.UserDataDeletionRequestFormMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "UserDataDeletion", description = "Obsługa zgłoszeń usunięcia danych użytkownika")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserDataDeletionController {
    private final UserDataDeletionService userDataDeletionService;
    private final UserDataDeletionRequestFormMapper userDataDeletionRequestFormMapper;
    private final ClientRequestMetadataResolver requestMetadataResolver;
    private final CurrentActorProvider currentActorProvider;

    @PostMapping("/profile/data-deletion/request")
    @Operation(
            summary = "Utworzenie zgłoszenia usunięcia danych użytkownika",
            description = "Tworzy zgłoszenie usunięcia danych osobowych dla aktualnie zalogowanego użytkownika jednostki naukowej i zleca wysłanie wiadomości e-mail z ograniczonym czasowo tokenem potwierdzającym."
    )
    @ApiResponse(responseCode = "200", description = "Zgłoszenie usunięcia danych użytkownika zostało przyjęte",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDataDeletionRequestAcceptedDto.class)))
    @ApiResponse(responseCode = "400", description = "Błąd walidacji, niezgodność konta lub przekroczony limit zgłoszeń",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Zalogowany użytkownik nie ma uprawnień do zgłoszenia usunięcia danych",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserDataDeletionRequestAcceptedDto requestUserDataDeletion(
            @Valid @RequestBody UserDataDeletionRequestForm form,
            HttpServletRequest request
    ) {
        UserActor actor = requireUserActor();
        var requestMetadata = requestMetadataResolver.resolve(request);
        return userDataDeletionService.requestDeletion(
                userDataDeletionRequestFormMapper.toDto(form, actor),
                requestMetadata.ipAddress(),
                requestMetadata.userAgent()
        );
    }

    @PostMapping("/user-data-deletion/verify/{token}")
    @Operation(
            summary = "Weryfikacja tokenu usunięcia danych użytkownika",
            description = "Zwraca status tokenu usunięcia danych użytkownika i zapisuje metadane kliknięcia linku, jeżeli token istnieje."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono status tokenu usunięcia danych użytkownika",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDataDeletionTokenStatusDto.class)))
    public UserDataDeletionTokenStatusDto verifyUserDataDeletionToken(
            @PathVariable(name = "token") String token,
            HttpServletRequest request
    ) {
        var requestMetadata = requestMetadataResolver.resolve(request);
        return userDataDeletionService.verifyToken(
                token,
                requestMetadata.ipAddress(),
                requestMetadata.userAgent()
        );
    }

    @PostMapping("/user-data-deletion/confirm/{token}")
    @Operation(
            summary = "Potwierdzenie usunięcia danych użytkownika",
            description = "Potwierdza ważny token usunięcia danych użytkownika, usuwa konto użytkownika i powiązania z instytucją oraz anonimizuje powiązane logi i zgłoszenia."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono status potwierdzenia usunięcia danych użytkownika",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDataDeletionTokenStatusDto.class)))
    public UserDataDeletionTokenStatusDto confirmUserDataDeletion(
            @PathVariable(name = "token") String token,
            HttpServletRequest request
    ) {
        var requestMetadata = requestMetadataResolver.resolve(request);
        return userDataDeletionService.confirmDeletion(
                token,
                requestMetadata.ipAddress(),
                requestMetadata.userAgent()
        );
    }

    private UserActor requireUserActor() {
        if (currentActorProvider.currentActor() instanceof UserActor userActor) {
            return userActor;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User account is required.");
    }
}
