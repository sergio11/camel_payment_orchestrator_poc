package com.poc.gateway.domain.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaymentNotFoundExceptionTest {

    @Test
    void constructor_setsPaymentIdAndMessage() {
        PaymentNotFoundException ex = new PaymentNotFoundException("pay-123");
        assertEquals("pay-123", ex.getPaymentId());
        assertTrue(ex.getMessage().contains("pay-123"));
    }

    @Test
    void constructor_messageFormat() {
        PaymentNotFoundException ex = new PaymentNotFoundException("abc-456");
        assertEquals("Payment not found: abc-456", ex.getMessage());
    }

    @Test
    void isDomainException() {
        PaymentNotFoundException ex = new PaymentNotFoundException("x");
        assertInstanceOf(DomainException.class, ex);
    }

    @Test
    void isRuntimeException() {
        PaymentNotFoundException ex = new PaymentNotFoundException("x");
        assertInstanceOf(RuntimeException.class, ex);
    }
}
