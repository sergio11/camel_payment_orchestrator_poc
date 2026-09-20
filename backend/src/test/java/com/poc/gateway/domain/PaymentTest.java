package com.poc.gateway.domain;

import com.poc.gateway.domain.model.PaymentStatus;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PaymentTest {

    @Test
    void create_returnsPaymentWithGeneratedId() {
        Payment p = Payment.create(new BigDecimal("100"), "USD", "c1", "CARD", "US", PaymentMetadata.empty());
        assertNotNull(p.id());
        assertEquals(PaymentStatus.PENDING, p.status());
        assertEquals("USD", p.currency());
        assertEquals("c1", p.customerId());
        assertNotNull(p.createdAt());
        assertNotNull(p.updatedAt());
    }

    @Test
    void create_setsDefaultValues() {
        Payment p = Payment.create(new BigDecimal("50.25"), "EUR", "cust-1", "PIX", "BR", PaymentMetadata.empty());
        assertNull(p.provider());
        assertNull(p.failureReason());
        assertEquals(new BigDecimal("50.25"), p.amount());
        assertEquals("PIX", p.paymentMethod());
        assertEquals("BR", p.country());
        assertEquals(p.createdAt(), p.updatedAt());
    }

    @Test
    void withStatus_returnsNewPaymentWithStatus() {
        Payment p = Payment.create(new BigDecimal("100"), "USD", "c1", "CARD", "US", PaymentMetadata.empty());
        Payment updated = p.withStatus(PaymentStatus.PROCESSING);
        assertEquals(PaymentStatus.PROCESSING, updated.status());
        assertEquals(p.id(), updated.id());
        assertNotNull(updated.updatedAt());
    }

    @Test
    void withStatus_preservesAllOtherFields() {
        Payment p = Payment.create(new BigDecimal("100"), "USD", "c1", "CARD", "US", PaymentMetadata.empty());
        Payment updated = p.withStatus(PaymentStatus.FAILED);
        assertEquals(p.amount(), updated.amount());
        assertEquals(p.currency(), updated.currency());
        assertEquals(p.customerId(), updated.customerId());
        assertEquals(p.paymentMethod(), updated.paymentMethod());
        assertEquals(p.country(), updated.country());
        assertEquals(p.createdAt(), updated.createdAt());
    }

    @Test
    void withStatus_throwsExceptionForInvalidTransition() {
        Payment p = Payment.create(new BigDecimal("100"), "USD", "c1", "CARD", "US", PaymentMetadata.empty());
        com.poc.gateway.domain.exception.InvalidPaymentTransitionException ex = assertThrows(
            com.poc.gateway.domain.exception.InvalidPaymentTransitionException.class,
            () -> p.withStatus(PaymentStatus.APPROVED)
        );
        assertEquals(PaymentStatus.PENDING, ex.getFrom());
        assertEquals(PaymentStatus.APPROVED, ex.getTo());
        assertTrue(ex.getMessage().contains("PENDING"));
        assertTrue(ex.getMessage().contains("APPROVED"));
    }

    @Test
    void withStatus_throwsExceptionForApprovedToPending() {
        Payment p = Payment.create(new BigDecimal("100"), "USD", "c1", "CARD", "US", PaymentMetadata.empty());
        Payment approved = p.withStatus(PaymentStatus.PROCESSING).withStatus(PaymentStatus.APPROVED);
        com.poc.gateway.domain.exception.InvalidPaymentTransitionException ex = assertThrows(
            com.poc.gateway.domain.exception.InvalidPaymentTransitionException.class,
            () -> approved.withStatus(PaymentStatus.PENDING)
        );
        assertEquals(PaymentStatus.APPROVED, ex.getFrom());
        assertEquals(PaymentStatus.PENDING, ex.getTo());
    }

    @Test
    void withStatus_allowsValidTransitionsFromProcessing() {
        Payment p = Payment.create(new BigDecimal("100"), "USD", "c1", "CARD", "US", PaymentMetadata.empty());
        Payment processing = p.withStatus(PaymentStatus.PROCESSING);

        Payment approved = processing.withStatus(PaymentStatus.APPROVED);
        assertEquals(PaymentStatus.APPROVED, approved.status());

        Payment p2 = Payment.create(new BigDecimal("100"), "USD", "c1", "CARD", "US", PaymentMetadata.empty());
        Payment processing2 = p2.withStatus(PaymentStatus.PROCESSING);
        Payment rejected = processing2.withStatus(PaymentStatus.REJECTED);
        assertEquals(PaymentStatus.REJECTED, rejected.status());

        Payment p3 = Payment.create(new BigDecimal("100"), "USD", "c1", "CARD", "US", PaymentMetadata.empty());
        Payment processing3 = p3.withStatus(PaymentStatus.PROCESSING);
        Payment review = processing3.withStatus(PaymentStatus.REVIEW);
        assertEquals(PaymentStatus.REVIEW, review.status());
    }

    @Test
    void withProvider_returnsNewPaymentWithProvider() {
        Payment p = Payment.create(new BigDecimal("100"), "USD", "c1", "CARD", "US", PaymentMetadata.empty());
        Payment withProv = p.withProvider("Stripe");
        assertEquals("Stripe", withProv.provider());
        assertEquals(p.id(), withProv.id());
    }

    @Test
    void withProvider_updatesTimestamp() {
        Payment p = Payment.create(new BigDecimal("100"), "USD", "c1", "CARD", "US", PaymentMetadata.empty());
        Payment withProv = p.withProvider("Stripe");
        assertNotNull(withProv.updatedAt());
    }

    @Test
    void withFailure_returnsNewPaymentWithFailedStatus() {
        Payment p = Payment.create(new BigDecimal("100"), "USD", "c1", "CARD", "US", PaymentMetadata.empty());
        Payment failed = p.withFailure("timeout");
        assertEquals(PaymentStatus.FAILED, failed.status());
        assertEquals("timeout", failed.failureReason());
    }

    @Test
    void withFailure_updatesTimestamp() {
        Payment p = Payment.create(new BigDecimal("100"), "USD", "c1", "CARD", "US", PaymentMetadata.empty());
        Payment failed = p.withFailure("timeout");
        assertNotNull(failed.updatedAt());
    }

    @Test
    void withUpdatedAt_returnsNewPaymentWithTimestamp() {
        Payment p = Payment.create(new BigDecimal("100"), "USD", "c1", "CARD", "US", PaymentMetadata.empty());
        LocalDateTime now = LocalDateTime.now();
        Payment updated = p.withUpdatedAt(now);
        assertEquals(now, updated.updatedAt());
        assertEquals(p.id(), updated.id());
        assertEquals(p.status(), updated.status());
    }

    @Test
    void recordEquality_worksCorrectly() {
        Payment p1 = new Payment(UUID.randomUUID(), new BigDecimal("100"), "USD", "c1", "CARD", "US", PaymentStatus.PENDING, null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now());
        Payment p2 = new Payment(p1.id(), p1.amount(), p1.currency(), p1.customerId(), p1.paymentMethod(), p1.country(), p1.status(), p1.provider(), p1.failureReason(), p1.metadata(), p1.createdAt(), p1.updatedAt());
        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
    }

    @Test
    void recordInequality_differentId() {
        Payment p1 = new Payment(UUID.randomUUID(), new BigDecimal("100"), "USD", "c1", "CARD", "US", PaymentStatus.PENDING, null, null, PaymentMetadata.empty(), LocalDateTime.now(), LocalDateTime.now());
        Payment p2 = new Payment(UUID.randomUUID(), p1.amount(), p1.currency(), p1.customerId(), p1.paymentMethod(), p1.country(), p1.status(), p1.provider(), p1.failureReason(), p1.metadata(), p1.createdAt(), p1.updatedAt());
        assertNotEquals(p1, p2);
    }

    @Test
    void recordToString_containsFields() {
        Payment p = Payment.create(new BigDecimal("100"), "USD", "c1", "CARD", "US", PaymentMetadata.empty());
        String str = p.toString();
        assertTrue(str.contains("USD"));
        assertTrue(str.contains("c1"));
        assertTrue(str.contains("PENDING"));
    }
}
