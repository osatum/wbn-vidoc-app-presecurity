package com.osatum.wbn.web.user.management.controller;

import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.application.user.dto.UserCreateResultDto;
import com.osatum.wbn.application.user.dto.UserCreateStatus;
import com.osatum.wbn.application.user.dto.UserListViewDto;
import com.osatum.wbn.application.user.dto.UserStateChangeResultDto;
import com.osatum.wbn.application.user.dto.UserViewDto;
import com.osatum.wbn.application.user.query.UserListQuery;
import com.osatum.wbn.application.user.service.UserService;
import com.osatum.wbn.web.error.ApiErrorResponse;
import com.osatum.wbn.web.shared.form.EntityStateChangeForm;
import com.osatum.wbn.web.user.management.form.UserBasicDataForm;
import com.osatum.wbn.web.user.management.form.UserCreateForm;
import com.osatum.wbn.web.user.management.form.UserPasswordForm;
import com.osatum.wbn.web.user.management.mapper.UserBasicDataFormMapper;
import com.osatum.wbn.web.user.management.mapper.UserCreateFormMapper;
import com.osatum.wbn.web.user.management.mapper.UserPasswordFormMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "Zarządzanie użytkownikami systemu")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    private final UserService userService;
    private final UserCreateFormMapper userCreateFormMapper;
    private final UserBasicDataFormMapper userBasicDataFormMapper;
    private final UserPasswordFormMapper userPasswordFormMapper;

    @PostMapping("/search")
    @Operation(
            summary = "Wyszukiwanie użytkowników",
            description = "Zwraca stronicowaną listę użytkowników zgodnie z kryteriami wyszukiwania."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono stronicowaną listę użytkowników",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResult.class)))
    @ApiResponse(responseCode = "403", description = "Użytkownik nie ma uprawnień do zarządzania użytkownikami")
    public PageResult<UserListViewDto> findUsers(@RequestBody UserListQuery query) {
        return userService.findUsers(query);
    }

    @GetMapping("/search/default-query")
    @Operation(
            summary = "Domyslne kryteria wyszukiwania uzytkownikow",
            description = "Zwraca domyslny stan kryteriow wyszukiwania uzytkownikow."
    )
    @ApiResponse(responseCode = "200", description = "Zwrocono domyslne kryteria wyszukiwania uzytkownikow",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserListQuery.class)))
    @ApiResponse(responseCode = "403", description = "Uzytkownik nie ma uprawnien do zarzadzania uzytkownikami")
    public UserListQuery getDefaultUserListQuery() {
        return userService.getDefaultUserListQuery();
    }

    @PostMapping("/search/{id}")
    @Operation(
            summary = "Wyszukiwanie użytkowników instytucji",
            description = "Zwraca stronicowaną listę użytkowników przypisanych do wskazanej instytucji zgodnie z kryteriami wyszukiwania."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono stronicowaną listę użytkowników instytucji",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResult.class)))
    @ApiResponse(responseCode = "403", description = "Użytkownik nie ma uprawnień do zarządzania użytkownikami")
    public PageResult<UserListViewDto> findUsersByInstitution(@PathVariable(name = "id") Long id, @RequestBody UserListQuery query) {
        return userService.findUsersByInstitution(id, query);
    }

    @GetMapping("/institution/{institutionId}/create-form")
    @Operation(
            summary = "Przygotowanie formularza tworzenia użytkownika",
            description = "Zwraca dane domyślne potrzebne do utworzenia użytkownika w ramach wskazanej instytucji."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono formularz tworzenia użytkownika",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserCreateForm.class)))
    @ApiResponse(responseCode = "404", description = "Nie znaleziono instytucji",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserCreateForm prepareCreateUserForm(@PathVariable(name = "institutionId") Long institutionId) {
        return userCreateFormMapper.toForm(userService.prepareCreateUserForm(institutionId));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Pobranie szczegółów użytkownika",
            description = "Zwraca szczegółowe dane użytkownika o podanym identyfikatorze."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono szczegóły użytkownika",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserViewDto.class)))
    @ApiResponse(responseCode = "500", description = "Błąd podczas pobierania użytkownika")
    public ResponseEntity<UserViewDto> findUser(@PathVariable(name = "id") Long id) {
        try {
            UserViewDto dto = userService.findUser(id);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("Error find user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}/activation-form")
    @Operation(
            summary = "Przygotowanie formularza aktywacji użytkownika",
            description = "Zwraca informacje potrzebne do aktywacji użytkownika, w tym ewentualną listę powiązań z instytucjami wymagających wyboru."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono formularz aktywacji użytkownika",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserStateChangeResultDto.class)))
    @ApiResponse(responseCode = "404", description = "Nie znaleziono użytkownika",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserStateChangeResultDto prepareUserActivationForm(@PathVariable(name = "id") Long id) {
        return userService.prepareActivationForm(id);
    }

    @PostMapping("/institution/{institutionId}")
    @Operation(
            summary = "Utworzenie użytkownika w instytucji",
            description = "Tworzy nowe konto użytkownika w ramach wskazanej instytucji albo, zależnie od danych formularza, obsługuje przypadek istniejącego użytkownika."
    )
    @ApiResponse(responseCode = "201", description = "Użytkownik został utworzony",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserCreateResultDto.class)))
    @ApiResponse(responseCode = "200", description = "Obsłużono istniejącego użytkownika lub jego powiązanie z instytucją",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserCreateResultDto.class)))
    @ApiResponse(responseCode = "400", description = "Błąd walidacji formularza",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Nie znaleziono instytucji",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Użytkownik lub powiązanie już istnieje",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<UserCreateResultDto> createUser(
            @PathVariable(name="institutionId") Long institutionId,
            @Valid @RequestBody UserCreateForm form
    ) {
        UserCreateResultDto result = userService.createUser(userCreateFormMapper.toDto(institutionId, form));
        HttpStatus status = result.getStatus() == UserCreateStatus.CREATED
                ? HttpStatus.CREATED
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }

    @PutMapping("/{id}/basic-data")
    @Operation(
            summary = "Aktualizacja podstawowych danych użytkownika",
            description = "Aktualizuje podstawowe dane użytkownika o podanym identyfikatorze."
    )
    @ApiResponse(responseCode = "200", description = "Podstawowe dane użytkownika zostały zaktualizowane",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserViewDto.class)))
    @ApiResponse(responseCode = "400", description = "Błąd walidacji formularza",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Nie znaleziono użytkownika",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserViewDto updateUserBasicData(@PathVariable(name = "id") Long id, @Valid @RequestBody UserBasicDataForm form) {
        return userService.updateBasicData(id, userBasicDataFormMapper.toDto(form));
    }

    @PutMapping("/{id}/password")
    @Operation(
            summary = "Zmiana hasła użytkownika przez administratora",
            description = "Ustawia nowe hasło użytkownika o podanym identyfikatorze po walidacji formularza hasła."
    )
    @ApiResponse(responseCode = "200", description = "Hasło użytkownika zostało zaktualizowane",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserViewDto.class)))
    @ApiResponse(responseCode = "400", description = "Błąd walidacji formularza lub niezgodne hasła",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Nie znaleziono użytkownika",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserViewDto updateUserPassword(@PathVariable(name = "id") Long id, @Valid @RequestBody UserPasswordForm form) {
        return userService.updatePassword(id, userPasswordFormMapper.toDto(form));
    }

    @PatchMapping("/{id}/state")
    @Operation(
            summary = "Zmiana stanu użytkownika",
            description = "Zmienia stan użytkownika. Przy aktywacji może wymagać wskazania powiązań z instytucjami, które mają zostać aktywowane razem z kontem."
    )
    @ApiResponse(responseCode = "200", description = "Stan użytkownika został zmieniony albo zwrócono wymagane dane do wyboru powiązań",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserStateChangeResultDto.class)))
    @ApiResponse(responseCode = "400", description = "Błąd walidacji formularza",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Nie znaleziono użytkownika",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserStateChangeResultDto changeUserState(@PathVariable(name = "id") Long id, @Valid @RequestBody EntityStateChangeForm form) {
        return userService.changeState(id, form.getState(), form.getMembershipIds());
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Usunięcie użytkownika zgodnie z RODO",
            description = "Usuwa konto użytkownika i jego powiązania z instytucjami oraz anonimizuje powiązane logi i zgłoszenia."
    )
    @ApiResponse(responseCode = "204", description = "Konto użytkownika zostało usunięte, a powiązane dane zanonimizowane")
    @ApiResponse(responseCode = "404", description = "Nie znaleziono użytkownika",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<Void> deleteUser(@PathVariable(name = "id") Long id) {
        userService.deleteForGdpr(id);
        return ResponseEntity.noContent().build();
    }
}
