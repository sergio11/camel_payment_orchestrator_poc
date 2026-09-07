package com.poc.gateway.repository;

import com.poc.gateway.entity.OutboxEventEntity;
import com.poc.gateway.entity.OutboxStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventRepositoryTest {

    @Mock
    EntityManager em;

    @Mock
    TypedQuery<OutboxEventEntity> query;

    private OutboxEventEntity event(String key) {
        OutboxEventEntity e = new OutboxEventEntity();
        e.id = UUID.randomUUID();
        e.aggregateId = UUID.randomUUID();
        e.type = "payments.events.received";
        e.payload = "{}";
        e.status = OutboxStatus.PENDING;
        e.createdAt = LocalDateTime.now();
        e.idempotencyKey = key;
        return e;
    }

    private OutboxEventRepository withEm(EntityManager em) {
        OutboxEventRepository repo = new OutboxEventRepository();
        repo.em = em;
        return repo;
    }

    @Test
    @DisplayName("isDbAvailable reflects EM presence")
    void isDbAvailable_reflectsEm() {
        assertFalse(withEm(null).isDbAvailable());
        assertTrue(withEm(em).isDbAvailable());
    }

    @Test
    @DisplayName("persist without EM stores in memory by key")
    void persist_noEm_inMemory() {
        OutboxEventRepository repo = withEm(null);
        OutboxEventEntity e = event("k1");
        repo.persist(e);
        assertTrue(repo.findByIdempotencyKey("k1").isPresent());
        verifyNoInteractions(em);
    }

    @Test
    @DisplayName("persist without EM ignores null key")
    void persist_noEm_nullKey_ignored() {
        OutboxEventRepository repo = withEm(null);
        repo.persist(event(null));
        assertTrue(repo.findByIdempotencyKey("k1").isEmpty());
    }

    @Test
    @DisplayName("persist with EM delegates to entity manager")
    void persist_withEm_delegates() {
        OutboxEventRepository repo = withEm(em);
        OutboxEventEntity e = event("k2");
        repo.persist(e);
        verify(em).persist(e);
        verify(em).flush();
    }

    @Test
    @DisplayName("findByIdempotencyKey blank returns empty")
    void findByIdempotencyKey_blank_empty() {
        OutboxEventRepository repo = withEm(em);
        assertTrue(repo.findByIdempotencyKey(null).isEmpty());
        assertTrue(repo.findByIdempotencyKey(" ").isEmpty());
        verifyNoInteractions(em);
    }

    @Test
    @DisplayName("findByIdempotencyKey without EM and unknown key returns empty")
    void findByIdempotencyKey_noEmUnknown_empty() {
        assertTrue(withEm(null).findByIdempotencyKey("unknown").isEmpty());
    }

    @Test
    @DisplayName("findByIdempotencyKey queries DB on miss")
    void findByIdempotencyKey_dbHit() {
        OutboxEventRepository repo = withEm(em);
        OutboxEventEntity e = event("k3");
        when(em.createQuery(anyString(), eq(OutboxEventEntity.class))).thenReturn(query);
        when(query.setParameter(eq("k"), eq("k3"))).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(e));
        Optional<OutboxEventEntity> found = repo.findByIdempotencyKey("k3");
        assertTrue(found.isPresent());
        assertEquals(e.id, found.get().id);
    }

    @Test
    @DisplayName("findByIdempotencyKey returns empty on DB miss")
    void findByIdempotencyKey_dbMiss() {
        OutboxEventRepository repo = withEm(em);
        when(em.createQuery(anyString(), eq(OutboxEventEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        assertTrue(repo.findByIdempotencyKey("k4").isEmpty());
    }

    @Test
    @DisplayName("findPending queries ordered pending events")
    void findPending_queries() {
        OutboxEventRepository repo = withEm(em);
        when(em.createQuery(anyString(), eq(OutboxEventEntity.class))).thenReturn(query);
        when(query.setParameter(eq("s"), eq(OutboxStatus.PENDING))).thenReturn(query);
        when(query.setMaxResults(50)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(event("k5")));
        assertEquals(1, repo.findPending(50).size());
    }

    @Test
    @DisplayName("markSent updates status when found")
    void markSent_found_updates() {
        OutboxEventRepository repo = withEm(em);
        UUID id = UUID.randomUUID();
        OutboxEventEntity e = event("k6");
        when(em.find(OutboxEventEntity.class, id)).thenReturn(e);
        repo.markSent(id);
        assertEquals(OutboxStatus.SENT, e.status);
        verify(em).merge(e);
    }

    @Test
    @DisplayName("markSent ignores missing event")
    void markSent_missing_noOp() {
        OutboxEventRepository repo = withEm(em);
        UUID id = UUID.randomUUID();
        when(em.find(OutboxEventEntity.class, id)).thenReturn(null);
        assertDoesNotThrow(() -> repo.markSent(id));
        verify(em, never()).merge(any());
    }
}
