package com.poc.gateway.repository;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.entity.PaymentStatus;
import com.poc.gateway.exception.PaymentNotFoundException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PaymentRepositoryInMemory implements PaymentRepository {

    private final ConcurrentHashMap<UUID, Payment> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> keyIndex = new ConcurrentHashMap<>();

    @Override
    public Payment save(Payment payment) {
        return save(payment, null);
    }

    @Override
    public Payment save(Payment payment, String idempotencyKey) {
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

    @Override
    public Optional<Payment> findByIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        UUID id = keyIndex.get(key);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Payment> findAll(String customerId, PaymentStatus status, int limit, int offset) {
        return store.values().stream()
            .filter(p -> customerId == null || p.customerId().equals(customerId))
            .filter(p -> status == null || p.status() == status)
            .sorted(Comparator.comparing(Payment::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Payment::id))
            .skip(Math.max(offset, 0))
            .limit(Math.max(limit, 1))
            .toList();
    }

    @Override
    public long count(String customerId, PaymentStatus status) {
        return store.values().stream()
            .filter(p -> customerId == null || p.customerId().equals(customerId))
            .filter(p -> status == null || p.status() == status)
            .count();
    }

    @Override
    public Payment update(UUID id, PaymentStatus newStatus) {
        Payment updated = store.compute(id, (key, existing) -> {
            if (existing == null) {
                return null;
            }
            return existing.withStatus(newStatus);
        });
        if (updated == null) {
            throw new PaymentNotFoundException(id.toString());
        }
        return updated;
    }

    @Override
    public Optional<Payment> updateIfPending(UUID id, PaymentStatus newStatus) {
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

    @Override
    public void deleteById(UUID id) {
        store.remove(id);
    }
}
