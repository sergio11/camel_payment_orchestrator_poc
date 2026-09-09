package com.poc.gateway.mapper;

import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.entity.PaymentMetadataEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.*;

class PaymentPersistenceMapperBranchesTest {

    private PaymentMetadataMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(PaymentMetadataMapper.class);
    }

    @Test
    @DisplayName("toDomain with malformed JSON additionalProperties returns metadata without extra fields")
    void toDomain_malformedJson_returnsMetadataWithoutExtraFields() {
        PaymentMetadataEntity entity = new PaymentMetadataEntity();
        entity.paymentId = java.util.UUID.randomUUID();
        entity.orderId = "ord-1";
        entity.additionalProperties = "{invalid json!!!";

        PaymentMetadata result = mapper.toDomain(entity);

        assertEquals("ord-1", result.orderId());
        assertNull(result.velocityScore());
        assertNull(result.geoRiskScore());
    }

    @Test
    @DisplayName("toDomain with blank additionalProperties skips deserialization")
    void toDomain_blankAdditionalProperties_skipsDeserialization() {
        PaymentMetadataEntity entity = new PaymentMetadataEntity();
        entity.paymentId = java.util.UUID.randomUUID();
        entity.orderId = "ord-2";
        entity.additionalProperties = "   ";

        PaymentMetadata result = mapper.toDomain(entity);

        assertEquals("ord-2", result.orderId());
    }

    @Test
    @DisplayName("toDomain with null additionalProperties uses defaults")
    void toDomain_nullAdditionalProperties_usesDefaults() {
        PaymentMetadataEntity entity = new PaymentMetadataEntity();
        entity.paymentId = java.util.UUID.randomUUID();
        entity.orderId = "ord-3";
        entity.additionalProperties = null;

        PaymentMetadata result = mapper.toDomain(entity);

        assertEquals("ord-3", result.orderId());
        assertNull(result.velocityScore());
        assertNull(result.geoRiskScore());
    }

    @Test
    @DisplayName("toDomain with entity containing all fields maps correctly")
    void toDomain_allFields() {
        PaymentMetadataEntity entity = new PaymentMetadataEntity();
        entity.paymentId = java.util.UUID.randomUUID();
        entity.orderId = "ord-4";
        entity.attempts = 5;
        entity.isNewPaymentMethod = true;
        entity.paymentMethodAgeDays = 90;
        entity.customerRiskTier = "HIGH";

        PaymentMetadata result = mapper.toDomain(entity);

        assertEquals("ord-4", result.orderId());
        assertEquals(5, result.attempts());
        assertEquals(true, result.isNewPaymentMethod());
        assertEquals(90, result.paymentMethodAgeDays());
        assertEquals("HIGH", result.customerRiskTier());
    }

    @Test
    @DisplayName("toEntity with all fields maps correctly")
    void toEntity_allFields() {
        PaymentMetadata domain = new PaymentMetadata("ord-5", 2, false, 45, "LOW", "2024-06-01T12:00:00", 8, 3);

        PaymentMetadataEntity entity = mapper.toEntity(domain);

        assertEquals("ord-5", entity.orderId);
        assertEquals(2, entity.attempts);
        assertEquals(false, entity.isNewPaymentMethod);
        assertEquals(45, entity.paymentMethodAgeDays);
        assertEquals("LOW", entity.customerRiskTier);
    }
}
