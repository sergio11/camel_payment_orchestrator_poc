package com.poc.gateway.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.gateway.entity.Payment;
import com.poc.gateway.entity.PaymentEntity;
import com.poc.gateway.entity.PaymentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

@ApplicationScoped
public class PaymentRepository {

    private static final Logger LOG = Logger.getLogger(PaymentRepository.class);
    private final ConcurrentHashMap<UUID, Payment> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> keyIndex = new ConcurrentHashMap<>();

    @Inject
    EntityManager em;

    @Inject
    ObjectMapper objectMapper;

    private boolean useDb() {
        return em != null;
    }

    @Transactional
    public Payment save(Payment payment) {
        return save(payment, null);
    }

    @Transactional
    public Payment save(Payment payment, String idempotencyKey) {
        if (!useDb()) {
            return saveInMemory(payment, idempotencyKey);
        }
        PaymentEntity e = toEntity(payment);
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
        em.merge(e);
        em.flush();
        return toDomain(e);
    }

    public Optional<Payment> findByIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        if (!useDb()) {
            UUID id = keyIndex.get(key);
            if (id == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(store.get(id));
        }
        List<PaymentEntity> list = em.createQuery(
                "FROM PaymentEntity WHERE idempotencyKey = :k", PaymentEntity.class)
            .setParameter("k", key)
            .setMaxResults(1)
            .getResultList();
        return list.isEmpty() ? Optional.empty() : Optional.of(toDomain(list.get(0)));
    }

    public Optional<Payment> findById(UUID id) {
        if (!useDb()) {
            return Optional.ofNullable(store.get(id));
        }
        PaymentEntity e = em.find(PaymentEntity.class, id);
        return Optional.ofNullable(e).map(this::toDomain);
    }

    public List<Payment> findAll(String customerId, PaymentStatus status, int limit, int offset) {
        if (!useDb()) {
            return findAllInMemory(customerId, status, limit, offset);
        }
        StringBuilder jpql = new StringBuilder("FROM PaymentEntity WHERE 1=1");
        if (customerId != null) {
            jpql.append(" AND customerId = :customerId");
        }
        if (status != null) {
            jpql.append(" AND status = :status");
        }
        jpql.append(" ORDER BY createdAt ASC, id ASC");
        var q = em.createQuery(jpql.toString(), PaymentEntity.class);
        if (customerId != null) {
            q.setParameter("customerId", customerId);
        }
        if (status != null) {
            q.setParameter("status", status);
        }
        q.setFirstResult(Math.max(offset, 0));
        q.setMaxResults(Math.max(limit, 1));
        return q.getResultList().stream().map(this::toDomain).toList();
    }

    public long count(String customerId, PaymentStatus status) {
        if (!useDb()) {
            return store.values().stream()
                .filter(p -> customerId == null || p.customerId().equals(customerId))
                .filter(p -> status == null || p.status() == status)
                .count();
        }
        StringBuilder jpql = new StringBuilder("SELECT COUNT(e) FROM PaymentEntity e WHERE 1=1");
        if (customerId != null) {
            jpql.append(" AND e.customerId = :customerId");
        }
        if (status != null) {
            jpql.append(" AND e.status = :status");
        }
        var q = em.createQuery(jpql.toString(), Long.class);
        if (customerId != null) {
            q.setParameter("customerId", customerId);
        }
        if (status != null) {
            q.setParameter("status", status);
        }
        return q.getSingleResult();
    }

    @Transactional
    public Payment update(UUID id, PaymentStatus newStatus) {
        if (!useDb()) {
            Payment updated = store.compute(id, (key, existing) -> {
                if (existing == null) {
                    return null;
                }
                return existing.withStatus(newStatus);
            });
            if (updated == null) {
                throw new com.poc.gateway.exception.PaymentNotFoundException(id.toString());
            }
            return updated;
        }
        PaymentEntity e = em.find(PaymentEntity.class, id);
        if (e == null) {
            throw new com.poc.gateway.exception.PaymentNotFoundException(id.toString());
        }
        e.status = newStatus;
        e.updatedAt = LocalDateTime.now();
        em.merge(e);
        em.flush();
        return toDomain(e);
    }

