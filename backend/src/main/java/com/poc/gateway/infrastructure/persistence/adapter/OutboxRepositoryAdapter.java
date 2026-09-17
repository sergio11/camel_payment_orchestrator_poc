package com.poc.gateway.infrastructure.persistence.adapter;

import com.poc.gateway.domain.model.OutboxEvent;
import com.poc.gateway.domain.model.OutboxStatus;
import com.poc.gateway.domain.port.outbound.OutboxRepositoryPort;
import com.poc.gateway.infrastructure.persistence.entity.OutboxEventEntity;
import com.poc.gateway.infrastructure.persistence.mapper.OutboxEventMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class OutboxRepositoryAdapter implements OutboxRepositoryPort {

    @Inject
    private EntityManager em;

    @Inject
    private OutboxEventMapper mapper;

    @Override
    @Transactional
    public void persist(OutboxEvent outboxEvent) {
        OutboxEventEntity entity = mapper.toEntity(outboxEvent);
        em.persist(entity);
        em.flush();
    }

    @Override
    public Optional<OutboxEvent> findByIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        List<OutboxEventEntity> list = em.createQuery(
                "FROM OutboxEventEntity WHERE idempotencyKey = :k", OutboxEventEntity.class)
            .setParameter("k", key)
            .setMaxResults(1)
            .getResultList();
        return list.isEmpty() ? Optional.empty() : Optional.of(mapper.toDomain(list.get(0)));
    }

    @Override
    public List<OutboxEvent> findPending(int limit) {
        List<OutboxEventEntity> entities = em.createQuery(
                "FROM OutboxEventEntity WHERE status = :s ORDER BY createdAt ASC", OutboxEventEntity.class)
            .setParameter("s", OutboxStatus.PENDING)
            .setMaxResults(limit)
            .getResultList();
        return entities.stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional
    public void markSent(UUID eventId) {
        OutboxEventEntity e = em.find(OutboxEventEntity.class, eventId);
        if (e != null) {
            e.status = OutboxStatus.SENT;
            em.merge(e);
        }
    }
}
