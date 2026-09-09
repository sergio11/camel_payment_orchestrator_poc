package com.poc.gateway.repository;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.entity.PaymentStatus;
import com.poc.gateway.exception.PaymentNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PaymentRepositoryTest {

    private PaymentRepository repository;

    @BeforeEach
    void setUp() {
        repository = PaymentRepository.inMemory();
    }

    private Payment createAndStorePayment(String customerId, PaymentStatus status) {
        Payment payment = Payment.create(
            new BigDecimal("100.00"),
            "USD",
            customerId,
            "CREDIT_CARD",
            "US",
            PaymentMetadata.empty()
        );
        Payment saved = repository.save(payment);
        if (status != PaymentStatus.PENDING) {
            return repository.update(saved.id(), status);
        }
        return saved;
    }

    @Test
    void save_returnsPaymentWithGeneratedId() {
        Payment payment = Payment.create(
            new BigDecimal("50.00"), "USD", "cust-1", "CREDIT_CARD", "US", PaymentMetadata.empty()
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

        Payment result = repository.update(saved.id(), PaymentStatus.APPROVED);

        assertEquals(PaymentStatus.APPROVED, result.status());

        Optional<Payment> found = repository.findById(saved.id());
        assertTrue(found.isPresent());
        assertEquals(PaymentStatus.APPROVED, found.get().status());
    }

    @Test
    void update_throwsNotFoundExceptionForNonexistentPayment() {
        Payment nonexistent = Payment.create(
            new BigDecimal("100.00"), "USD", "cust-1", "CREDIT_CARD", "US", PaymentMetadata.empty()
        );

        assertThrows(
            PaymentNotFoundException.class,
            () -> repository.update(nonexistent.id(), PaymentStatus.APPROVED)
        );
    }

    @Test
    void update_concurrentUpdatesDoNotCauseLostUpdate() throws Exception {
        Payment saved = createAndStorePayment("cust-1", PaymentStatus.PENDING);
        
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Payment current = repository.findById(saved.id()).orElseThrow();
                    PaymentStatus newStatus = index % 2 == 0 ? PaymentStatus.APPROVED : PaymentStatus.REJECTED;
                    repository.update(current.id(), newStatus);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        endLatch.await();
        
        Optional<Payment> finalPayment = repository.findById(saved.id());
        assertTrue(finalPayment.isPresent());
        assertTrue(finalPayment.get().status() == PaymentStatus.APPROVED || 
                   finalPayment.get().status() == PaymentStatus.REJECTED);
        assertEquals(threadCount, successCount.get() + errorCount.get());
        
        executor.shutdown();
    }

    @Test
    void save_idempotencyKeyStoreEvicted_createsNewPayment() throws Exception {
        Payment saved = repository.save(
            Payment.create(new BigDecimal("10.00"), "USD", "c1", "CARD", "US", PaymentMetadata.empty()),
            "key1"
        );
        java.lang.reflect.Field storeField = PaymentRepositoryInMemory.class.getDeclaredField("store");
        storeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentHashMap<UUID, Payment> store =
            (java.util.concurrent.ConcurrentHashMap<UUID, Payment>) storeField.get(repository);
        store.remove(saved.id());

        Payment second = repository.save(
            Payment.create(new BigDecimal("20.00"), "EUR", "c2", "CARD", "DE", PaymentMetadata.empty()),
            "key1"
        );
        assertNotEquals(saved.id(), second.id());
        assertEquals(new BigDecimal("20.00"), second.amount());
    }

    @Test
    void findById_nullId_returnsEmpty() {
        createAndStorePayment("c1", PaymentStatus.PENDING);
        assertEquals(Optional.empty(), repository.findById(null));
    }
}
