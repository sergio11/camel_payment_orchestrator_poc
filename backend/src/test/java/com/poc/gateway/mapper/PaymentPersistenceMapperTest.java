package com.poc.gateway.mapper;

import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.entity.PaymentEntity;
import com.poc.gateway.entity.PaymentMetadataEntity;
import com.poc.gateway.entity.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentPersistenceMapperTest {

    private PaymentPersistenceMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PaymentPersistenceMapper();
    }

    @Test
    @DisplayName("toEntity maps domain payment to entity with 1:1 metadata")
    void testToEntityWithMetadata() {
        UUID id = UUID.randomUUID();
        Payment payment = new Payment(
            id,
            new BigDecimal("100.00"),
            "EUR",
            "cust-1",
            "CARD",
            "ES",
            PaymentStatus.PENDING,
            "stripe",
            null,
            Map.of("orderId", "ord-123", "attempts", 2, "customerRiskTier", "LOW"),
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        PaymentEntity entity = mapper.toEntity(payment);

        assertNotNull(entity);
        assertEquals(id, entity.id);
        assertEquals(new BigDecimal("100.00"), entity.amount);
        assertEquals("EUR", entity.currency);
        assertEquals("cust-1", entity.customerId);
        assertEquals("CARD", entity.paymentMethod);
        assertEquals("ES", entity.country);
        assertEquals(PaymentStatus.PENDING, entity.status);
        assertNotNull(entity.metadata);
        assertEquals("ord-123", entity.metadata.orderId);
        assertEquals(2, entity.metadata.attempts);
        assertEquals("LOW", entity.metadata.customerRiskTier);
    }

    @Test
    @DisplayName("toDomain maps JPA entity to domain payment")
    void testToDomain() {
        UUID id = UUID.randomUUID();
        PaymentEntity entity = new PaymentEntity();
        entity.id = id;
        entity.amount = new BigDecimal("50.00");
        entity.currency = "USD";
        entity.customerId = "cust-2";
        entity.status = PaymentStatus.APPROVED;
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = LocalDateTime.now();

        PaymentMetadataEntity meta = new PaymentMetadataEntity();
        meta.paymentId = id;
        meta.orderId = "ord-999";
        meta.attempts = 1;
        meta.customerRiskTier = "HIGH";
        entity.metadata = meta;

        Payment domain = mapper.toDomain(entity);

        assertNotNull(domain);
        assertEquals(id, domain.id());
        assertEquals(PaymentStatus.APPROVED, domain.status());
        assertEquals("ord-999", domain.metadata().get("orderId"));
        assertEquals(1, domain.metadata().get("attempts"));
        assertEquals("HIGH", domain.metadata().get("customerRiskTier"));
    }

    @Test
    @DisplayName("toEntity and toDomain handle null gracefully")
    void testNullHandling() {
        assertNull(mapper.toEntity(null));
        assertNull(mapper.toDomain(null));
    }

    @Test
    @DisplayName("toEntity with empty metadata map creates entity without metadata")
    void testToEntityWithEmptyMetadata() {
        Payment payment = new Payment(
            UUID.randomUUID(),
            new BigDecimal("10.00"),
            "USD",
            "cust-1",
            "CARD",
            "US",
            PaymentStatus.PENDING,
            null,
            null,
            Map.of(),
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        PaymentEntity entity = mapper.toEntity(payment);

        assertNotNull(entity);
        assertNull(entity.metadata);
    }

    @Test
    @DisplayName("toDomain with entity without metadata returns empty metadata map")
    void testToDomainWithoutMetadata() {
        PaymentEntity entity = new PaymentEntity();
        entity.id = UUID.randomUUID();
        entity.amount = new BigDecimal("10.00");
        entity.currency = "USD";
        entity.customerId = "cust-1";
        entity.status = PaymentStatus.PENDING;
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = LocalDateTime.now();
        entity.metadata = null;

        Payment domain = mapper.toDomain(entity);

        assertNotNull(domain);
        assertNotNull(domain.metadata());
        assertTrue(domain.metadata().isEmpty());
    }

    @Test
    @DisplayName("toEntity with metadata without additional properties creates entity without additionalProperties JSON")
    void testToEntityMetadataWithoutAdditional() {
        UUID id = UUID.randomUUID();
        Payment payment = new Payment(
            id,
            new BigDecimal("50.00"),
            "USD",
            "cust-1",
            "CARD",
            "US",
            PaymentStatus.PENDING,
            null,
            null,
            Map.of("orderId", "ord-1", "attempts", 1),
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        PaymentEntity entity = mapper.toEntity(payment);

        assertNotNull(entity);
        assertNotNull(entity.metadata);
        assertEquals("ord-1", entity.metadata.orderId);
        assertEquals(1, entity.metadata.attempts);
        assertNull(entity.metadata.additionalProperties);
    }

    @Test
    @DisplayName("toEntity with null id and timestamps generates defaults")
    void testToEntityNullIdAndTimestamps() {
        Payment payment = new Payment(
            null,
            new BigDecimal("25.00"),
            "USD",
            "cust-1",
            "CARD",
            "US",
            PaymentStatus.PENDING,
            null,
            null,
            null,
            null,
            null
        );

        PaymentEntity entity = mapper.toEntity(payment);

        assertNotNull(entity);
        assertNotNull(entity.id);
        assertNotNull(entity.createdAt);
        assertNotNull(entity.updatedAt);
        assertEquals(PaymentStatus.PENDING, entity.status);
    }

    @Test
    @DisplayName("toEntity with extra metadata keys serializes additionalProperties to JSON")
    void testToEntityWithExtraMetadataKeys() {
        UUID id = UUID.randomUUID();
        Payment payment = new Payment(
            id,
            new BigDecimal("50.00"),
            "USD",
            "cust-1",
            "CARD",
            "US",
            PaymentStatus.PENDING,
            null,
            null,
            Map.of("orderId", "ord-1", "customField", "customValue", "anotherKey", 42),
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        PaymentEntity entity = mapper.toEntity(payment);

        assertNotNull(entity);
        assertNotNull(entity.metadata);
        assertEquals("ord-1", entity.metadata.orderId);
        assertNotNull(entity.metadata.additionalProperties);
        assertTrue(entity.metadata.additionalProperties.contains("customField"));
        assertTrue(entity.metadata.additionalProperties.contains("customValue"));
    }

    @Test
    @DisplayName("toDomain with metadata entity containing additionalProperties JSON deserializes correctly")
    void testToDomainWithAdditionalPropertiesJson() {
        UUID id = UUID.randomUUID();
        PaymentEntity entity = new PaymentEntity();
        entity.id = id;
        entity.amount = new BigDecimal("50.00");
        entity.currency = "USD";
        entity.customerId = "cust-1";
        entity.status = PaymentStatus.PENDING;
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = LocalDateTime.now();

        PaymentMetadataEntity meta = new PaymentMetadataEntity();
        meta.paymentId = id;
        meta.orderId = "ord-1";
        meta.additionalProperties = "{\"custom\":\"value\"}";
        entity.metadata = meta;

        Payment domain = mapper.toDomain(entity);

        assertNotNull(domain);
        assertEquals("ord-1", domain.metadata().get("orderId"));
        assertEquals("value", domain.metadata().get("custom"));
    }

    @Test
    @DisplayName("toEntity with empty additionalProperties does not serialize extra JSON")
    void testToEntity_emptyAdditionalProperties() {
        UUID id = UUID.randomUUID();
        Payment payment = new Payment(
            id,
            new BigDecimal("50.00"),
            "USD",
            "cust-empty",
            "CARD",
            "US",
            PaymentStatus.PENDING,
            null,
            null,
            Map.of("orderId", "ord-test"),
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        PaymentEntity entity = mapper.toEntity(payment);

        assertNotNull(entity);
        assertNotNull(entity.metadata);
        assertNull(entity.metadata.additionalProperties);
    }
}
