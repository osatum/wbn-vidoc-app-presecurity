package com.osatum.wbn.web.auth.controller;

import com.osatum.wbn.application.auth.dto.AuthResponse;
import com.osatum.wbn.application.auth.dto.AutorizeIpRequest;
import com.osatum.wbn.application.auth.dto.LoginRequest;
import com.osatum.wbn.application.auth.dto.SelectInstitutionAndRoleRequest;
import com.osatum.wbn.application.auth.service.AuthService;
import com.osatum.wbn.web.auth.cookie.AuthCookieWriter;
import com.osatum.wbn.web.error.ApiErrorResponse;
import com.osatum.wbn.web.shared.request.ClientRequestMetadataResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AuthApplication", description = "Uwierzytelnianie użytkowników i instytucji")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ClientRequestMetadataResolver requestMetadataResolver;
    private final AuthCookieWriter cookieWriter;

    @PostMapping("/login")
    @Operation(
            summary = "Logowanie użytkownika",
            description = "Weryfikuje dane logowania użytkownika. Jeżeli użytkownik ma jedno aktywne powiązanie z instytucją, zwraca token dostępu i ustawia cookie z refresh tokenem. Jeżeli wymagany jest wybór instytucji lub roli, zwraca odpowiedni status i ustawia cookie preautoryzacyjne."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono wynik logowania",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponse(responseCode = "400", description = "Błąd walidacji formularza",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Nieprawidłowe dane logowania",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "500", description = "Błąd podczas logowania")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        var result = authService.login(req, requestMetadataResolver.resolve(request));
        return cookieWriter.write(result, response).response();
    }

    @PostMapping("/select-institution")
    @Operation(
            summary = "Wybór instytucji lub roli",
            description = "Kończy logowanie użytkownika po wcześniejszej preautoryzacji albo zmienia aktywny kontekst zalogowanego użytkownika. Po poprawnym wyborze zwraca token dostępu i ustawia cookie z refresh tokenem."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono wynik wyboru instytucji lub roli",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponse(responseCode = "400", description = "Nieprawidłowy wybór instytucji, roli lub powiązania",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Brak prawidłowego tokenu preautoryzacyjnego lub aktywnego tokenu dostępu użytkownika")
    public AuthResponse selectInstitution(@Valid @RequestBody SelectInstitutionAndRoleRequest req,
                                          @CookieValue(name = AuthCookieWriter.PRE_AUTH, required = false) String authCookie,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {
        var result = authService.selectInstitution(req, authCookie, requestMetadataResolver.resolve(request));
        return cookieWriter.write(result, response).response();
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Odświeżenie tokenu dostępu",
            description = "Weryfikuje cookie z refresh tokenem, rotuje refresh token, aktualizuje aktywność sesji logowania i zwraca nowy token dostępu."
    )
    @ApiResponse(responseCode = "200", description = "Token dostępu został odświeżony",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponse(responseCode = "400", description = "Brak, nieprawidłowy lub niedozwolony refresh token",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public AuthResponse refresh(
            @CookieValue(name = AuthCookieWriter.REFRESH_TOKEN, required = false) String refreshCookie,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var result = authService.refresh(refreshCookie, requestMetadataResolver.resolve(request));
        return cookieWriter.write(result, response).response();
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Wylogowanie",
            description = "Unieważnia refresh token z cookie, kończy powiązaną sesję logowania jako wylogowaną i usuwa cookie refresh tokenu."
    )
    @ApiResponse(responseCode = "200", description = "Użytkownik został wylogowany")
    public void logout(
            @CookieValue(name = AuthCookieWriter.REFRESH_TOKEN, required = false) String refreshCookie,
            HttpServletResponse response
    ) {
        authService.logout(refreshCookie);
        cookieWriter.clearAuthenticationCookies(response);
    }

    @PostMapping("/autorizeIp")
    @Operation(
            summary = "Autoryzacja instytucji po adresie IP",
            description = "Sprawdza, czy adres IP klienta pasuje do reguł wskazanej aktywnej instytucji bez wymaganego logowania. Po poprawnej autoryzacji tworzy sesję instytucji z rolą podglądu, ustawia refresh token w cookie i zwraca token dostępu."
    )
    @ApiResponse(responseCode = "200", description = "Instytucja została autoryzowana na podstawie adresu IP",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponse(responseCode = "400", description = "Nieprawidłowe żądanie, niedozwolony adres IP lub instytucja wymaga logowania",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public AuthResponse autorizeIp(
            @Valid @RequestBody AutorizeIpRequest req,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var result = authService.autorizeIp(req, requestMetadataResolver.resolve(request));
        return cookieWriter.write(result, response).response();
    }
}
