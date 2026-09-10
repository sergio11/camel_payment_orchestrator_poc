package com.poc.gateway.infrastructure.persistence.mapper;

import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.infrastructure.persistence.entity.PaymentMetadataEntity;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PaymentMetadataMapperTest {

    private final PaymentMetadataMapper mapper = new com.poc.gateway.infrastructure.persistence.mapper.PaymentMetadataMapperImpl();

    @Test
    void toEntity_null_returnsNull() {
        assertNull(mapper.toEntity(null));
    }

    @Test
    void toDomain_null_returnsNull() {
        assertNull(mapper.toDomain(null));
    }

    @Test
    void toEntity_mapsCommonFields() {
        PaymentMetadata meta = new PaymentMetadata("order-1", 3, true, 30, "LOW", "2024-01-01", 5, 10);
        PaymentMetadataEntity entity = mapper.toEntity(meta);
        assertEquals("order-1", entity.orderId);
        assertEquals(3, entity.attempts);
        assertTrue(entity.isNewPaymentMethod);
        assertEquals(30, entity.paymentMethodAgeDays);
        assertEquals("LOW", entity.customerRiskTier);
    }

    @Test
    void toDomain_mapsCommonFields() {
        PaymentMetadataEntity entity = new PaymentMetadataEntity();
        entity.paymentId = UUID.randomUUID();
        entity.orderId = "order-1";
        entity.attempts = 3;
        entity.isNewPaymentMethod = true;
        entity.paymentMethodAgeDays = 30;
        entity.customerRiskTier = "LOW";

        PaymentMetadata meta = mapper.toDomain(entity);
        assertEquals("order-1", meta.orderId());
        assertEquals(3, meta.attempts());
        assertTrue(meta.isNewPaymentMethod());
        assertEquals(30, meta.paymentMethodAgeDays());
        assertEquals("LOW", meta.customerRiskTier());
    }

    @Test
    void toEntity_and_toDomain_roundtrip() {
        PaymentMetadata meta = new PaymentMetadata("order-1", 3, true, 30, "LOW", null, null, null);
        PaymentMetadataEntity entity = mapper.toEntity(meta);
        PaymentMetadata back = mapper.toDomain(entity);
        assertEquals("order-1", back.orderId());
        assertEquals(3, back.attempts());
        assertTrue(back.isNewPaymentMethod());
        assertEquals(30, back.paymentMethodAgeDays());
        assertEquals("LOW", back.customerRiskTier());
    }

    @Test
    void toEntity_nullValues() {
        PaymentMetadata meta = new PaymentMetadata(null, null, null, null, null, null, null, null);
        PaymentMetadataEntity entity = mapper.toEntity(meta);
        assertNull(entity.orderId);
        assertNull(entity.attempts);
        assertNull(entity.isNewPaymentMethod);
        assertNull(entity.paymentMethodAgeDays);
        assertNull(entity.customerRiskTier);
    }
}
