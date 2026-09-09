package com.poc.shared.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain Java SE bean validation (Validation.buildDefaultValidatorFactory()).
 * Limitation: this does NOT run inside Quarkus ArC, so it cannot catch CDI
 * misconfiguration of custom ConstraintValidators (e.g. HV000030 surfacing
 * as HTTP 500). Real regression coverage lives in the backend module
 * (PaymentResourceValidationMatrixTest via POST /payments).
 */
class PaymentRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setup() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private PaymentRequestDTO createValidRequest() {
        return new PaymentRequestDTO(
            new BigDecimal("100.00"),
            "USD",
            "customer-123",
            "CREDIT_CARD",
            "US",
            null
        );
    }

    @Test
    @DisplayName("Valid payment request should have no violations")
    void testValidRequest() {
        PaymentRequestDTO request = createValidRequest();
        Set<ConstraintViolation<PaymentRequestDTO>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Valid request should have no violations");
    }

    @Test
    @DisplayName("Invalid currency should have violation")
    void testInvalidCurrency() {
        PaymentRequestDTO request = new PaymentRequestDTO(
            new BigDecimal("100.00"),
            "INVALID",
            "customer-123",
            "CREDIT_CARD",
            "US",
            null
        );
        Set<ConstraintViolation<PaymentRequestDTO>> violations = validator.validate(request);
        assertFalse(violations.isEmpty(), "Invalid currency should have violations");
    }

    @Test
    @DisplayName("Valid MXN currency should pass")
    void testValidMxnCurrency() {
        PaymentRequestDTO request = new PaymentRequestDTO(
            new BigDecimal("100.00"),
            "MXN",
            "customer-123",
            "CREDIT_CARD",
            "MX",
            null
        );
        Set<ConstraintViolation<PaymentRequestDTO>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "MXN should be valid");
    }

    @Test
    @DisplayName("Payment method validation is handled at resource layer, not bean validation")
    void testPaymentMethodNotValidatedByBeanValidation() {
        PaymentRequestDTO request = new PaymentRequestDTO(
            new BigDecimal("100.00"),
            "USD",
            "customer-123",
            "BITCOIN",
            "US",
            null
        );
        Set<ConstraintViolation<PaymentRequestDTO>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Payment method is validated at resource layer, not via bean validation");
    }

    @Test
    @DisplayName("Amount below minimum should have violation")
    void testAmountBelowMinimum() {
        PaymentRequestDTO request = new PaymentRequestDTO(
            new BigDecimal("0.001"),
            "USD",
            "customer-123",
            "CREDIT_CARD",
            "US",
            null
        );
        Set<ConstraintViolation<PaymentRequestDTO>> violations = validator.validate(request);
        assertFalse(violations.isEmpty(), "Amount below 0.01 should have violations");
    }
}
