package com.poc.gateway.repository;

import com.poc.gateway.entity.Payment;
import com.poc.gateway.entity.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentRepositoryTest {

    private PaymentRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PaymentRepository();
    }

    private Payment createAndStorePayment(String customerId, PaymentStatus status) {
        Payment payment = Payment.create(
            new BigDecimal("100.00"),
            "USD",
            customerId,
            "CREDIT_CARD",
            "US",
            Map.of()
        );
        Payment saved = repository.save(payment);
        if (status != PaymentStatus.PENDING) {
            return repository.update(saved, status);
        }
        return saved;
    }

    @Test
    void save_returnsPaymentWithGeneratedId() {
        Payment payment = Payment.create(
            new BigDecimal("50.00"), "USD", "cust-1", "CREDIT_CARD", "US", Map.of()
        );

        Payment saved = repository.save(payment);

        assertNotNull(saved.id());
        assertEquals(PaymentStatus.PENDING, saved.status());
    }

    @Test
    void findById_returnsPaymentForValidId() {
        Payment saved = createAndStorePayment("cust-1", PaymentStatus.PENDING);

        Optional<Payment> found = repository.findById(saved.id());

        assertTrue(found.isPresent());
        assertEquals(saved.id(), found.get().id());
        assertEquals("cust-1", found.get().customerId());
    }

    @Test
    void findById_returnsEmptyForInvalidId() {
        Optional<Payment> found = repository.findById(UUID.randomUUID());

        assertTrue(found.isEmpty());
    }

    @Test
    void findAll_returnsAllPaymentsWithNoFilters() {
        createAndStorePayment("cust-1", PaymentStatus.PENDING);
        createAndStorePayment("cust-2", PaymentStatus.APPROVED);
        createAndStorePayment("cust-3", PaymentStatus.FAILED);

        var results = repository.findAll(null, null, 10, 0);

        assertEquals(3, results.size());
    }

    @Test
    void findAll_filtersByCustomerId() {
        createAndStorePayment("cust-1", PaymentStatus.PENDING);
        createAndStorePayment("cust-1", PaymentStatus.APPROVED);
        createAndStorePayment("cust-2", PaymentStatus.PENDING);

        var results = repository.findAll("cust-1", null, 10, 0);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(p -> "cust-1".equals(p.customerId())));
    }

    @Test
    void findAll_filtersByStatus() {
        createAndStorePayment("cust-1", PaymentStatus.PENDING);
        createAndStorePayment("cust-2", PaymentStatus.APPROVED);
        createAndStorePayment("cust-3", PaymentStatus.PENDING);

        var results = repository.findAll(null, PaymentStatus.PENDING, 10, 0);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(p -> p.status() == PaymentStatus.PENDING));
    }

    @Test
    void findAll_filtersByCustomerIdAndStatus() {
        createAndStorePayment("cust-1", PaymentStatus.PENDING);
        createAndStorePayment("cust-1", PaymentStatus.APPROVED);
        createAndStorePayment("cust-2", PaymentStatus.PENDING);

        var results = repository.findAll("cust-1", PaymentStatus.APPROVED, 10, 0);

        assertEquals(1, results.size());
        assertEquals("cust-1", results.get(0).customerId());
        assertEquals(PaymentStatus.APPROVED, results.get(0).status());
    }

    @Test
    void findAll_respectsLimit() {
        for (int i = 0; i < 5; i++) {
            createAndStorePayment("cust-1", PaymentStatus.PENDING);
        }

        var results = repository.findAll(null, null, 3, 0);

        assertEquals(3, results.size());
    }

    @Test
    void findAll_respectsOffset() {
        for (int i = 0; i < 5; i++) {
            createAndStorePayment("cust-" + i, PaymentStatus.PENDING);
        }

        var results = repository.findAll(null, null, 10, 2);

        assertEquals(3, results.size());
    }

    @Test
    void findAll_returnsEmptyForNoMatches() {
        createAndStorePayment("cust-1", PaymentStatus.PENDING);

        var results = repository.findAll("nonexistent", null, 10, 0);

        assertTrue(results.isEmpty());
    }

    @Test
    void count_returnsCorrectTotal() {
        createAndStorePayment("cust-1", PaymentStatus.PENDING);
        createAndStorePayment("cust-1", PaymentStatus.APPROVED);
        createAndStorePayment("cust-2", PaymentStatus.PENDING);

        assertEquals(3, repository.count(null, null));
    }

    @Test
    void count_filtersByCustomerId() {
        createAndStorePayment("cust-1", PaymentStatus.PENDING);
        createAndStorePayment("cust-1", PaymentStatus.APPROVED);
        createAndStorePayment("cust-2", PaymentStatus.PENDING);

        assertEquals(2, repository.count("cust-1", null));
    }

    @Test
    void count_filtersByStatus() {
        createAndStorePayment("cust-1", PaymentStatus.PENDING);
        createAndStorePayment("cust-2", PaymentStatus.APPROVED);
        createAndStorePayment("cust-3", PaymentStatus.PENDING);

        assertEquals(2, repository.count(null, PaymentStatus.PENDING));
    }

    @Test
    void count_returnsZeroForEmptyStore() {
        assertEquals(0, repository.count(null, null));
    }

    @Test
    void update_persistsChanges() {
        Payment saved = createAndStorePayment("cust-1", PaymentStatus.PENDING);
        Payment updated = saved.withStatus(PaymentStatus.APPROVED).withProvider("provider-a");

        Payment result = repository.update(updated, PaymentStatus.APPROVED);

        assertEquals(PaymentStatus.APPROVED, result.status());
        assertEquals("provider-a", result.provider());

        Optional<Payment> found = repository.findById(saved.id());
        assertTrue(found.isPresent());
        assertEquals(PaymentStatus.APPROVED, found.get().status());
    }


}
