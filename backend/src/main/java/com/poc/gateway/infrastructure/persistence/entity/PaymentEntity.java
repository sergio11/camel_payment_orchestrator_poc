package com.poc.gateway.infrastructure.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import com.poc.gateway.domain.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payments_customer", columnList = "customerId"),
    @Index(name = "idx_payments_status", columnList = "status")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uq_payments_idempotency", columnNames = "idempotencyKey")
})
public class PaymentEntity extends PanacheEntityBase {

    @Id
    @Column(nullable = false, updatable = false)
    public UUID id;

    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal amount;

    @Column(nullable = false, length = 3)
    public String currency;

    @Column(nullable = false)
    public String customerId;

    public String paymentMethod;

    @Column(length = 2)
    public String country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public PaymentStatus status;

    public String provider;

    @Column(length = 1024)
    public String failureReason;

    @jakarta.persistence.OneToOne(mappedBy = "payment", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.EAGER)
    public PaymentMetadataEntity metadata;

    @Column(nullable = false)
    public LocalDateTime createdAt;

    @Column(nullable = false)
    public LocalDateTime updatedAt;

    @Column(length = 36, unique = true)
    public String idempotencyKey;

    @Version
    public long version;
}
