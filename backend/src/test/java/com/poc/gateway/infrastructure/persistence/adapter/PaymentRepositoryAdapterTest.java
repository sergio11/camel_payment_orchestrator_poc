package com.poc.gateway.infrastructure.persistence.adapter;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.domain.exception.PaymentNotFoundException;
import com.poc.gateway.domain.model.PaymentStatus;
import com.poc.gateway.infrastructure.persistence.entity.PaymentEntity;
import com.poc.gateway.infrastructure.persistence.entity.PaymentMetadataEntity;
import com.poc.gateway.infrastructure.persistence.mapper.PaymentPersistenceMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentRepositoryAdapterTest {

    @Mock EntityManager em;
    @Mock PaymentPersistenceMapper persistenceMapper;
    @InjectMocks PaymentRepositoryAdapter adapter;

    private Payment domainPayment;
    private PaymentEntity entityPayment;

    @BeforeEach
    void setUp() {
        domainPayment = new Payment(
            UUID.randomUUID(), new BigDecimal("100"), "USD", "c1", "CARD", "US",
            PaymentStatus.PENDING, null, null, PaymentMetadata.empty(),
            LocalDateTime.now(), LocalDateTime.now()
        );
        entityPayment = new PaymentEntity();
        entityPayment.id = domainPayment.id();
        entityPayment.amount = domainPayment.amount();
        entityPayment.currency = domainPayment.currency();
        entityPayment.customerId = domainPayment.customerId();
        entityPayment.paymentMethod = domainPayment.paymentMethod();
        entityPayment.country = domainPayment.country();
        entityPayment.status = PaymentStatus.PENDING;
        entityPayment.createdAt = domainPayment.createdAt();
        entityPayment.updatedAt = domainPayment.updatedAt();
    }

    @Test
    void save_newPayment_setsDefaultsAndMerges() {
        entityPayment.metadata = new PaymentMetadataEntity();
        entityPayment.metadata.paymentId = entityPayment.id;
        entityPayment.metadata.enrichedAt = "2024-01-01";
        entityPayment.metadata.velocityScore = 5;
        entityPayment.metadata.geoRiskScore = 10;
        Payment enrichedDomainPayment = new Payment(
            domainPayment.id(), domainPayment.amount(), domainPayment.currency(),
            domainPayment.customerId(), domainPayment.paymentMethod(), domainPayment.country(),
            domainPayment.status(), null, null,
            new PaymentMetadata("order-1", 3, true, 30, "LOW", "2024-01-01", 5, 10),
            domainPayment.createdAt(), domainPayment.updatedAt()
        );
        when(persistenceMapper.toEntity(domainPayment)).thenReturn(entityPayment);
        when(em.merge(entityPayment)).thenReturn(entityPayment);
        when(persistenceMapper.toDomain(entityPayment)).thenReturn(enrichedDomainPayment);

        Payment result = adapter.save(domainPayment);

        assertNotNull(result);
        assertEquals("2024-01-01", result.metadata().enrichedAt());
        assertEquals(5, result.metadata().velocityScore());
        assertEquals(10, result.metadata().geoRiskScore());
        verify(em).merge(entityPayment);
        verify(em).flush();
    }

    @Test
    void save_withIdempotencyKey_setsKey() {
        when(persistenceMapper.toEntity(domainPayment)).thenReturn(entityPayment);
        when(em.merge(entityPayment)).thenReturn(entityPayment);
        when(persistenceMapper.toDomain(entityPayment)).thenReturn(domainPayment);

        adapter.save(domainPayment, "idem-key-123");

        assertEquals("idem-key-123", entityPayment.idempotencyKey);
    }

    @Test
    void save_withNullId_generatesUuid() {
        entityPayment.id = null;
        when(persistenceMapper.toEntity(domainPayment)).thenReturn(entityPayment);
        when(em.merge(entityPayment)).thenReturn(entityPayment);
        when(persistenceMapper.toDomain(entityPayment)).thenReturn(domainPayment);

        adapter.save(domainPayment);

        assertNotNull(entityPayment.id);
    }

    @Test
    void save_withNullStatus_setsPending() {
        entityPayment.status = null;
        when(persistenceMapper.toEntity(domainPayment)).thenReturn(entityPayment);
        when(em.merge(entityPayment)).thenReturn(entityPayment);
        when(persistenceMapper.toDomain(entityPayment)).thenReturn(domainPayment);

        adapter.save(domainPayment);

        assertEquals(PaymentStatus.PENDING, entityPayment.status);
    }

    @Test
    void save_withNullCreatedAt_setsNow() {
        entityPayment.createdAt = null;
        when(persistenceMapper.toEntity(domainPayment)).thenReturn(entityPayment);
        when(em.merge(entityPayment)).thenReturn(entityPayment);
        when(persistenceMapper.toDomain(entityPayment)).thenReturn(domainPayment);

        adapter.save(domainPayment);

        assertNotNull(entityPayment.createdAt);
    }

    @SuppressWarnings("unchecked")
    @Test
    void findByIdempotencyKey_validKey_returnsPayment() {
        TypedQuery<PaymentEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(PaymentEntity.class))).thenReturn(query);
        when(query.setParameter(eq("k"), eq("key-1"))).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(entityPayment));
        when(persistenceMapper.toDomain(entityPayment)).thenReturn(domainPayment);

        Optional<Payment> result = adapter.findByIdempotencyKey("key-1");

        assertTrue(result.isPresent());
        assertEquals(domainPayment.id(), result.get().id());
    }

    @Test
    void findByIdempotencyKey_nullKey_returnsEmpty() {
        Optional<Payment> result = adapter.findByIdempotencyKey(null);
        assertFalse(result.isPresent());
    }

    @Test
    void findByIdempotencyKey_blankKey_returnsEmpty() {
        Optional<Payment> result = adapter.findByIdempotencyKey("  ");
        assertFalse(result.isPresent());
    }

    @SuppressWarnings("unchecked")
    @Test
    void findByIdempotencyKey_emptyResult_returnsEmpty() {
        TypedQuery<PaymentEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(PaymentEntity.class))).thenReturn(query);
        when(query.setParameter(eq("k"), eq("key-1"))).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        Optional<Payment> result = adapter.findByIdempotencyKey("key-1");
        assertFalse(result.isPresent());
    }

    @Test
    void findById_validId_returnsPayment() {
        when(em.find(eq(PaymentEntity.class), eq(domainPayment.id()))).thenReturn(entityPayment);
        when(persistenceMapper.toDomain(entityPayment)).thenReturn(domainPayment);

        Optional<Payment> result = adapter.findById(domainPayment.id());
        assertTrue(result.isPresent());
    }

    @Test
    void findById_nullId_returnsEmpty() {
        Optional<Payment> result = adapter.findById(null);
        assertFalse(result.isPresent());
    }

    @Test
    void findById_notFound_returnsEmpty() {
        UUID randomId = UUID.randomUUID();
        when(em.find(eq(PaymentEntity.class), eq(randomId))).thenReturn(null);
        Optional<Payment> result = adapter.findById(randomId);
        assertFalse(result.isPresent());
    }

    @SuppressWarnings("unchecked")
    @Test
    void findAll_noFilters_returnsList() {
        TypedQuery<PaymentEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(PaymentEntity.class))).thenReturn(query);
        when(query.setFirstResult(anyInt())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(entityPayment));
        when(persistenceMapper.toDomain(entityPayment)).thenReturn(domainPayment);

        List<Payment> result = adapter.findAll(null, null, 10, 0);
        assertEquals(1, result.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    void findAll_withCustomerId_filtersByCustomer() {
        TypedQuery<PaymentEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(PaymentEntity.class))).thenReturn(query);
        when(query.setParameter(eq("customerId"), eq("c1"))).thenReturn(query);
        when(query.setFirstResult(anyInt())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(entityPayment));
        when(persistenceMapper.toDomain(entityPayment)).thenReturn(domainPayment);

        List<Payment> result = adapter.findAll("c1", null, 10, 0);
        assertEquals(1, result.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    void findAll_withStatus_filtersByStatus() {
        TypedQuery<PaymentEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(PaymentEntity.class))).thenReturn(query);
        when(query.setParameter(eq("status"), any())).thenReturn(query);
        when(query.setFirstResult(anyInt())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(entityPayment));
        when(persistenceMapper.toDomain(entityPayment)).thenReturn(domainPayment);

        List<Payment> result = adapter.findAll(null, PaymentStatus.APPROVED, 10, 0);
        assertEquals(1, result.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    void findAll_negativeOffset_usesZero() {
        TypedQuery<PaymentEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(PaymentEntity.class))).thenReturn(query);
        when(query.setFirstResult(eq(0))).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        List<Payment> result = adapter.findAll(null, null, 10, -5);
        assertNotNull(result);
        verify(query).setFirstResult(0);
    }

    @SuppressWarnings("unchecked")
    @Test
    void findAll_negativeLimit_usesOne() {
        TypedQuery<PaymentEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(PaymentEntity.class))).thenReturn(query);
        when(query.setFirstResult(anyInt())).thenReturn(query);
        when(query.setMaxResults(eq(1))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        List<Payment> result = adapter.findAll(null, null, -1, 0);
        assertNotNull(result);
        verify(query).setMaxResults(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void count_noFilters_returnsCount() {
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.getSingleResult()).thenReturn(5L);

        long result = adapter.count(null, null);
        assertEquals(5L, result);
    }

    @SuppressWarnings("unchecked")
    @Test
    void count_withCustomerId_filtersByCustomer() {
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter(eq("customerId"), eq("c1"))).thenReturn(query);
        when(query.getSingleResult()).thenReturn(3L);

        long result = adapter.count("c1", null);
        assertEquals(3L, result);
    }

    @SuppressWarnings("unchecked")
    @Test
    void count_withStatus_filtersByStatus() {
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter(eq("status"), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(2L);

        long result = adapter.count(null, PaymentStatus.APPROVED);
        assertEquals(2L, result);
    }

    @Test
    void update_found_updatesAndReturns() {
        when(em.find(eq(PaymentEntity.class), eq(domainPayment.id()))).thenReturn(entityPayment);
        when(em.merge(entityPayment)).thenReturn(entityPayment);
        when(persistenceMapper.toDomain(entityPayment)).thenReturn(domainPayment);

        Payment result = adapter.update(domainPayment.id(), PaymentStatus.APPROVED);
        assertNotNull(result);
        verify(em).merge(entityPayment);
        verify(em).flush();
    }

    @Test
    void update_notFound_throwsException() {
        UUID randomId = UUID.randomUUID();
        when(em.find(eq(PaymentEntity.class), eq(randomId))).thenReturn(null);
        assertThrows(PaymentNotFoundException.class, () ->
            adapter.update(randomId, PaymentStatus.APPROVED));
    }

    @Test
    void updateIfPending_notFound_returnsEmpty() {
        UUID randomId = UUID.randomUUID();
        when(em.find(eq(PaymentEntity.class), eq(randomId))).thenReturn(null);
        Optional<Payment> result = adapter.updateIfPending(randomId, PaymentStatus.APPROVED);
        assertFalse(result.isPresent());
    }

    @Test
    void updateIfPending_notPending_returnsEmpty() {
        entityPayment.status = PaymentStatus.APPROVED;
        when(em.find(eq(PaymentEntity.class), eq(domainPayment.id()))).thenReturn(entityPayment);
        Optional<Payment> result = adapter.updateIfPending(domainPayment.id(), PaymentStatus.REJECTED);
        assertFalse(result.isPresent());
    }

    @Test
    void updateIfPending_pending_updatesAndReturns() {
        when(em.find(eq(PaymentEntity.class), eq(domainPayment.id()))).thenReturn(entityPayment);
        when(em.merge(entityPayment)).thenReturn(entityPayment);
        when(persistenceMapper.toDomain(entityPayment)).thenReturn(domainPayment);

        Optional<Payment> result = adapter.updateIfPending(domainPayment.id(), PaymentStatus.APPROVED);
        assertTrue(result.isPresent());
        verify(em).merge(entityPayment);
    }

    @Test
    void updateIfPending_optimisticLock_returnsEmpty() {
        when(em.find(eq(PaymentEntity.class), eq(domainPayment.id()))).thenReturn(entityPayment);
        when(em.merge(entityPayment)).thenThrow(new OptimisticLockException());

        Optional<Payment> result = adapter.updateIfPending(domainPayment.id(), PaymentStatus.APPROVED);
        assertFalse(result.isPresent());
    }

    @Test
    void update_nullStatus_setsNullStatus() {
        when(em.find(eq(PaymentEntity.class), eq(domainPayment.id()))).thenReturn(entityPayment);
        when(em.merge(entityPayment)).thenReturn(entityPayment);
        when(persistenceMapper.toDomain(entityPayment)).thenReturn(domainPayment);

        Payment result = adapter.update(domainPayment.id(), null);
        assertNotNull(result);
        verify(em).merge(entityPayment);
    }

    @Test
    void deleteById_found_removesEntity() {
        when(em.find(eq(PaymentEntity.class), eq(domainPayment.id()))).thenReturn(entityPayment);
        adapter.deleteById(domainPayment.id());
        verify(em).remove(entityPayment);
    }

    @Test
    void deleteById_notFound_doesNothing() {
        UUID randomId = UUID.randomUUID();
        when(em.find(eq(PaymentEntity.class), eq(randomId))).thenReturn(null);
        adapter.deleteById(randomId);
        verify(em, never()).remove(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void findAll_withBothFilters_filtersByCustomerAndStatus() {
        TypedQuery<PaymentEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(PaymentEntity.class))).thenReturn(query);
        when(query.setParameter(eq("customerId"), eq("c1"))).thenReturn(query);
        when(query.setParameter(eq("status"), any())).thenReturn(query);
        when(query.setFirstResult(anyInt())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(entityPayment));
        when(persistenceMapper.toDomain(entityPayment)).thenReturn(domainPayment);

        List<Payment> result = adapter.findAll("c1", PaymentStatus.APPROVED, 10, 0);
        assertEquals(1, result.size());
        verify(query).setParameter("customerId", "c1");
        verify(query).setParameter(eq("status"), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void count_withBothFilters_filtersByCustomerAndStatus() {
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter(eq("customerId"), eq("c1"))).thenReturn(query);
        when(query.setParameter(eq("status"), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1L);

        long result = adapter.count("c1", PaymentStatus.APPROVED);
        assertEquals(1L, result);
        verify(query).setParameter("customerId", "c1");
        verify(query).setParameter(eq("status"), any());
    }
}
