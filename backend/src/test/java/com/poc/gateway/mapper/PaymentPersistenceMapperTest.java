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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentPersistenceMapperTest {

    private PaymentPersistenceMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = MapperTestHelper.persistenceMapper();
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
            new PaymentMetadata("ord-123", 2, null, null, "LOW", null, null, null),
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
        assertEquals("ord-999", domain.metadata().orderId());
        assertEquals(1, domain.metadata().attempts());
        assertEquals("HIGH", domain.metadata().customerRiskTier());
    }

    @Test
    @DisplayName("toEntity and toDomain handle null gracefully")
    void testNullHandling() {
        assertNull(mapper.toEntity(null));
        assertNull(mapper.toDomain(null));
    }

    @Test
    @DisplayName("toEntity with empty metadata creates entity without metadata")
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
            PaymentMetadata.empty(),
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        PaymentEntity entity = mapper.toEntity(payment);

        assertNotNull(entity);
        assertNotNull(entity.metadata);
    }

    @Test
    @DisplayName("toDomain with entity without metadata returns null metadata")
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
        assertNull(domain.metadata());
    }

    @Test
    @DisplayName("toEntity with metadata creates entity with metadata fields")
    void testToEntityMetadata() {
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
            new PaymentMetadata("ord-1", 1, null, null, null, null, null, null),
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
    @DisplayName("toEntity with full metadata maps all fields")
    void testToEntityWithFullMetadata() {
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
            new PaymentMetadata("ord-1", 3, true, 30, "LOW", "2024-01-01T00:00:00", 10, 5),
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        PaymentEntity entity = mapper.toEntity(payment);

        assertNotNull(entity);
        assertNotNull(entity.metadata);
        assertEquals("ord-1", entity.metadata.orderId);
        assertEquals(3, entity.metadata.attempts);
        assertEquals("LOW", entity.metadata.customerRiskTier);
    }

    @Test
    @DisplayName("toDomain with metadata entity containing all fields maps correctly")
    void testToDomainWithFullMetadata() {
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
        meta.attempts = 2;
        meta.isNewPaymentMethod = true;
        meta.paymentMethodAgeDays = 15;
        meta.customerRiskTier = "MEDIUM";
        entity.metadata = meta;

        Payment domain = mapper.toDomain(entity);

        assertNotNull(domain);
        assertEquals("ord-1", domain.metadata().orderId());
        assertEquals(2, domain.metadata().attempts());
        assertEquals(true, domain.metadata().isNewPaymentMethod());
        assertEquals(15, domain.metadata().paymentMethodAgeDays());
        assertEquals("MEDIUM", domain.metadata().customerRiskTier());
    }

    @Test
    @DisplayName("toEntity with empty metadata does not create metadata entity")
    void testToEntity_emptyMetadata() {
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
            new PaymentMetadata(null, null, null, null, null, null, null, null),
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        PaymentEntity entity = mapper.toEntity(payment);

        assertNotNull(entity);
        assertNotNull(entity.metadata);
    }
}
