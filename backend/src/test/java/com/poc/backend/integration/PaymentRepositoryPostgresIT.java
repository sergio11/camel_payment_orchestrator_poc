package com.poc.backend.integration;

import com.poc.camel.testsupport.container.PostgresTestContainer;
import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.domain.model.PaymentStatus;
import com.poc.gateway.domain.port.outbound.PaymentRepositoryPort;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(BackendIntegrationTest.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentRepositoryPostgresIT {

    @Inject
    PaymentRepositoryPort paymentRepository;

    @AfterEach
    void cleanup() {
        paymentRepository.findAll(null, null, 1000, 0)
            .forEach(p -> paymentRepository.deleteById(p.id()));
    }

    @Test
    @DisplayName("IT: Persist and retrieve payment from real PostgreSQL")
    void testPersistAndRetrievePayment() {
        Payment payment = Payment.create(
            new BigDecimal("150.00"), "USD", "cust-123",
            "CREDIT_CARD", "US", PaymentMetadata.empty()
        );

        Payment saved = paymentRepository.save(payment);
        assertThat(saved.id()).isNotNull();

        Optional<Payment> found = paymentRepository.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().amount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(found.get().status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("IT: Update payment status from PENDING to APPROVED in real PostgreSQL")
    void testUpdatePaymentStatus() {
        Payment payment = Payment.create(
            new BigDecimal("200.00"), "EUR", "cust-456",
            "WALLET", "ES", PaymentMetadata.empty()
        );

        Payment saved = paymentRepository.save(payment);

        Optional<Payment> updated = paymentRepository.updateIfPending(saved.id(), PaymentStatus.APPROVED);
        assertThat(updated).isPresent();
        assertThat(updated.get().status()).isEqualTo(PaymentStatus.APPROVED);

        Optional<Payment> found = paymentRepository.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("IT: Update status only if PENDING - idempotency check in real PostgreSQL")
    void testUpdateStatusIdempotency() {
        Payment payment = Payment.create(
            new BigDecimal("50.00"), "GBP", "cust-789",
            "CREDIT_CARD", "GB", PaymentMetadata.empty()
        );

        Payment saved = paymentRepository.save(payment);
        paymentRepository.update(saved.id(), PaymentStatus.APPROVED);

        Optional<Payment> notUpdated = paymentRepository.updateIfPending(saved.id(), PaymentStatus.FAILED);
        assertThat(notUpdated).isEmpty();

        Optional<Payment> found = paymentRepository.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("IT: List payments by customer in real PostgreSQL")
    void testListByCustomer() {
        for (int i = 0; i < 3; i++) {
            paymentRepository.save(Payment.create(
                new BigDecimal("10.00"), "USD", "cust-list-test",
                "CREDIT_CARD", "US", PaymentMetadata.empty()
            ));
        }

        var results = paymentRepository.findAll("cust-list-test", null, 10, 0);
        assertThat(results).hasSize(3);
    }

    @Test
    @DisplayName("IT: Count payments in real PostgreSQL")
    void testCountPayments() {
        paymentRepository.save(Payment.create(
            new BigDecimal("100.00"), "USD", "cust-count",
            "CREDIT_CARD", "US", PaymentMetadata.empty()
        ));
        paymentRepository.save(Payment.create(
            new BigDecimal("200.00"), "USD", "cust-count",
            "WALLET", "US", PaymentMetadata.empty()
        ));

        long count = paymentRepository.count("cust-count", null);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("IT: Delete payment in real PostgreSQL")
    void testDeletePayment() {
        Payment payment = Payment.create(
            new BigDecimal("99.99"), "USD", "cust-delete",
            "CREDIT_CARD", "US", PaymentMetadata.empty()
        );
        Payment saved = paymentRepository.save(payment);

        paymentRepository.deleteById(saved.id());

        Optional<Payment> found = paymentRepository.findById(saved.id());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("IT: Find by idempotency key in real PostgreSQL")
    void testFindByIdempotencyKey() {
        Payment payment = Payment.create(
            new BigDecimal("75.00"), "USD", "cust-idem",
            "CREDIT_CARD", "US", PaymentMetadata.empty()
        );
        paymentRepository.save(payment, "idem-key-123");

        Optional<Payment> found = paymentRepository.findByIdempotencyKey("idem-key-123");
        assertThat(found).isPresent();
        assertThat(found.get().customerId()).isEqualTo("cust-idem");
    }

    @Test
    @DisplayName("IT: Find by non-existent idempotency key returns empty")
    void testFindByIdempotencyKey_notFound() {
        Optional<Payment> found = paymentRepository.findByIdempotencyKey("nonexistent");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("IT: Find by null idempotency key returns empty")
    void testFindByIdempotencyKey_null() {
        Optional<Payment> found = paymentRepository.findByIdempotencyKey(null);
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("IT: Find by null id returns empty")
    void testFindById_null() {
        Optional<Payment> found = paymentRepository.findById(null);
        assertThat(found).isEmpty();
    }
}
