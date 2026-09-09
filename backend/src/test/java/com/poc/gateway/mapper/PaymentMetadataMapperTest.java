package com.poc.gateway.mapper;

import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.entity.PaymentMetadataEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentMetadataMapperTest {

    private PaymentMetadataMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(PaymentMetadataMapper.class);
    }

    @Test
    @DisplayName("toEntity maps all matching fields correctly")
    void toEntity_validDomain_mapsFields() {
        PaymentMetadata domain = new PaymentMetadata("ord-1", 2, true, 30, "LOW", null, null, null);

        PaymentMetadataEntity entity = mapper.toEntity(domain);

        assertNotNull(entity);
        assertEquals("ord-1", entity.orderId);
        assertEquals(2, entity.attempts);
        assertTrue(entity.isNewPaymentMethod);
        assertEquals(30, entity.paymentMethodAgeDays);
        assertEquals("LOW", entity.customerRiskTier);
    }

    @Test
    @DisplayName("toDomain maps all matching fields correctly")
    void toDomain_validEntity_mapsFields() {
        PaymentMetadataEntity entity = new PaymentMetadataEntity();
        entity.paymentId = UUID.randomUUID();
        entity.orderId = "ord-2";
        entity.attempts = 5;
        entity.isNewPaymentMethod = false;
        entity.paymentMethodAgeDays = 90;
        entity.customerRiskTier = "HIGH";

        PaymentMetadata domain = mapper.toDomain(entity);

        assertNotNull(domain);
        assertEquals("ord-2", domain.orderId());
        assertEquals(5, domain.attempts());
        assertFalse(domain.isNewPaymentMethod());
        assertEquals(90, domain.paymentMethodAgeDays());
        assertEquals("HIGH", domain.customerRiskTier());
    }

    @Test
    @DisplayName("toEntity with null domain returns null")
    void toEntity_nullDomain_returnsNull() {
        PaymentMetadataEntity entity = mapper.toEntity(null);
        assertNull(entity);
    }

    @Test
    @DisplayName("toDomain with null entity returns null")
    void toDomain_nullEntity_returnsNull() {
        PaymentMetadata domain = mapper.toDomain(null);
        assertNull(domain);
    }

    @Test
    @DisplayName("toDomain handles entity with null fields")
    void toDomain_nullFields_returnsDomainWithNulls() {
        PaymentMetadataEntity entity = new PaymentMetadataEntity();
        entity.paymentId = UUID.randomUUID();

        PaymentMetadata domain = mapper.toDomain(entity);

        assertNotNull(domain);
        assertNull(domain.orderId());
        assertNull(domain.attempts());
        assertNull(domain.isNewPaymentMethod());
        assertNull(domain.paymentMethodAgeDays());
        assertNull(domain.customerRiskTier());
        assertNull(domain.enrichedAt());
        assertNull(domain.velocityScore());
        assertNull(domain.geoRiskScore());
    }

    @Test
    @DisplayName("round-trip: domain to entity to domain preserves common fields")
    void roundTrip_domainToEntityToDomain_preservesFields() {
        PaymentMetadata original = new PaymentMetadata("ord-RT", 7, true, 60, "MEDIUM", "2026-01-01T00:00:00Z", 85, 30);

        PaymentMetadataEntity entity = mapper.toEntity(original);
        PaymentMetadata result = mapper.toDomain(entity);

        assertNotNull(result);
        assertEquals(original.orderId(), result.orderId());
        assertEquals(original.attempts(), result.attempts());
        assertEquals(original.isNewPaymentMethod(), result.isNewPaymentMethod());
        assertEquals(original.paymentMethodAgeDays(), result.paymentMethodAgeDays());
        assertEquals(original.customerRiskTier(), result.customerRiskTier());
        assertNull(result.enrichedAt());
        assertNull(result.velocityScore());
        assertNull(result.geoRiskScore());
    }

    @Test
    @DisplayName("round-trip: entity to domain to entity preserves common fields")
    void roundTrip_entityToDomainToEntity_preservesFields() {
        PaymentMetadataEntity original = new PaymentMetadataEntity();
        original.paymentId = UUID.randomUUID();
        original.orderId = "ord-RT2";
        original.attempts = 3;
        original.isNewPaymentMethod = false;
        original.paymentMethodAgeDays = 45;
        original.customerRiskTier = "HIGH";

        PaymentMetadata domain = mapper.toDomain(original);
        PaymentMetadataEntity result = mapper.toEntity(domain);

        assertNotNull(result);
        assertEquals(original.orderId, result.orderId);
        assertEquals(original.attempts, result.attempts);
        assertEquals(original.isNewPaymentMethod, result.isNewPaymentMethod);
        assertEquals(original.paymentMethodAgeDays, result.paymentMethodAgeDays);
        assertEquals(original.customerRiskTier, result.customerRiskTier);
    }

    @Test
    @DisplayName("toEntity with empty domain creates entity with null fields")
    void toEntity_emptyDomain_createsEntityWithNulls() {
        PaymentMetadata empty = PaymentMetadata.empty();

        PaymentMetadataEntity entity = mapper.toEntity(empty);

        assertNotNull(entity);
        assertNull(entity.orderId);
        assertNull(entity.attempts);
        assertNull(entity.isNewPaymentMethod);
        assertNull(entity.paymentMethodAgeDays);
        assertNull(entity.customerRiskTier);
    }
}
