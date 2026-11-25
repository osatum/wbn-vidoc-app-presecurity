package com.osatum.wbn.web.user.passwordreset.controller;

import com.osatum.wbn.application.user.dto.PasswordResetRequestAcceptedDto;
import com.osatum.wbn.application.user.dto.PasswordResetTokenStatusDto;
import com.osatum.wbn.application.user.service.PasswordResetService;
import com.osatum.wbn.web.error.ApiErrorResponse;
import com.osatum.wbn.web.shared.request.ClientRequestMetadataResolver;
import com.osatum.wbn.web.user.passwordreset.form.PasswordResetConfirmForm;
import com.osatum.wbn.web.user.passwordreset.form.PasswordResetRequestForm;
import com.osatum.wbn.web.user.passwordreset.mapper.PasswordResetFormMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "PasswordReset", description = "Obsługa resetowania hasła użytkownika")
@RestController
@RequestMapping("/api/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {
    private final PasswordResetService passwordResetService;
    private final PasswordResetFormMapper passwordResetFormMapper;
    private final ClientRequestMetadataResolver requestMetadataResolver;

    @PostMapping("/request")
    @Operation(
            summary = "Zgłoszenie resetu hasła",
            description = "Przyjmuje zgłoszenie resetu hasła dla adresu e-mail, zapisuje metadane zgłoszenia i, jeżeli konto jest aktywne, zleca wysłanie wiadomości e-mail z linkiem resetującym."
    )
    @ApiResponse(responseCode = "200", description = "Zgłoszenie resetu hasła zostało przyjęte",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PasswordResetRequestAcceptedDto.class)))
    @ApiResponse(responseCode = "400", description = "Błąd walidacji formularza",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public PasswordResetRequestAcceptedDto requestPasswordReset(
            @Valid @RequestBody PasswordResetRequestForm form,
            HttpServletRequest request
    ) {
        var requestMetadata = requestMetadataResolver.resolve(request);
        return passwordResetService.requestReset(
                passwordResetFormMapper.toDto(form),
                requestMetadata.ipAddress(),
                requestMetadata.userAgent()
        );
    }

    @PostMapping("/verify/{token}")
    @Operation(
            summary = "Weryfikacja tokenu resetu hasła",
            description = "Zwraca status tokenu resetu hasła i zapisuje metadane kliknięcia linku, jeżeli token istnieje."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono status tokenu resetu hasła",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PasswordResetTokenStatusDto.class)))
    public PasswordResetTokenStatusDto verifyPasswordResetToken(
            @PathVariable(name = "token") String token,
            HttpServletRequest request
    ) {
        var requestMetadata = requestMetadataResolver.resolve(request);
        return passwordResetService.verifyToken(
                token,
                requestMetadata.ipAddress(),
                requestMetadata.userAgent()
        );
    }

    @PostMapping("/confirm/{token}")
    @Operation(
            summary = "Potwierdzenie resetu hasła",
            description = "Zmienia hasło użytkownika dla ważnego tokenu resetu hasła, zapisuje zakończenie zgłoszenia i unieważnia aktywne sesje użytkownika."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono status potwierdzenia resetu hasła",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PasswordResetTokenStatusDto.class)))
    @ApiResponse(responseCode = "400", description = "Błąd walidacji formularza lub niezgodne hasła",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public PasswordResetTokenStatusDto confirmPasswordReset(
            @PathVariable(name = "token") String token,
            @Valid @RequestBody PasswordResetConfirmForm form,
            HttpServletRequest request
    ) {
        var requestMetadata = requestMetadataResolver.resolve(request);
        return passwordResetService.confirmReset(
                token,
                passwordResetFormMapper.toDto(form),
                requestMetadata.ipAddress(),
                requestMetadata.userAgent()
        );
    }
}
