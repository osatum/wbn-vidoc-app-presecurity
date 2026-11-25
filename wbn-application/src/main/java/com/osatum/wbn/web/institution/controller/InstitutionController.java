package com.osatum.wbn.web.institution.controller;

import com.osatum.wbn.application.institution.dto.InstitutionListViewDto;
import com.osatum.wbn.application.institution.dto.InstitutionDeletionConfirmationDto;
import com.osatum.wbn.application.institution.dto.InstitutionMembershipListDto;
import com.osatum.wbn.application.institution.dto.InstitutionMembershipStateChangeResultDto;
import com.osatum.wbn.application.institution.dto.InstitutionStateChangeResultDto;
import com.osatum.wbn.application.institution.dto.InstitutionViewDto;
import com.osatum.wbn.application.institution.query.InstitutionMembershipListQuery;
import com.osatum.wbn.application.institution.query.InstitutionListQuery;
import com.osatum.wbn.application.institution.service.InstitutionDeletionService;
import com.osatum.wbn.application.institution.service.InstitutionService;
import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.web.error.ApiErrorResponse;
import com.osatum.wbn.web.institution.form.InstitutionCreateForm;
import com.osatum.wbn.web.institution.form.InstitutionDeletionConfirmationForm;
import com.osatum.wbn.web.institution.mapper.InstitutionCreateFormMapper;
import com.osatum.wbn.web.shared.form.EntityStateChangeForm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Institution", description = "Zarządzanie instytucjami, dostępami i powiązaniami użytkowników")
@RestController
@RequestMapping("/api/institution")
@RequiredArgsConstructor
public class InstitutionController {

    private final InstitutionService institutionService;
    private final InstitutionDeletionService institutionDeletionService;
    private final InstitutionCreateFormMapper formMapper;

