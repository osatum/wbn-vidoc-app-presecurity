package com.osatum.wbn.web.error;

import com.osatum.wbn.application.shared.exception.ActivationTokenAlreadyUsedException;
import com.osatum.wbn.application.journal.service.ExchangeRatesUnavailableException;
import com.osatum.wbn.application.shared.exception.InstitutionDeletionConfirmationException;
import com.osatum.wbn.application.shared.exception.InstitutionDeletionNotAllowedException;
import com.osatum.wbn.application.shared.exception.ResourceAlreadyExistsException;
import com.osatum.wbn.application.shared.exception.ResourceNotFoundException;
import com.osatum.wbn.application.shared.exception.NavigationAuditReportGenerationException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {
    @ExceptionHandler(ExchangeRatesUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleExchangeRatesUnavailable(RuntimeException exception) {
        log.warn("Search exchange rates unavailable: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiErrorResponse.of("EXCHANGE_RATES_UNAVAILABLE", "Currency conversion is temporarily unavailable"));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of("INVALID_REQUEST", "Invalid request body."));
    }
    @ExceptionHandler(NavigationAuditReportGenerationException.class)
    public ResponseEntity<ApiErrorResponse> handleReportGeneration(NavigationAuditReportGenerationException exception) {
        log.error("Activity report generation failed", exception);
        return ResponseEntity.internalServerError().body(ApiErrorResponse.of("REPORT_GENERATION_FAILED",
                "Could not generate the activity report. Please try again later."));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        return ResponseEntity.badRequest().body(ApiErrorResponse.validation(fieldErrors));
    }

    /**
     * Zachowuje format błędów REST także wtedy, gdy kryteria odrzuci granica aplikacji, nie kontroler.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolations(ConstraintViolationException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                fieldErrors.put(fieldPath(violation.getPropertyPath()), violation.getMessage()));
        return ResponseEntity.badRequest().body(ApiErrorResponse.validation(fieldErrors));
    }

    private String fieldPath(Path path) {
        StringBuilder field = new StringBuilder();
        for (Path.Node node : path) {
            if (node.isInIterable()) {
                field.append('[');
                if (node.getIndex() != null) {
                    field.append(node.getIndex());
                } else if (node.getKey() != null) {
                    field.append(node.getKey());
                }
                field.append(']');
            }
            if (node.getKind() == ElementKind.PROPERTY) {
                if (!field.isEmpty()) {
                    field.append('.');
                }
                field.append(node.getName());
            }
        }
        return field.toString();
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleAlreadyExists(ResourceAlreadyExistsException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "RESOURCE_ALREADY_EXISTS",
                        ex.getMessage(),
                        Map.of(ex.getFieldName(), ex.getMessage()),
                        Instant.now()
                ));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("RESOURCE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of("AUTHENTICATION_FAILED", "Invalid credentials"));
    }

    @ExceptionHandler(ActivationTokenAlreadyUsedException.class)
    public ResponseEntity<ApiErrorResponse> handleActivationTokenAlreadyUsed(ActivationTokenAlreadyUsedException ex) {
        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.of("ACTIVATION_TOKEN_ALREADY_USED", ex.getMessage()));
    }

    @ExceptionHandler(InstitutionDeletionConfirmationException.class)
    public ResponseEntity<ApiErrorResponse> handleInstitutionDeletionConfirmation(
            InstitutionDeletionConfirmationException ex
    ) {
        HttpStatus status = InstitutionDeletionConfirmationException.Reason.USED.equals(ex.getReason())
                ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;

        return ResponseEntity
                .status(status)
                .body(ApiErrorResponse.of(
                        "INSTITUTION_DELETION_CONFIRMATION_" + ex.getReason().name(),
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(InstitutionDeletionNotAllowedException.class)
    public ResponseEntity<ApiErrorResponse> handleInstitutionDeletionNotAllowed(
            InstitutionDeletionNotAllowedException ex
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("INSTITUTION_DELETION_NOT_ALLOWED", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.of("INVALID_REQUEST", ex.getMessage()));
    }
}
