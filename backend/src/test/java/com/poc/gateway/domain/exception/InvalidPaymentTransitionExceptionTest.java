package com.poc.gateway.domain.exception;

import com.poc.gateway.domain.model.PaymentStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InvalidPaymentTransitionExceptionTest {

    @Test
    void constructor_setsFromAndTo() {
        InvalidPaymentTransitionException ex = new InvalidPaymentTransitionException(
            PaymentStatus.PENDING, PaymentStatus.APPROVED
        );
        assertEquals(PaymentStatus.PENDING, ex.getFrom());
        assertEquals(PaymentStatus.APPROVED, ex.getTo());
    }

    @Test
    void constructor_generatesCorrectMessage() {
        InvalidPaymentTransitionException ex = new InvalidPaymentTransitionException(
            PaymentStatus.PROCESSING, PaymentStatus.PENDING
        );
        assertEquals("Invalid payment transition: PROCESSING -> PENDING", ex.getMessage());
    }

    @Test
    void extendsDomainException() {
        InvalidPaymentTransitionException ex = new InvalidPaymentTransitionException(
            PaymentStatus.PENDING, PaymentStatus.APPROVED
        );
        assertInstanceOf(DomainException.class, ex);
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void gettersReturnCorrectValues() {
        InvalidPaymentTransitionException ex = new InvalidPaymentTransitionException(
            PaymentStatus.APPROVED, PaymentStatus.REJECTED
        );
        assertEquals(PaymentStatus.APPROVED, ex.getFrom());
        assertEquals(PaymentStatus.REJECTED, ex.getTo());
        assertTrue(ex.getMessage().contains("APPROVED"));
        assertTrue(ex.getMessage().contains("REJECTED"));
    }
}
