package com.osatum.wbn.web.configuration.controller;

import com.osatum.wbn.application.configuration.dto.RatingYearsDto;
import com.osatum.wbn.application.configuration.dto.SearchYearsDto;
import com.osatum.wbn.application.configuration.dto.JournalLinksDto;
import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.web.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Udostępnia frontendowi lata ocen i szablony publicznych linków JML z centralnej konfiguracji.
 * Opublikowane lata wyszukiwania są udostępniane niezależnie od lat prezentacji ICV.
 * Odpowiedź z linkiem do wydań zawiera znacznik identyfikatora czasopisma, bez danych użytkownika i sesji.
 * Stan prezentacji benchmarków jest przekazywany osobno w odpowiedzi Karty decyzji.
 */
@Tag(name = "Configuration", description = "Parametry konfiguracyjne udostępniane frontendowi")
@RestController
@RequestMapping("/api/configuration")
@RequiredArgsConstructor
@Slf4j
public class ConfigurationController {

    private final ConfigurationService configurationService;

    @GetMapping("/journalLinks")
    @Operation(summary = "Publiczne linki czasopisma", operationId = "getJournalLinks")
    public JournalLinksDto getJournalLinks() {
        return new JournalLinksDto(configurationService.getJournalIssuesUrlTemplate());
    }

    @GetMapping("/searchYears")
    @Operation(summary = "Opublikowane lata wyszukiwania", operationId = "getSearchYears")
    public SearchYearsDto getSearchYears() {
        return new SearchYearsDto(configurationService.getCurrentRatingYear(), configurationService.getAvailableSearchYears());
    }

    @GetMapping("/ratingYears")
    @Operation(
            summary = "Pobranie lat oceny",
            description = "Zwraca aktualny rok oceny oraz listę lat dostępnych w systemie dla widoków ankiet i ocen czasopism."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono lata oceny",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RatingYearsDto.class)))
    @ApiResponse(responseCode = "400", description = "Błąd walidacji żądania",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Brak lub nieprawidłowe uwierzytelnienie",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<RatingYearsDto> ratingYears() {
        try {
            RatingYearsDto dto = configurationService.getRatingYears();
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.info("Error during loading rating years", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}
