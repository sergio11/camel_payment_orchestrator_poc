package com.poc.gateway.infrastructure.persistence.adapter;

import com.poc.gateway.domain.model.OutboxEvent;
import com.poc.gateway.infrastructure.persistence.entity.OutboxEventEntity;
import com.poc.gateway.infrastructure.persistence.mapper.OutboxEventMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRepositoryAdapterTest {

    @Mock EntityManager em;
    @Mock OutboxEventMapper mapper;
    @InjectMocks OutboxRepositoryAdapter adapter;

    private OutboxEvent domainEvent;
    private OutboxEventEntity entityEvent;

    @BeforeEach
    void setUp() {
        domainEvent = OutboxEvent.create(
            UUID.randomUUID(), "PaymentReceived", "{\"test\":true}", "idem-1"
        );
        entityEvent = new OutboxEventEntity();
        entityEvent.id = domainEvent.id();
        entityEvent.aggregateId = domainEvent.aggregateId();
        entityEvent.type = domainEvent.type();
        entityEvent.payload = domainEvent.payload();
        entityEvent.status = com.poc.gateway.infrastructure.persistence.entity.OutboxStatus.PENDING;
        entityEvent.createdAt = domainEvent.createdAt();
        entityEvent.idempotencyKey = domainEvent.idempotencyKey();
    }

    @Test
    void persist_mapsAndPersists() {
        when(mapper.toEntity(domainEvent)).thenReturn(entityEvent);

        adapter.persist(domainEvent);

        verify(em).persist(entityEvent);
        verify(em).flush();
    }

    @SuppressWarnings("unchecked")
    @Test
    void findByIdempotencyKey_validKey_returnsEvent() {
        TypedQuery<OutboxEventEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(OutboxEventEntity.class))).thenReturn(query);
        when(query.setParameter(eq("k"), eq("idem-1"))).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(entityEvent));
        when(mapper.toDomain(entityEvent)).thenReturn(domainEvent);

        Optional<OutboxEvent> result = adapter.findByIdempotencyKey("idem-1");
        assertTrue(result.isPresent());
        assertEquals(domainEvent.id(), result.get().id());
    }

    @Test
    void findByIdempotencyKey_nullKey_returnsEmpty() {
        assertFalse(adapter.findByIdempotencyKey(null).isPresent());
    }

    @Test
    void findByIdempotencyKey_blankKey_returnsEmpty() {
        assertFalse(adapter.findByIdempotencyKey("  ").isPresent());
    }

    @SuppressWarnings("unchecked")
    @Test
    void findByIdempotencyKey_emptyResult_returnsEmpty() {
        TypedQuery<OutboxEventEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(OutboxEventEntity.class))).thenReturn(query);
        when(query.setParameter(eq("k"), eq("idem-1"))).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        assertFalse(adapter.findByIdempotencyKey("idem-1").isPresent());
    }

    @SuppressWarnings("unchecked")
    @Test
    void findPending_returnsPendingEvents() {
        TypedQuery<OutboxEventEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(OutboxEventEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.setMaxResults(10)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(entityEvent));
        when(mapper.toDomain(entityEvent)).thenReturn(domainEvent);

        List<OutboxEvent> result = adapter.findPending(10);
        assertEquals(1, result.size());
    }

    @Test
    void markSent_found_marksAsSent() {
        when(em.find(eq(OutboxEventEntity.class), eq(domainEvent.id()))).thenReturn(entityEvent);
        when(em.merge(entityEvent)).thenReturn(entityEvent);

        adapter.markSent(domainEvent.id());

        assertEquals(com.poc.gateway.infrastructure.persistence.entity.OutboxStatus.SENT, entityEvent.status);
        verify(em).merge(entityEvent);
    }

    @Test
    void markSent_notFound_doesNothing() {
        UUID randomId = UUID.randomUUID();
        when(em.find(eq(OutboxEventEntity.class), eq(randomId))).thenReturn(null);

        adapter.markSent(randomId);

        verify(em, never()).merge(any());
    }
}
