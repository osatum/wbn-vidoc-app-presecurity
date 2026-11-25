package com.osatum.wbn.web.error;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@Schema(description = "Standardowa odpowiedź błędu API")
public record ApiErrorResponse(
        @Schema(description = "Techniczny kod błędu zwracany przez API.", example = "VALIDATION_ERROR")
        String code,
        @Schema(description = "Czytelny opis błędu.", example = "Request validation failed")
        String message,
        @Schema(description = "Mapa błędów walidacji pól formularza. Kluczem jest nazwa pola, a wartością komunikat błędu.")
        Map<String, String> fieldErrors,
        @Schema(description = "Data i czas utworzenia odpowiedzi błędu.")
        Instant timestamp
) {
    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, Map.of(), Instant.now());
    }

    public static ApiErrorResponse validation(Map<String, String> fieldErrors) {
        return new ApiErrorResponse("VALIDATION_ERROR", "Request validation failed", fieldErrors, Instant.now());
    }
}
