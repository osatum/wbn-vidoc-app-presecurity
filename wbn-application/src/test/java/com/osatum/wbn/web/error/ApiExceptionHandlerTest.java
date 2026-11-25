package com.osatum.wbn.web.error;

import com.osatum.wbn.application.shared.exception.ActivationTokenAlreadyUsedException;
import com.osatum.wbn.application.journal.query.JournalListQuery;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void returnsDedicatedErrorCodeForAlreadyUsedActivationToken() {
        var response = handler.handleActivationTokenAlreadyUsed(new ActivationTokenAlreadyUsedException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("ACTIVATION_TOKEN_ALREADY_USED");
    }

    @Test
    void returnsAllApplicationViolationsUsingTheExistingValidationErrorFormat() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var query = new JournalListQuery();
            query.setPage(-1);
            query.setPublicationFeeFreeOnly(true);
            query.setPublicationFeeMaxPln(BigDecimal.ZERO);
            var exception = new ConstraintViolationException("Private validation detail", factory.getValidator().validate(query));

            var response = handler.handleConstraintViolations(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
            assertThat(response.getBody().message()).isEqualTo("Request validation failed");
            assertThat(response.getBody().fieldErrors()).containsKeys("page", "feeCriteriaValid");
            assertThat(response.getBody().toString()).doesNotContain("Private validation detail", "ConstraintViolationException");
        }
    }

    @Test
    void keepsTheSameIndexedFieldPathsAsRequestBodyValidation() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var query = new JournalListQuery();
            query.setCountryCodes(List.of("PL", ""));
            query.setLanguageCodes(Collections.singletonList(null));
            query.setRepositoryIds(List.of(BigDecimal.ZERO));
            query.setLicenseCodes(Collections.singletonList(null));
            query.setAccessModels(Collections.singletonList(null));
            query.setFrequencyCodes(Collections.singletonList(null));
            var binding = new BeanPropertyBindingResult(query, "query");
            new SpringValidatorAdapter(validator).validate(query, binding);

            var response = handler.handleConstraintViolations(new ConstraintViolationException(validator.validate(query)));

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().fieldErrors()).containsKeys("countryCodes[1]", "languageCodes[0]", "repositoryIds[0]",
                    "repositoryIdsValid", "licenseCodes[0]", "accessModels[0]", "frequencyCodes[0]");
            assertThat(response.getBody().fieldErrors().keySet()).containsExactlyInAnyOrderElementsOf(
                    binding.getFieldErrors().stream().map(FieldError::getField).collect(Collectors.toSet()));
        }
    }
}
