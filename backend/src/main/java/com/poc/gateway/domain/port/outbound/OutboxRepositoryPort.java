package com.poc.gateway.domain.port.outbound;

import com.poc.gateway.domain.model.OutboxEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxRepositoryPort {
    void persist(OutboxEvent outboxEvent);
    Optional<OutboxEvent> findByIdempotencyKey(String key);
    List<OutboxEvent> findPending(int limit);
    void markSent(UUID eventId);
}
