package com.poc.gateway.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.entity.PaymentEntity;
import com.poc.gateway.entity.PaymentMetadataEntity;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.jboss.logging.Logger;

@ApplicationScoped
public class PaymentPersistenceMapper {

    private static final Logger LOG = Logger.getLogger(PaymentPersistenceMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PaymentEntity toEntity(Payment p) {
        if (p == null) {
            return null;
        }
        PaymentEntity e = new PaymentEntity();
        e.id = p.id() != null ? p.id() : UUID.randomUUID();
        e.amount = p.amount();
        e.currency = p.currency();
        e.customerId = p.customerId();
        e.paymentMethod = p.paymentMethod();
        e.country = p.country();
        e.status = p.status();
        e.provider = p.provider();
        e.failureReason = p.failureReason();
        e.createdAt = p.createdAt() != null ? p.createdAt() : LocalDateTime.now();
        e.updatedAt = p.updatedAt() != null ? p.updatedAt() : LocalDateTime.now();

        if (p.metadata() != null && !p.metadata().isEmpty()) {
            PaymentMetadataEntity m = toMetadataEntity(e.id, p.metadata());
            m.payment = e;
            e.metadata = m;
        }

        return e;
    }

    public Payment toDomain(PaymentEntity e) {
        if (e == null) {
            return null;
        }
        Map<String, Object> metadataMap = e.metadata != null ? toMetadataMap(e.metadata) : Map.of();
        return new Payment(
            e.id,
            e.amount,
            e.currency,
            e.customerId,
            e.paymentMethod,
            e.country,
            e.status,
            e.provider,
            e.failureReason,
            metadataMap,
            e.createdAt,
            e.updatedAt
        );
    }

    public PaymentMetadataEntity toMetadataEntity(UUID paymentId, Map<String, Object> metadata) {
        PaymentMetadata domainMetadata = PaymentMetadata.fromMap(metadata);
        PaymentMetadataEntity entity = new PaymentMetadataEntity();
        entity.paymentId = paymentId;
        entity.orderId = domainMetadata.orderId();
        entity.attempts = domainMetadata.attempts();
        entity.isNewPaymentMethod = domainMetadata.isNewPaymentMethod();
        entity.paymentMethodAgeDays = domainMetadata.paymentMethodAgeDays();
        entity.customerRiskTier = domainMetadata.customerRiskTier();
        if (domainMetadata.additionalProperties() != null && !domainMetadata.additionalProperties().isEmpty()) {
            try {
                entity.additionalProperties = objectMapper.writeValueAsString(domainMetadata.additionalProperties());
            } catch (JsonProcessingException ex) {
                LOG.warnf(ex, "Failed to serialize additionalProperties for payment %s", paymentId);
            }
        }
        return entity;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> toMetadataMap(PaymentMetadataEntity entity) {
        Map<String, Object> additionalProps = Map.of();
        if (entity.additionalProperties != null && !entity.additionalProperties.isBlank()) {
            try {
                additionalProps = objectMapper.readValue(entity.additionalProperties, Map.class);
            } catch (JsonProcessingException ex) {
                LOG.warnf(ex, "Failed to deserialize additionalProperties for payment %s", entity.paymentId);
            }
        }
        PaymentMetadata domain = new PaymentMetadata(
            entity.orderId,
            entity.attempts,
            entity.isNewPaymentMethod,
            entity.paymentMethodAgeDays,
            entity.customerRiskTier,
            additionalProps
        );
        return domain.toMap();
    }
}
