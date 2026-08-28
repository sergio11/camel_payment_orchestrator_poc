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

class PaymentRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setup() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private PaymentRequest createValidRequest() {
        return new PaymentRequest(
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
        PaymentRequest request = createValidRequest();
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Valid request should have no violations");
    }

    @Test
    @DisplayName("Invalid currency should have violation")
    void testInvalidCurrency() {
        PaymentRequest request = new PaymentRequest(
            new BigDecimal("100.00"),
            "INVALID",
            "customer-123",
            "CREDIT_CARD",
            "US",
            null
        );
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty(), "Invalid currency should have violations");
    }

    @Test
    @DisplayName("Valid MXN currency should pass")
    void testValidMxnCurrency() {
        PaymentRequest request = new PaymentRequest(
            new BigDecimal("100.00"),
            "MXN",
            "customer-123",
            "CREDIT_CARD",
            "MX",
            null
        );
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "MXN should be valid");
    }

    @Test
    @DisplayName("Invalid payment method should have violation")
    void testInvalidPaymentMethod() {
        PaymentRequest request = new PaymentRequest(
            new BigDecimal("100.00"),
            "USD",
            "customer-123",
            "BITCOIN",
            "US",
            null
        );
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty(), "Invalid payment method should have violations");
    }

    @Test
    @DisplayName("Amount below minimum should have violation")
    void testAmountBelowMinimum() {
        PaymentRequest request = new PaymentRequest(
            new BigDecimal("0.001"),
            "USD",
            "customer-123",
            "CREDIT_CARD",
            "US",
            null
        );
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty(), "Amount below 0.01 should have violations");
    }
}
