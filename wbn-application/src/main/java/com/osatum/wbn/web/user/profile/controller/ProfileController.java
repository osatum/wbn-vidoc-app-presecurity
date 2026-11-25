package com.osatum.wbn.web.user.profile.controller;

import com.osatum.wbn.application.user.dto.UserProfileResponse;
import com.osatum.wbn.application.user.service.UserProfileService;
import com.osatum.wbn.web.user.profile.form.OwnPasswordChangeForm;
import com.osatum.wbn.web.error.ApiErrorResponse;
import com.osatum.wbn.web.user.management.form.UserBasicDataForm;
import com.osatum.wbn.web.user.management.mapper.UserBasicDataFormMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "UserProfile", description = "Profil aktualnie zalogowanego użytkownika lub instytucji")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProfileController {

    private final UserProfileService userProfileService;
    private final UserBasicDataFormMapper userBasicDataFormMapper;

    @GetMapping("/profile")
    @Operation(
            summary = "Pobranie profilu zalogowanego podmiotu",
            description = "Zwraca dane aktualnie zalogowanego użytkownika wraz z dostępami do instytucji albo dane zalogowanej instytucji."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono profil zalogowanego podmiotu",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserProfileResponse.class)))
    @ApiResponse(responseCode = "401", description = "Brak uwierzytelnienia")
    public UserProfileResponse profile() {
        return userProfileService.getProfile();
    }

    @PutMapping("/profile/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Zmiana własnego hasła",
            description = "Zmienia hasło aktualnie zalogowanego użytkownika po podaniu obecnego hasła oraz powtórzeniu nowego hasła. Po zmianie unieważnia pozostałe aktywne sesje użytkownika."
    )
    @ApiResponse(responseCode = "204", description = "Hasło zostało zmienione")
    @ApiResponse(responseCode = "400", description = "Błąd walidacji formularza lub niezgodne hasła",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Nieprawidłowe obecne hasło",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Operacja wymaga zalogowanego użytkownika, a nie sesji instytucji",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Nie znaleziono konta użytkownika",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public void changeOwnPassword(@Valid @RequestBody OwnPasswordChangeForm form) {
        userProfileService.changeOwnPassword(
                form.getCurrentPassword(),
                form.getPassword(),
                form.getPasswordRepeat()
        );
    }

    @GetMapping("/profile/basic-data/form")
    @Operation(
            summary = "Pobranie formularza zmiany wlasnych danych uzytkownika",
            description = "Zwraca dane formularza zmiany imienia, nazwiska i ORCID aktualnie zalogowanego uzytkownika w kontekscie jednostki badawczej."
    )
    @ApiResponse(responseCode = "200", description = "Zwrocono formularz danych uzytkownika",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserBasicDataForm.class)))
    @ApiResponse(responseCode = "403", description = "Operacja dostepna tylko dla uzytkownika jednostki badawczej",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Nie znaleziono konta uzytkownika",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserBasicDataForm ownBasicDataForm() {
        return userBasicDataFormMapper.toForm(userProfileService.findOwnBasicData());
    }

    @PutMapping("/profile/basic-data")
    @Operation(
            summary = "Zmiana wĹ‚asnych danych uĹĽytkownika",
            description = "Aktualizuje imiÄ™, nazwisko oraz identyfikator ORCID aktualnie zalogowanego uĹĽytkownika w kontekĹ›cie jednostki badawczej."
    )
    @ApiResponse(responseCode = "200", description = "Dane uĹĽytkownika zostaĹ‚y zaktualizowane",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserProfileResponse.class)))
    @ApiResponse(responseCode = "400", description = "BĹ‚Ä…d walidacji formularza",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Operacja dostÄ™pna tylko dla uĹĽytkownika jednostki badawczej",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Nie znaleziono konta uĹĽytkownika",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserProfileResponse updateOwnBasicData(@Valid @RequestBody UserBasicDataForm form) {
        return userProfileService.updateOwnBasicData(userBasicDataFormMapper.toDto(form));
    }

}
