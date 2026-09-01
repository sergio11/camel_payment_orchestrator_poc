package com.poc.gateway.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentNotFoundExceptionTest {

    @Test
    @DisplayName("constructor stores paymentId")
    void constructor_storesPaymentId() {
        PaymentNotFoundException ex = new PaymentNotFoundException("pay-123");

        assertEquals("pay-123", ex.getPaymentId());
    }

    @Test
    @DisplayName("getPaymentId returns the ID passed to constructor")
    void getPaymentId_returnsConstructorValue() {
        String expected = "abc-def-456";
        PaymentNotFoundException ex = new PaymentNotFoundException(expected);

        assertEquals(expected, ex.getPaymentId());
    }

    @Test
    @DisplayName("message contains paymentId")
    void message_containsPaymentId() {
        PaymentNotFoundException ex = new PaymentNotFoundException("pay-999");

        assertTrue(ex.getMessage().contains("pay-999"));
    }

    @Test
    @DisplayName("message format matches expected pattern")
    void message_formatMatchesExpected() {
        PaymentNotFoundException ex = new PaymentNotFoundException("pay-123");

        assertEquals("Payment not found: pay-123", ex.getMessage());
    }

    @Test
    @DisplayName("exception is a RuntimeException")
    void exception_isRuntimeException() {
        PaymentNotFoundException ex = new PaymentNotFoundException("pay-1");

        assertInstanceOf(RuntimeException.class, ex);
    }
}
