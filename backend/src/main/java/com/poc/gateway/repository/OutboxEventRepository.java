package com.poc.gateway.repository;

import com.poc.gateway.entity.OutboxEventEntity;
import com.poc.gateway.entity.OutboxStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class OutboxEventRepository {

    @Inject
    public EntityManager em;

    private final ConcurrentHashMap<String, OutboxEventEntity> memByKey = new ConcurrentHashMap<>();

    public boolean isDbAvailable() {
        return em != null;
    }

    @Transactional
    public void persist(OutboxEventEntity event) {
        if (em == null) {
            if (event.idempotencyKey != null) {
                memByKey.putIfAbsent(event.idempotencyKey, event);
            }
            return;
        }
        em.persist(event);
        em.flush();
    }

    public Optional<OutboxEventEntity> findByIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        OutboxEventEntity mem = memByKey.get(key);
        if (mem != null) {
            return Optional.of(mem);
        }
        if (em == null) {
            return Optional.empty();
        }
        List<OutboxEventEntity> list = em.createQuery(
                "FROM OutboxEventEntity WHERE idempotencyKey = :k", OutboxEventEntity.class)
            .setParameter("k", key)
            .setMaxResults(1)
            .getResultList();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<OutboxEventEntity> findPending(int limit) {
        return em.createQuery("FROM OutboxEventEntity WHERE status = :s ORDER BY createdAt ASC", OutboxEventEntity.class)
            .setParameter("s", OutboxStatus.PENDING)
            .setMaxResults(limit)
            .getResultList();
    }

    @Transactional
    public void markSent(UUID id) {
        OutboxEventEntity e = em.find(OutboxEventEntity.class, id);
        if (e != null) {
            e.status = OutboxStatus.SENT;
            em.merge(e);
        }
    }
}
