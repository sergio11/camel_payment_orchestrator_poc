package com.poc.gateway.entity;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.entity.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentTest {

    @Test
    @DisplayName("create() generates UUID id and sets PENDING status")
    void create_generatesUuidAndSetsPendingStatus() {
        Payment payment = Payment.create(
            new BigDecimal("50.00"), "EUR", "cust-42",
            "DEBIT_CARD", "DE", Map.of("key", "value")
        );

        assertNotNull(payment.id());
        assertEquals(PaymentStatus.PENDING, payment.status());
        assertEquals(new BigDecimal("50.00"), payment.amount());
        assertEquals("EUR", payment.currency());
        assertEquals("cust-42", payment.customerId());
        assertEquals("DEBIT_CARD", payment.paymentMethod());
        assertEquals("DE", payment.country());
        assertNull(payment.provider());
        assertNull(payment.failureReason());
        assertEquals(Map.of("key", "value"), payment.metadata());
        assertNotNull(payment.createdAt());
        assertNotNull(payment.updatedAt());
    }

    @Test
    @DisplayName("create() generates unique ids")
    void create_generatesUniqueIds() {
        Payment p1 = Payment.create(new BigDecimal("1"), "USD", "c", "m", "US", Map.of());
        Payment p2 = Payment.create(new BigDecimal("1"), "USD", "c", "m", "US", Map.of());

        assertNotEquals(p1.id(), p2.id());
    }

    @Test
    @DisplayName("withStatus returns new payment with updated status")
    void withStatus_returnsNewPaymentWithUpdatedStatus() {
        Payment original = Payment.create(new BigDecimal("10"), "USD", "c", "m", "US", Map.of());
        Payment updated = original.withStatus(PaymentStatus.APPROVED);

        assertEquals(PaymentStatus.APPROVED, updated.status());
        assertEquals(original.id(), updated.id());
        assertEquals(original.amount(), updated.amount());
        assertEquals(original.currency(), updated.currency());
        assertEquals(original.customerId(), updated.customerId());
        assertEquals(original.createdAt(), updated.createdAt());
        assertNotNull(updated.updatedAt());
    }

    @Test
    @DisplayName("withProvider returns new payment with provider set")
    void withProvider_returnsNewPaymentWithProvider() {
        Payment original = Payment.create(new BigDecimal("10"), "USD", "c", "m", "US", Map.of());
        Payment updated = original.withProvider("STRIPE");

        assertEquals("STRIPE", updated.provider());
        assertEquals(original.id(), updated.id());
        assertEquals(original.status(), updated.status());
    }

    @Test
    @DisplayName("withFailure returns new payment with FAILED status and reason")
    void withFailure_returnsNewPaymentWithFailedStatus() {
        Payment original = Payment.create(new BigDecimal("10"), "USD", "c", "m", "US", Map.of());
        Payment updated = original.withFailure("insufficient funds");

        assertEquals(PaymentStatus.FAILED, updated.status());
        assertEquals("insufficient funds", updated.failureReason());
        assertEquals(original.id(), updated.id());
    }

    @Test
    @DisplayName("withUpdatedAt returns new payment with updated timestamp")
    void withUpdatedAt_returnsNewPaymentWithUpdatedTimestamp() {
        Payment original = Payment.create(new BigDecimal("10"), "USD", "c", "m", "US", Map.of());
        LocalDateTime newTime = LocalDateTime.of(2025, 6, 15, 12, 0);
        Payment updated = original.withUpdatedAt(newTime);

        assertEquals(newTime, updated.updatedAt());
        assertEquals(original.id(), updated.id());
        assertEquals(original.status(), updated.status());
        assertEquals(original.createdAt(), updated.createdAt());
    }

    @Test
    @DisplayName("getters return correct values from record")
    void getters_returnCorrectValues() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Payment payment = new Payment(
            id, new BigDecimal("99.99"), "GBP", "cust-99",
            "APPLE_PAY", "UK", PaymentStatus.PROCESSING, "PAYPAL",
            "timeout", Map.of("x", 1), now, now
        );

        assertEquals(id, payment.id());
        assertEquals(new BigDecimal("99.99"), payment.amount());
        assertEquals("GBP", payment.currency());
        assertEquals("cust-99", payment.customerId());
        assertEquals("APPLE_PAY", payment.paymentMethod());
        assertEquals("UK", payment.country());
        assertEquals(PaymentStatus.PROCESSING, payment.status());
        assertEquals("PAYPAL", payment.provider());
        assertEquals("timeout", payment.failureReason());
        assertEquals(Map.of("x", 1), payment.metadata());
        assertEquals(now, payment.createdAt());
        assertEquals(now, payment.updatedAt());
    }
}
