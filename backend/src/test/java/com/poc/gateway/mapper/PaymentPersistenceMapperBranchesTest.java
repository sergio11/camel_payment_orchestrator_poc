package com.poc.gateway.mapper;

import com.poc.gateway.entity.PaymentMetadataEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PaymentPersistenceMapperBranchesTest {

    private PaymentPersistenceMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PaymentPersistenceMapper();
    }

    @Test
    @DisplayName("toMetadataMap with malformed JSON additionalProperties returns empty map (catch path)")
    void toMetadataMap_malformedJson_returnsEmptyMap() {
        PaymentMetadataEntity entity = new PaymentMetadataEntity();
        entity.paymentId = java.util.UUID.randomUUID();
        entity.orderId = "ord-1";
        entity.additionalProperties = "{invalid json!!!";

        Map<String, Object> result = mapper.toMetadataMap(entity);

        assertEquals("ord-1", result.get("orderId"));
        assertFalse(result.containsKey("additionalProperties"));
    }

    @Test
    @DisplayName("toMetadataMap with blank additionalProperties skips deserialization")
    void toMetadataMap_blankAdditionalProperties_skipsDeserialization() {
        PaymentMetadataEntity entity = new PaymentMetadataEntity();
        entity.paymentId = java.util.UUID.randomUUID();
        entity.orderId = "ord-2";
        entity.additionalProperties = "   ";

        Map<String, Object> result = mapper.toMetadataMap(entity);

        assertEquals("ord-2", result.get("orderId"));
    }

    @Test
    @DisplayName("toMetadataMap with null additionalProperties uses empty map")
    void toMetadataMap_nullAdditionalProperties_usesEmptyMap() {
        PaymentMetadataEntity entity = new PaymentMetadataEntity();
        entity.paymentId = java.util.UUID.randomUUID();
        entity.orderId = "ord-3";
        entity.additionalProperties = null;

        Map<String, Object> result = mapper.toMetadataMap(entity);

        assertEquals("ord-3", result.get("orderId"));
    }
}
