package com.poc.gateway.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_event", indexes = {
    @Index(name = "idx_outbox_status", columnList = "status")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uq_outbox_idempotency", columnNames = "idempotencyKey")
})
public class OutboxEventEntity extends PanacheEntityBase {

    @Id
    @Column(nullable = false, updatable = false)
    public UUID id;

    @Column(nullable = false)
    public UUID aggregateId;

    @Column(nullable = false, length = 100)
    public String type;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public OutboxStatus status;

    @Column(nullable = false)
    public LocalDateTime createdAt;

    @Column(length = 36, unique = true)
    public String idempotencyKey;
}