    @Transactional
    public Optional<Payment> updateIfPending(UUID id, PaymentStatus newStatus) {
        if (!useDb()) {
            Payment[] holder = new Payment[1];
            store.compute(id, (key, existing) -> {
                if (existing == null || existing.status() != PaymentStatus.PENDING) {
                    holder[0] = existing;
                    return existing;
                }
                Payment updated = existing.withStatus(newStatus);
                holder[0] = updated;
                return updated;
            });
            Payment result = holder[0];
            if (result == null || result.status() != newStatus) {
                return Optional.empty();
            }
            return Optional.of(result);
        }
        PaymentEntity e = em.find(PaymentEntity.class, id);
        if (e == null || e.status != PaymentStatus.PENDING) {
            return Optional.empty();
        }
        e.status = newStatus;
        e.updatedAt = LocalDateTime.now();
        try {
            em.merge(e);
            em.flush();
        } catch (jakarta.persistence.OptimisticLockException ex) {
            LOG.warnf("Optimistic lock conflict updating payment %s, treating as already transitioned", id);
            return Optional.empty();
        }
        return Optional.of(toDomain(e));
    }

    @Transactional
    public void deleteById(UUID id) {        if (!useDb()) {
            store.remove(id);
            return;
        }
        PaymentEntity e = em.find(PaymentEntity.class, id);
        if (e != null) {
            em.remove(e);
        }
    }

    private Payment saveInMemory(Payment payment, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            UUID existingId = keyIndex.get(idempotencyKey);
            if (existingId != null) {
                Payment existing = store.get(existingId);
                if (existing != null) {
                    return existing;
                }
            }
        }
        UUID id = payment.id() != null ? payment.id() : UUID.randomUUID();
        LOG.debugf("Saving payment (in-memory fallback): %s", id);
        Payment saved = new Payment(
            id,
            payment.amount(),
            payment.currency(),
            payment.customerId(),
            payment.paymentMethod(),
            payment.country(),
            payment.status() != null ? payment.status() : PaymentStatus.PENDING,
            payment.provider(),
            payment.failureReason(),
            payment.metadata(),
            payment.createdAt() != null ? payment.createdAt() : LocalDateTime.now(),
            LocalDateTime.now()
        );
        store.put(saved.id(), saved);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            keyIndex.putIfAbsent(idempotencyKey, saved.id());
        }
        return saved;
    }

    private List<Payment> findAllInMemory(String customerId, PaymentStatus status, int limit, int offset) {
        return store.values().stream()
            .filter(p -> customerId == null || p.customerId().equals(customerId))
            .filter(p -> status == null || p.status() == status)
            .sorted(java.util.Comparator.comparing(Payment::createdAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                .thenComparing(Payment::id))
            .skip(offset)
            .limit(limit)
            .toList();
    }

    PaymentEntity toEntity(Payment p) {
        PaymentEntity e = new PaymentEntity();
        e.id = p.id();
        e.amount = p.amount();
        e.currency = p.currency();
        e.customerId = p.customerId();
        e.paymentMethod = p.paymentMethod();
        e.country = p.country();
        e.status = p.status();
        e.provider = p.provider();
        e.failureReason = p.failureReason();
        e.metadataJson = toJson(p.metadata());
        e.createdAt = p.createdAt();
        e.updatedAt = p.updatedAt();
        return e;
    }

    Payment toDomain(PaymentEntity e) {
        return new Payment(
            e.id, e.amount, e.currency, e.customerId, e.paymentMethod,
            e.country, e.status, e.provider, e.failureReason,
            fromJson(e.metadataJson), e.createdAt, e.updatedAt
        );
    }

    String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            ObjectMapper om = objectMapper != null ? objectMapper : new ObjectMapper();
            return om.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize payment metadata", ex);
        }
    }

    Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            ObjectMapper om = objectMapper != null ? objectMapper : new ObjectMapper();
            return om.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException ex) {
            Map<String, Object> raw = new HashMap<>();
            raw.put("raw", json);
            return raw;
        }
    }
}