    @PostMapping("/search-institution")
    @Operation(
            summary = "Wyszukiwanie instytucji",
            description = "Zwraca stronicowaną listę instytucji zgodnie z kryteriami wyszukiwania, sortowania i paginacji."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono stronicowaną listę instytucji",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResult.class)))
    @ApiResponse(responseCode = "403", description = "Użytkownik nie ma uprawnień do zarządzania instytucjami")
    public PageResult<InstitutionListViewDto> searchInstitutions(@RequestBody InstitutionListQuery query) {
        return institutionService.getInstitutions(query);
    }

    @GetMapping("/search-institution/default-query")
    @Operation(
            summary = "Domyslne kryteria wyszukiwania instytucji",
            description = "Zwraca domyslny stan kryteriow wyszukiwania instytucji."
    )
    @ApiResponse(responseCode = "200", description = "Zwrocono domyslne kryteria wyszukiwania instytucji",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = InstitutionListQuery.class)))
    @ApiResponse(responseCode = "403", description = "Uzytkownik nie ma uprawnien do zarzadzania instytucjami")
    public InstitutionListQuery getDefaultInstitutionListQuery() {
        return institutionService.getDefaultInstitutionListQuery();
    }

    @PostMapping("/{id}/memberships/search")
    @Operation(
            summary = "Wyszukiwanie powiązań instytucji",
            description = "Zwraca stronicowaną listę powiązań użytkowników ze wskazaną instytucją."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono stronicowaną listę powiązań instytucji",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResult.class)))
    @ApiResponse(responseCode = "404", description = "Nie znaleziono instytucji",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public PageResult<InstitutionMembershipListDto> searchInstitutionMemberships(
            @PathVariable(name = "id") Long id,
            @RequestBody InstitutionMembershipListQuery query
    ) {
        return institutionService.getInstitutionMemberships(id, query);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Pobranie szczegółów instytucji",
            description = "Zwraca pełne dane instytucji wraz z konfiguracją domen e-mail i reguł adresów IP."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono szczegóły instytucji",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = InstitutionViewDto.class)))
    @ApiResponse(responseCode = "404", description = "Nie znaleziono instytucji",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public InstitutionViewDto findInstitution(@PathVariable(name = "id") Long id) {
        return institutionService.findInstitution(id);
    }

    @GetMapping("/{id}/activation-form")
    @Operation(
            summary = "Przygotowanie formularza aktywacji instytucji",
            description = "Zwraca informacje potrzebne do aktywacji instytucji, w tym powiązania użytkowników wymagające wyboru stanu."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono formularz aktywacji instytucji",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = InstitutionStateChangeResultDto.class)))
    @ApiResponse(responseCode = "404", description = "Nie znaleziono instytucji",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public InstitutionStateChangeResultDto prepareInstitutionActivationForm(@PathVariable(name = "id") Long id) {
        return institutionService.prepareActivationForm(id);
    }

    @PostMapping
    @Operation(
            summary = "Utworzenie instytucji",
            description = "Tworzy nową instytucję wraz z konfiguracją domen e-mail oraz reguł dostępu po adresach IP."
    )
    @ApiResponse(responseCode = "201", description = "Instytucja została utworzona",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = InstitutionViewDto.class)))
    @ApiResponse(responseCode = "400", description = "Błąd walidacji formularza instytucji",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Instytucja, domena e-mail lub reguła IP już istnieje",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<InstitutionViewDto> createInstitution(@Valid @RequestBody InstitutionCreateForm form) {
        return ResponseEntity.status(HttpStatus.CREATED).body(institutionService.createInstitution(formMapper.toDto(form)));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Aktualizacja instytucji",
            description = "Aktualizuje dane instytucji, jej domeny e-mail oraz reguły dostępu po adresach IP."
    )
    @ApiResponse(responseCode = "200", description = "Instytucja została zaktualizowana",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = InstitutionViewDto.class)))
    @ApiResponse(responseCode = "400", description = "Błąd walidacji formularza instytucji",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Nie znaleziono instytucji",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Instytucja, domena e-mail lub reguła IP już istnieje",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public InstitutionViewDto updateInstitution(@PathVariable(name = "id") Long id, @Valid @RequestBody InstitutionCreateForm form) {
        return institutionService.updateInstitution(id, formMapper.toDto(form));
    }

    @PatchMapping("/{id}/state")
    @Operation(
            summary = "Zmiana stanu instytucji",
            description = "Zmienia stan instytucji. Przy aktywacji może wymagać wskazania powiązań użytkowników, które mają zostać aktywowane razem z instytucją."
    )
    @ApiResponse(responseCode = "200", description = "Stan instytucji został zmieniony albo zwrócono dane wymagane do wyboru powiązań",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = InstitutionStateChangeResultDto.class)))
    @ApiResponse(responseCode = "400", description = "Błąd walidacji formularza",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Nie znaleziono instytucji",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public InstitutionStateChangeResultDto changeInstitutionState(@PathVariable(name = "id") Long id, @Valid @RequestBody EntityStateChangeForm form) {
        return institutionService.changeState(id, form.getState(), form.getMembershipIds());
    }

    @PatchMapping("/{id}/memberships/{membershipId}/state")
    @Operation(
            summary = "Zmiana stanu powiązania instytucji",
            description = "Zmienia stan wskazanego powiązania użytkownika z instytucją."
    )
    @ApiResponse(responseCode = "200", description = "Stan powiązania instytucji został zmieniony",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = InstitutionMembershipStateChangeResultDto.class)))
    @ApiResponse(responseCode = "400", description = "Błąd walidacji formularza",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Nie znaleziono instytucji lub powiązania",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public InstitutionMembershipStateChangeResultDto changeInstitutionMembershipState(
            @PathVariable(name = "id") Long id,
            @PathVariable(name = "membershipId") Long membershipId,
            @Valid @RequestBody EntityStateChangeForm form
    ) {
        return institutionService.changeMembershipState(id, membershipId, form.getState());
    }

    @PatchMapping("/{id}/memberships/{membershipId}/confirm")
    @Operation(
            summary = "Potwierdzenie powiązania instytucji",
            description = "Potwierdza wskazane powiązanie użytkownika z instytucją, jeżeli spełnia warunki potwierdzenia."
    )
    @ApiResponse(responseCode = "200", description = "Powiązanie instytucji zostało potwierdzone",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = InstitutionMembershipStateChangeResultDto.class)))
    @ApiResponse(responseCode = "400", description = "Powiązania nie można potwierdzić",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Nie znaleziono instytucji lub powiązania",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public InstitutionMembershipStateChangeResultDto confirmInstitutionMembership(
            @PathVariable(name = "id") Long id,
            @PathVariable(name = "membershipId") Long membershipId
    ) {
        return institutionService.confirmMembership(id, membershipId);
    }

    @PostMapping("/{id}/deletion-confirmations")
    @Operation(
            summary = "Przygotowanie potwierdzenia usunięcia instytucji",
            description = "Tworzy jednorazowy kod wymagany do trwałego usunięcia instytucji."
    )
    @ApiResponse(responseCode = "200", description = "Utworzono jednorazowe potwierdzenie",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = InstitutionDeletionConfirmationDto.class)))
    @ApiResponse(responseCode = "404", description = "Nie znaleziono instytucji",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Instytucji nie można usunąć",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public InstitutionDeletionConfirmationDto createInstitutionDeletionConfirmation(
            @PathVariable(name = "id") Long id
    ) {
        return institutionDeletionService.createConfirmation(id);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Trwałe usunięcie instytucji",
            description = "Usuwa instytucję, jej relacje i dane zależne po poprawnym potwierdzeniu jednorazowym kodem."
    )
    @ApiResponse(responseCode = "204", description = "Instytucja i powiązane dane zostały usunięte")
    @ApiResponse(responseCode = "400", description = "Kod potwierdzający jest nieprawidłowy albo wygasł",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Nie znaleziono instytucji",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Instytucji nie można usunąć albo potwierdzenie zostało już użyte",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<Void> deleteInstitution(
            @PathVariable(name = "id") Long id,
            @Valid @RequestBody InstitutionDeletionConfirmationForm form
    ) {
        institutionDeletionService.deleteInstitution(id, form.getConfirmationId(), form.getConfirmationCode());
        return ResponseEntity.noContent().build();
    }

}
