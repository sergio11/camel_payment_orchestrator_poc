package com.poc.gateway.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record OutboxEvent(
    UUID id,
    UUID aggregateId,
    String type,
    String payload,
    OutboxStatus status,
    LocalDateTime createdAt,
    String idempotencyKey
) {
    public static OutboxEvent create(UUID aggregateId, String type, String payload, String idempotencyKey) {
        return new OutboxEvent(
            UUID.randomUUID(),
            aggregateId,
            type,
            payload,
            OutboxStatus.PENDING,
            LocalDateTime.now(),
            idempotencyKey
        );
    }

    public OutboxEvent withSent() {
        return new OutboxEvent(id, aggregateId, type, payload, OutboxStatus.SENT, createdAt, idempotencyKey);
    }
}
