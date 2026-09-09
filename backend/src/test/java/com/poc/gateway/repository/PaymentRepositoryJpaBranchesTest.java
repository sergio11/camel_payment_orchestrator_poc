package com.poc.gateway.repository;

import com.poc.gateway.entity.PaymentEntity;
import com.poc.gateway.entity.PaymentStatus;
import com.poc.gateway.mapper.PaymentPersistenceMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentRepositoryJpaBranchesTest {

    @Mock
    EntityManager em;

    @Mock
    PaymentPersistenceMapper persistenceMapper;

    @InjectMocks
    PaymentRepositoryJpa repository;

    @Test
    @DisplayName("findByIdempotencyKey(null) returns empty without querying")
    void findByIdempotencyKey_null_returnsEmpty() {
        Optional<?> result = repository.findByIdempotencyKey(null);
        assertTrue(result.isEmpty());
        verify(em, never()).createQuery(anyString(), any(Class.class));
    }

    @Test
    @DisplayName("findByIdempotencyKey(blank) returns empty without querying")
    void findByIdempotencyKey_blank_returnsEmpty() {
        Optional<?> result = repository.findByIdempotencyKey("   ");
        assertTrue(result.isEmpty());
        verify(em, never()).createQuery(anyString(), any(Class.class));
    }

    @Test
    @DisplayName("findById(null) returns empty without querying")
    void findById_null_returnsEmpty() {
        Optional<?> result = repository.findById(null);
        assertTrue(result.isEmpty());
        verify(em, never()).find(any(), any());
    }

    @Test
    @DisplayName("findAll with null customerId and null status uses no filters")
    void findAll_noFilters() {
        TypedQuery<PaymentEntity> query = mock(TypedQuery.class);
        when(em.createQuery(contains("FROM PaymentEntity"), eq(PaymentEntity.class))).thenReturn(query);
        when(query.setFirstResult(anyInt())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        repository.findAll(null, null, 10, 0);

        verify(query, never()).setParameter(eq("customerId"), any());
        verify(query, never()).setParameter(eq("status"), any());
    }

    @Test
    @DisplayName("count with null customerId and null status uses no filters")
    void count_noFilters() {
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createQuery(contains("SELECT COUNT"), eq(Long.class))).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L);

        long result = repository.count(null, null);

        verify(query, never()).setParameter(eq("customerId"), any());
        verify(query, never()).setParameter(eq("status"), any());
        assertEquals(0L, result);
    }
}
