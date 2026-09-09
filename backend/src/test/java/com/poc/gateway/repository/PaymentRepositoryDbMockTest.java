package com.poc.gateway.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.gateway.domain.Payment;
import com.poc.gateway.entity.PaymentEntity;
import com.poc.gateway.entity.PaymentMetadataEntity;
import com.poc.gateway.entity.PaymentStatus;
import com.poc.gateway.exception.PaymentNotFoundException;
import com.poc.gateway.mapper.PaymentPersistenceMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentRepositoryDbMockTest {

    @Mock
    EntityManager em;

    @Mock
    TypedQuery<PaymentEntity> entityQuery;

    @Mock
    TypedQuery<Long> countQuery;

    private PaymentRepositoryJpa repository;
    private PaymentPersistenceMapper persistenceMapper = new PaymentPersistenceMapper();

    @BeforeEach
    void setUp() throws Exception {
        repository = new PaymentRepositoryJpa();
        setField("em", em);
        setField("persistenceMapper", persistenceMapper);
        lenient().when(em.merge(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void setField(String name, Object value) throws Exception {
        Field f = PaymentRepositoryJpa.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(repository, value);
    }

    private Payment fullPayment() {
        return new Payment(
            UUID.randomUUID(), new BigDecimal("100.00"), "USD", "cust-1",
            "CREDIT_CARD", "US", PaymentStatus.PENDING, "prov", null,
            Map.of("k", "v"), LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private PaymentEntity fullEntity(UUID id) {
        PaymentEntity e = new PaymentEntity();
        e.id = id;
        e.amount = new BigDecimal("100.00");
        e.currency = "USD";
        e.customerId = "cust-1";
        e.paymentMethod = "CREDIT_CARD";
        e.country = "US";
        e.status = PaymentStatus.PENDING;
        e.provider = "prov";
        e.failureReason = null;
        e.createdAt = LocalDateTime.now();
        e.updatedAt = LocalDateTime.now();
        PaymentMetadataEntity meta = new PaymentMetadataEntity();
        meta.paymentId = id;
        meta.additionalProperties = "{\"k\":\"v\"}";
        e.metadata = meta;
        return e;
    }

    @SuppressWarnings("unchecked")
    private void stubEntityQuery(List<PaymentEntity> result) {
        when(em.createQuery(anyString(), eq(PaymentEntity.class))).thenReturn(entityQuery);
        lenient().when(entityQuery.setParameter(anyString(), any())).thenReturn(entityQuery);
        lenient().when(entityQuery.setFirstResult(anyInt())).thenReturn(entityQuery);
        lenient().when(entityQuery.setMaxResults(anyInt())).thenReturn(entityQuery);
        when(entityQuery.getResultList()).thenReturn(result);
    }

    // ========== save (DB path) ==========

    @Test
    @DisplayName("save persists full payment via EM")
    void save_fullPayment_merges() {
        Payment saved = repository.save(fullPayment(), "key-1");
        assertNotNull(saved.id());
        verify(em).merge(any(PaymentEntity.class));
        verify(em).flush();
    }

    @Test
    @DisplayName("save applies defaults for null id, status and createdAt")
    void save_nulls_appliesDefaults() {
        Payment p = new Payment(null, new BigDecimal("10.00"), "EUR", "c", "CARD",
            "ES", null, null, null, Map.of(), null, null);
        Payment saved = repository.save(p, "key-2");
        assertNotNull(saved.id());
        assertEquals(PaymentStatus.PENDING, saved.status());
        assertNotNull(saved.createdAt());
        verify(em).merge(any(PaymentEntity.class));
    }

    // ========== saveInMemory idempotency branches ==========

    @Test
    @DisplayName("saveInMemory second save with same key returns existing")
    void saveInMemory_sameKey_returnsExisting() throws Exception {
        PaymentRepository mem = PaymentRepository.inMemory();
        Payment first = mem.save(fullPayment(), "idem-1");
        Payment second = mem.save(fullPayment(), "idem-1");
        assertEquals(first.id(), second.id());
    }

    @Test
    @DisplayName("saveInMemory stores key index")
    void saveInMemory_withKey_indexed() throws Exception {
        PaymentRepository mem = PaymentRepository.inMemory();
        Payment saved = mem.save(fullPayment(), "idem-2");
        assertTrue(mem.findByIdempotencyKey("idem-2").isPresent());
        assertEquals(saved.id(), mem.findByIdempotencyKey("idem-2").orElseThrow().id());
    }

    @Test
    @DisplayName("findByIdempotencyKey blank and miss return empty")
    void findByIdempotencyKey_blankAndMiss_empty() throws Exception {
        PaymentRepository mem = PaymentRepository.inMemory();
        assertTrue(mem.findByIdempotencyKey(null).isEmpty());
        assertTrue(mem.findByIdempotencyKey("  ").isEmpty());
        assertTrue(mem.findByIdempotencyKey("nope").isEmpty());
    }

    // ========== findByIdempotencyKey (DB path) ==========

    @Test
    @DisplayName("findByIdempotencyKey blank returns empty without query")
    void findByIdempotencyKey_blank_empty() {
        assertTrue(repository.findByIdempotencyKey(null).isEmpty());
        assertTrue(repository.findByIdempotencyKey(" ").isEmpty());
        verifyNoInteractions(em);
    }

    @Test
    @DisplayName("findByIdempotencyKey returns mapped payment on hit")
    void findByIdempotencyKey_hit_mapped() {
        UUID id = UUID.randomUUID();
        stubEntityQuery(List.of(fullEntity(id)));
        Optional<Payment> found = repository.findByIdempotencyKey("k");
        assertTrue(found.isPresent());
        assertEquals(id, found.get().id());
        assertEquals("v", found.get().metadata().get("k"));
    }

    @Test
    @DisplayName("findByIdempotencyKey returns empty on miss")
    void findByIdempotencyKey_miss_empty() {
        stubEntityQuery(List.of());
        assertTrue(repository.findByIdempotencyKey("k").isEmpty());
    }

    // ========== findById (DB path) ==========

    @Test
    @DisplayName("findById maps entity")
    void findById_hit_mapped() {
        UUID id = UUID.randomUUID();
        when(em.find(PaymentEntity.class, id)).thenReturn(fullEntity(id));
        Optional<Payment> found = repository.findById(id);
        assertTrue(found.isPresent());
        assertEquals("cust-1", found.get().customerId());
    }

    @Test
    @DisplayName("findById returns empty when missing")
    void findById_miss_empty() {
        when(em.find(eq(PaymentEntity.class), any(UUID.class))).thenReturn(null);
        assertTrue(repository.findById(UUID.randomUUID()).isEmpty());
    }

    // ========== findAll (DB path, all filter combos) ==========

    @Test
    @DisplayName("findAll without filters")
    void findAll_noFilters() {
        UUID id = UUID.randomUUID();
        stubEntityQuery(List.of(fullEntity(id)));
        List<Payment> all = repository.findAll(null, null, 10, 0);
        assertEquals(1, all.size());
    }

    @Test
    @DisplayName("findAll with customer and status")
    void findAll_customerAndStatus() {
        stubEntityQuery(List.of());
        List<Payment> all = repository.findAll("cust-1", PaymentStatus.PENDING, 5, 2);
        assertTrue(all.isEmpty());
        verify(entityQuery).setParameter("customerId", "cust-1");
        verify(entityQuery).setParameter(eq("status"), eq(PaymentStatus.PENDING));
    }

    @Test
    @DisplayName("findAll with customer only")
    void findAll_customerOnly() {
        stubEntityQuery(List.of());
        repository.findAll("cust-1", null, 5, -3);
        verify(entityQuery).setParameter("customerId", "cust-1");
        verify(entityQuery, never()).setParameter(eq("status"), any());
    }

    @Test
    @DisplayName("findAll with status only")
    void findAll_statusOnly() {
        stubEntityQuery(List.of());
        repository.findAll(null, PaymentStatus.FAILED, 0, 0);
        verify(entityQuery).setParameter(eq("status"), eq(PaymentStatus.FAILED));
    }

    // ========== count (all combos) ==========

    private void stubCount(long value) {
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
        lenient().when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(value);
    }

    @Test
    @DisplayName("count with customer and status")
    void count_customerAndStatus() {
        stubCount(7L);
        assertEquals(7L, repository.count("cust-1", PaymentStatus.PENDING));
        verify(countQuery).setParameter("customerId", "cust-1");
        verify(countQuery).setParameter(eq("status"), eq(PaymentStatus.PENDING));
    }

    @Test
    @DisplayName("count with customer only")
    void count_customerOnly() {
        stubCount(3L);
        assertEquals(3L, repository.count("cust-1", null));
    }

    @Test
    @DisplayName("count with status only")
    void count_statusOnly() {
        stubCount(2L);
        assertEquals(2L, repository.count(null, PaymentStatus.FAILED));
    }

    @Test
    @DisplayName("count without filters")
    void count_noFilters() {
        stubCount(9L);
        assertEquals(9L, repository.count(null, null));
        verify(countQuery, never()).setParameter(anyString(), any());
    }

    @Test
    @DisplayName("count in-memory filters correctly")
    void count_inMemory() throws Exception {
        PaymentRepository mem = PaymentRepository.inMemory();
        mem.save(fullPayment());
        assertEquals(1L, mem.count(null, null));
        assertEquals(1L, mem.count("cust-1", PaymentStatus.PENDING));
        assertEquals(0L, mem.count("other", null));
        assertEquals(0L, mem.count(null, PaymentStatus.FAILED));
    }

    // ========== update (DB path) ==========

    @Test
    @DisplayName("update transitions status")
    void update_found_updates() {
        UUID id = UUID.randomUUID();
        when(em.find(PaymentEntity.class, id)).thenReturn(fullEntity(id));
        Payment updated = repository.update(id, PaymentStatus.APPROVED);
        assertEquals(PaymentStatus.APPROVED, updated.status());
        verify(em).merge(any(PaymentEntity.class));
    }

    @Test
    @DisplayName("update throws when missing")
    void update_missing_throws() {
        UUID id = UUID.randomUUID();
        when(em.find(PaymentEntity.class, id)).thenReturn(null);
        assertThrows(PaymentNotFoundException.class, () -> repository.update(id, PaymentStatus.APPROVED));
    }

    // ========== updateIfPending (DB path) ==========

    @Test
    @DisplayName("updateIfPending transitions pending")
    void updateIfPending_pending_present() {
        UUID id = UUID.randomUUID();
        when(em.find(PaymentEntity.class, id)).thenReturn(fullEntity(id));
        Optional<Payment> updated = repository.updateIfPending(id, PaymentStatus.APPROVED);
        assertTrue(updated.isPresent());
        assertEquals(PaymentStatus.APPROVED, updated.get().status());
    }

    @Test
    @DisplayName("updateIfPending returns empty when missing")
    void updateIfPending_missing_empty() {
        when(em.find(eq(PaymentEntity.class), any(UUID.class))).thenReturn(null);
        assertTrue(repository.updateIfPending(UUID.randomUUID(), PaymentStatus.APPROVED).isEmpty());
    }

    @Test
    @DisplayName("updateIfPending returns empty when already transitioned")
    void updateIfPending_nonPending_empty() {
        UUID id = UUID.randomUUID();
        PaymentEntity e = fullEntity(id);
        e.status = PaymentStatus.APPROVED;
        when(em.find(PaymentEntity.class, id)).thenReturn(e);
        assertTrue(repository.updateIfPending(id, PaymentStatus.FAILED).isEmpty());
        verify(em, never()).merge(any());
    }

    @Test
    @DisplayName("updateIfPending returns empty on optimistic lock")
    void updateIfPending_lockConflict_empty() {
        UUID id = UUID.randomUUID();
        when(em.find(PaymentEntity.class, id)).thenReturn(fullEntity(id));
        when(em.merge(any())).thenThrow(new OptimisticLockException("conflict"));
        assertTrue(repository.updateIfPending(id, PaymentStatus.APPROVED).isEmpty());
    }

    // ========== deleteById ==========

    @Test
    @DisplayName("deleteById removes existing entity")
    void deleteById_found_removes() {
        UUID id = UUID.randomUUID();
        PaymentEntity e = fullEntity(id);
        when(em.find(PaymentEntity.class, id)).thenReturn(e);
        repository.deleteById(id);
        verify(em).remove(e);
    }

    @Test
    @DisplayName("deleteById ignores missing entity")
    void deleteById_missing_noOp() {
        UUID id = UUID.randomUUID();
        when(em.find(PaymentEntity.class, id)).thenReturn(null);
        assertDoesNotThrow(() -> repository.deleteById(id));
        verify(em, never()).remove(any());
    }

    @Test
    @DisplayName("deleteById in-memory removes")
    void deleteById_inMemory() throws Exception {
        PaymentRepository mem = PaymentRepository.inMemory();
        Payment saved = mem.save(fullPayment());
        mem.deleteById(saved.id());
        assertTrue(mem.findById(saved.id()).isEmpty());
    }

    // ========== toEntity / toDomain via PaymentPersistenceMapper ==========

    @Test
    @DisplayName("toEntity maps all fields")
    void toEntity_fullMapping() {
        PaymentEntity e = persistenceMapper.toEntity(fullPayment());
        assertEquals("USD", e.currency);
        assertEquals("cust-1", e.customerId);
        assertEquals(PaymentStatus.PENDING, e.status);
    }

    @Test
    @DisplayName("toDomain maps all fields including metadata")
    void toDomain_fullMapping() {
        UUID id = UUID.randomUUID();
        Payment p = persistenceMapper.toDomain(fullEntity(id));
        assertEquals(id, p.id());
    }

    @Test
    @DisplayName("toDomain handles null metadata gracefully")
    void toDomain_blankMetadata_emptyMap() {
        PaymentEntity e = fullEntity(UUID.randomUUID());
        e.metadata = null;
        assertTrue(persistenceMapper.toDomain(e).metadata().isEmpty());
    }

    @Test
    @DisplayName("saveInMemory handles blank key, null id, status and timestamps")
    void saveInMemory_edgeCases() throws Exception {
        PaymentRepository mem = PaymentRepository.inMemory();
        Payment noIds = new Payment(null, new BigDecimal("1.00"), "USD", "c", "CARD",
            "ES", null, null, null, Map.of(), null, null);
        Payment saved = mem.save(noIds, "  ");
        assertNotNull(saved.id());
        assertEquals(PaymentStatus.PENDING, saved.status());
        assertNotNull(saved.createdAt());
        assertTrue(mem.findByIdempotencyKey("  ").isEmpty());
    }

    @Test
    @DisplayName("saveInMemory recreates when index points to deleted payment")
    void saveInMemory_staleIndex_recreates() throws Exception {
        PaymentRepository mem = PaymentRepository.inMemory();
        Payment first = mem.save(fullPayment(), "stale-1");
        mem.deleteById(first.id());
        Payment second = mem.save(fullPayment(), "stale-1");
        assertNotNull(second.id());
    }

    @Test
    @DisplayName("update in-memory throws for unknown id")
    void update_inMemory_unknown_throws() throws Exception {
        PaymentRepository mem = PaymentRepository.inMemory();
        assertThrows(com.poc.gateway.exception.PaymentNotFoundException.class,
            () -> mem.update(UUID.randomUUID(), PaymentStatus.APPROVED));
    }
}
