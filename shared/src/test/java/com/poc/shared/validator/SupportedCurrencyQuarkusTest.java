package com.poc.shared.validator;

import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.shared.dto.PaymentRequestDTO;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.validation.ConstraintViolation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bean-level coverage for {@code @SupportedCurrency} running inside Quarkus ArC,
 * so the {@link Validator} is built by Quarkus (ArcConstraintValidatorFactory).
 *
 * <p>Authoritative regression coverage for the HV000030 failure mode
 * (custom {@code ConstraintValidator} mis-registered as a CDI bean, surfacing
 * as HTTP 500 on POST /payments) lives in the backend module:
 * {@code PaymentResourceValidationMatrixTest}. That test asserts valid
 * currencies return 201/200 and never 500 through the real REST endpoint.
 */
@QuarkusTest
class SupportedCurrencyQuarkusTest {

    @Inject
    Validator validator;

    private static PaymentRequestDTO requestWith(String currency) {
        return new PaymentRequestDTO(
            new BigDecimal("100.00"),
            currency,
            "customer-123",
            "CREDIT_CARD",
            "US",
            PaymentMetadataDTO.empty()
        );
    }

    @Test
    @DisplayName("Valid USD currency has no violations via Arc-managed Validator")
    void validUsdHasNoViolations() {
        Set<ConstraintViolation<PaymentRequestDTO>> violations = validator.validate(requestWith("USD"));
        assertTrue(violations.isEmpty(), "USD should be valid: " + describe(violations));
    }

    @Test
    @DisplayName("Valid MXN currency has no violations via Arc-managed Validator")
    void validMxnHasNoViolations() {
        Set<ConstraintViolation<PaymentRequestDTO>> violations = validator.validate(requestWith("MXN"));
        assertTrue(violations.isEmpty(), "MXN should be valid: " + describe(violations));
    }

    @Test
    @DisplayName("Invalid BTC currency has violations via Arc-managed Validator")
    void invalidBtcHasViolations() {
        Set<ConstraintViolation<PaymentRequestDTO>> violations = validator.validate(requestWith("BTC"));
        assertFalse(violations.isEmpty(), "BTC should be rejected");
    }

    private static String describe(Set<ConstraintViolation<PaymentRequestDTO>> violations) {
        return violations.stream()
            .map(v -> v.getPropertyPath() + "=" + v.getMessage())
            .collect(Collectors.joining(", "));
    }
}
