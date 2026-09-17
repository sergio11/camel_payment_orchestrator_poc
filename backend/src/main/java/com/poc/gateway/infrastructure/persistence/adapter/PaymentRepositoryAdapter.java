package com.poc.gateway.infrastructure.persistence.adapter;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.model.PaymentStatus;
import com.poc.gateway.domain.port.outbound.PaymentRepositoryPort;
import com.poc.gateway.domain.exception.PaymentNotFoundException;
import com.poc.gateway.infrastructure.persistence.entity.PaymentEntity;
import com.poc.gateway.infrastructure.persistence.mapper.PaymentPersistenceMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jboss.logging.Logger;

@ApplicationScoped
public class PaymentRepositoryAdapter implements PaymentRepositoryPort {

    private static final Logger LOG = Logger.getLogger(PaymentRepositoryAdapter.class);

    @Inject
    private EntityManager em;

    @Inject
    private PaymentPersistenceMapper persistenceMapper;

    @Override
    @Transactional
    public Payment save(Payment payment) {
        return save(payment, null);
    }

    @Override
    @Transactional
    public Payment save(Payment payment, String idempotencyKey) {
        PaymentEntity e = persistenceMapper.toEntity(payment);
        if (e.id == null) {
            e.id = UUID.randomUUID();
        }
        if (e.status == null) {
            e.status = PaymentStatus.PENDING;
        }
        if (e.createdAt == null) {
            e.createdAt = LocalDateTime.now();
        }
        e.updatedAt = LocalDateTime.now();
        e.idempotencyKey = idempotencyKey;

        PaymentEntity merged = em.merge(e);
        em.flush();
        return persistenceMapper.toDomain(merged);
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        List<PaymentEntity> list = em.createQuery(
                "FROM PaymentEntity WHERE idempotencyKey = :k", PaymentEntity.class)
            .setParameter("k", key)
            .setMaxResults(1)
            .getResultList();
        return list.isEmpty() ? Optional.empty() : Optional.of(persistenceMapper.toDomain(list.get(0)));
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        PaymentEntity e = em.find(PaymentEntity.class, id);
        return Optional.ofNullable(e).map(persistenceMapper::toDomain);
    }

    @Override
    public List<Payment> findAll(String customerId, PaymentStatus status, int limit, int offset) {
        TypedQuery<PaymentEntity> q = em.createQuery(buildFindQuery(customerId, status), PaymentEntity.class);
        bindCommonParams(q, customerId, status);
        q.setFirstResult(Math.max(offset, 0));
        q.setMaxResults(Math.max(limit, 1));
        return q.getResultList().stream().map(persistenceMapper::toDomain).toList();
    }

    @Override
    public long count(String customerId, PaymentStatus status) {
        TypedQuery<Long> q = em.createQuery(buildCountQuery(customerId, status), Long.class);
        bindCommonParams(q, customerId, status);
        return q.getSingleResult();
    }

    @Override
    @Transactional
    public Payment update(UUID id, PaymentStatus newStatus) {
        PaymentEntity e = em.find(PaymentEntity.class, id);
        if (e == null) {
            throw new PaymentNotFoundException(id.toString());
        }
        e.status = newStatus;
        e.updatedAt = LocalDateTime.now();
        PaymentEntity merged = em.merge(e);
        em.flush();
        return persistenceMapper.toDomain(merged);
    }

    @Override
    @Transactional
    public Optional<Payment> updateIfPending(UUID id, PaymentStatus newStatus) {
        PaymentEntity e = em.find(PaymentEntity.class, id);
        if (e == null || e.status != PaymentStatus.PENDING) {
            return Optional.empty();
        }
        e.status = newStatus;
        e.updatedAt = LocalDateTime.now();
        try {
            PaymentEntity merged = em.merge(e);
            em.flush();
            return Optional.of(persistenceMapper.toDomain(merged));
        } catch (OptimisticLockException ex) {
            LOG.warnf("Optimistic lock conflict updating payment %s, treating as already transitioned", id);
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        PaymentEntity e = em.find(PaymentEntity.class, id);
        if (e != null) {
            em.remove(e);
        }
    }

    private String buildFindQuery(String customerId, PaymentStatus status) {
        StringBuilder jpql = new StringBuilder("FROM PaymentEntity WHERE 1=1");
        if (customerId != null) {
            jpql.append(" AND customerId = :customerId");
        }
        if (status != null) {
            jpql.append(" AND status = :status");
        }
        jpql.append(" ORDER BY createdAt ASC, id ASC");
        return jpql.toString();
    }

    private String buildCountQuery(String customerId, PaymentStatus status) {
        StringBuilder jpql = new StringBuilder("SELECT COUNT(e) FROM PaymentEntity e WHERE 1=1");
        if (customerId != null) {
            jpql.append(" AND e.customerId = :customerId");
        }
        if (status != null) {
            jpql.append(" AND e.status = :status");
        }
        return jpql.toString();
    }

    private <T> void bindCommonParams(TypedQuery<T> q, String customerId, PaymentStatus status) {
        if (customerId != null) {
            q.setParameter("customerId", customerId);
        }
        if (status != null) {
            q.setParameter("status", status);
        }
    }
}
