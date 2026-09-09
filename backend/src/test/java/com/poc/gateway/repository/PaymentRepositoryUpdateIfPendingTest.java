package com.poc.gateway.repository;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.entity.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PaymentRepositoryUpdateIfPendingTest {

    private PaymentRepository repository;

    @BeforeEach
    void setUp() {
        repository = PaymentRepository.inMemory();
    }

    @Test
    void updateIfPending_transitionsPendingToApproved() {
        Payment saved = repository.save(Payment.create(
            new BigDecimal("10.00"), "USD", "c1", "CREDIT_CARD", "US", PaymentMetadata.empty()));

        Optional<Payment> updated = repository.updateIfPending(saved.id(), PaymentStatus.APPROVED);

        assertTrue(updated.isPresent());
        assertEquals(PaymentStatus.APPROVED, updated.get().status());
    }

    @Test
    void updateIfPending_secondTransitionReturnsEmpty() {
        Payment saved = repository.save(Payment.create(
            new BigDecimal("10.00"), "USD", "c1", "CREDIT_CARD", "US", PaymentMetadata.empty()));

        assertTrue(repository.updateIfPending(saved.id(), PaymentStatus.APPROVED).isPresent());
        assertTrue(repository.updateIfPending(saved.id(), PaymentStatus.FAILED).isEmpty());
        assertEquals(PaymentStatus.APPROVED, repository.findById(saved.id()).orElseThrow().status());
    }

    @Test
    void updateIfPending_unknownIdReturnsEmpty() {
        assertTrue(repository.updateIfPending(java.util.UUID.randomUUID(), PaymentStatus.APPROVED).isEmpty());
    }
}
