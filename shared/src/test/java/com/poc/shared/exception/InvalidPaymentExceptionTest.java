package com.poc.shared.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InvalidPaymentExceptionTest {

    @Test
    @DisplayName("Constructor should set paymentId and message correctly")
    void testConstructorSetsFields() {
        InvalidPaymentException ex = new InvalidPaymentException("pay-123", "Invalid fields");

        assertEquals("pay-123", ex.getPaymentId());
        assertEquals("Invalid fields", ex.getMessage());
    }

    @Test
    @DisplayName("getPaymentId should return the paymentId")
    void testGetPaymentId() {
        InvalidPaymentException ex = new InvalidPaymentException("pay-456", "Error");

        assertEquals("pay-456", ex.getPaymentId());
    }

    @Test
    @DisplayName("getMessage should return the message")
    void testGetMessage() {
        InvalidPaymentException ex = new InvalidPaymentException("pay-789", "Required fields missing");

        assertEquals("Required fields missing", ex.getMessage());
    }

    @Test
    @DisplayName("Exception should be a RuntimeException")
    void testIsRuntimeException() {
        InvalidPaymentException ex = new InvalidPaymentException("pay-000", "Error");

        assertInstanceOf(RuntimeException.class, ex);
    }
}
