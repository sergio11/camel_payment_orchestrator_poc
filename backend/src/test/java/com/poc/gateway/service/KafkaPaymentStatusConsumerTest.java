package com.poc.gateway.service;

import com.poc.gateway.entity.Payment;
import com.poc.gateway.entity.PaymentStatus;
import com.poc.gateway.repository.PaymentRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class KafkaPaymentStatusConsumerTest {

    @Inject
    PaymentRepository repository;

    @BeforeEach
    void setup() {
        // Clean is not possible with in-memory, but we can test the logic
    }

    @Test
    @DisplayName("Verify PaymentRepository can update status")
    void testPaymentStatusUpdate() {
        // Given: A payment in PENDING status
        Payment payment = new Payment(
            UUID.randomUUID(),
            new BigDecimal("100.00"),
            "USD",
            "customer-test",
            "CREDIT_CARD",
            "US",
            PaymentStatus.PENDING,
            null,
            null,
            null,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
        Payment saved = repository.save(payment);

        // When: Update to APPROVED
        Payment updated = saved.withStatus(PaymentStatus.APPROVED);
        repository.save(updated);

        // Then: Status should be updated
        Optional<Payment> found = repository.findById(saved.id());
        assertTrue(found.isPresent());
        assertEquals(PaymentStatus.APPROVED, found.get().status());
    }

    @Test
    @DisplayName("Verify PaymentRepository can update to FAILED status")
    void testPaymentStatusUpdateToFailed() {
        // Given: A payment
        Payment payment = new Payment(
            UUID.randomUUID(),
            new BigDecimal("50.00"),
            "EUR",
            "customer-fail",
            "WALLET",
            "ES",
            PaymentStatus.PENDING,
            null,
            null,
            null,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
        Payment saved = repository.save(payment);

        // When: Update to FAILED
        Payment updated = saved.withStatus(PaymentStatus.FAILED);
        repository.save(updated);

        // Then: Status should be FAILED
        Optional<Payment> found = repository.findById(saved.id());
        assertTrue(found.isPresent());
        assertEquals(PaymentStatus.FAILED, found.get().status());
    }
}
