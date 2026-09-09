package com.poc.gateway.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PaymentMetadataTest {

    @Test
    @DisplayName("empty() returns metadata with all null fields and empty additionalProperties")
    void empty_allNullFields() {
        PaymentMetadata m = PaymentMetadata.empty();
        assertNull(m.orderId());
        assertNull(m.attempts());
        assertNull(m.isNewPaymentMethod());
        assertNull(m.paymentMethodAgeDays());
        assertNull(m.customerRiskTier());
        assertNotNull(m.additionalProperties());
        assertTrue(m.additionalProperties().isEmpty());
    }

    @Test
    @DisplayName("fromMap(null) returns empty metadata")
    void fromMap_null_returnsEmpty() {
        PaymentMetadata m = PaymentMetadata.fromMap(null);
        assertNull(m.orderId());
        assertTrue(m.additionalProperties().isEmpty());
    }

    @Test
    @DisplayName("fromMap(empty) returns empty metadata")
    void fromMap_empty_returnsEmpty() {
        PaymentMetadata m = PaymentMetadata.fromMap(Map.of());
        assertNull(m.orderId());
        assertTrue(m.additionalProperties().isEmpty());
    }

    @Test
    @DisplayName("fromMap with attempts as Number (Double/Long)")
    void fromMap_attemptsAsNumber() {
        Map<String, Object> map = new HashMap<>();
        map.put("orderId", "ord-1");
        map.put("attempts", 3.0);
        PaymentMetadata m = PaymentMetadata.fromMap(map);
        assertEquals("ord-1", m.orderId());
        assertEquals(3, m.attempts());
    }

    @Test
    @DisplayName("fromMap with attempts as valid String")
    void fromMap_attemptsAsString() {
        Map<String, Object> map = new HashMap<>();
        map.put("attempts", "5");
        PaymentMetadata m = PaymentMetadata.fromMap(map);
        assertEquals(5, m.attempts());
    }

    @Test
    @DisplayName("fromMap with attempts as invalid String (NumberFormatException)")
    void fromMap_attemptsInvalidString() {
        Map<String, Object> map = new HashMap<>();
        map.put("attempts", "not-a-number");
        PaymentMetadata m = PaymentMetadata.fromMap(map);
        assertNull(m.attempts());
    }

    @Test
    @DisplayName("fromMap with paymentMethodAgeDays as Number and String paths")
    void fromMap_ageDaysVariousTypes() {
        Map<String, Object> map1 = new HashMap<>();
        map1.put("paymentMethodAgeDays", 10);
        assertEquals(10, PaymentMetadata.fromMap(map1).paymentMethodAgeDays());

        Map<String, Object> map2 = new HashMap<>();
        map2.put("paymentMethodAgeDays", "20");
        assertEquals(20, PaymentMetadata.fromMap(map2).paymentMethodAgeDays());

        Map<String, Object> map3 = new HashMap<>();
        map3.put("paymentMethodAgeDays", "bad");
        assertNull(PaymentMetadata.fromMap(map3).paymentMethodAgeDays());
    }

    @Test
    @DisplayName("fromMap with isNewPaymentMethod as Boolean and String")
    void fromMap_isNewPaymentMethodTypes() {
        Map<String, Object> map1 = new HashMap<>();
        map1.put("isNewPaymentMethod", true);
        assertTrue(PaymentMetadata.fromMap(map1).isNewPaymentMethod());

        Map<String, Object> map2 = new HashMap<>();
        map2.put("isNewPaymentMethod", "true");
        assertTrue(PaymentMetadata.fromMap(map2).isNewPaymentMethod());

        Map<String, Object> map3 = new HashMap<>();
        map3.put("isNewPaymentMethod", "false");
        assertFalse(PaymentMetadata.fromMap(map3).isNewPaymentMethod());
    }

    @Test
    @DisplayName("fromMap preserves additional properties not in known fields")
    void fromMap_additionalProperties() {
        Map<String, Object> map = new HashMap<>();
        map.put("orderId", "ord-1");
        map.put("customField", "customValue");
        map.put("anotherField", 42);
        PaymentMetadata m = PaymentMetadata.fromMap(map);
        assertEquals("ord-1", m.orderId());
        assertEquals("customValue", m.additionalProperties().get("customField"));
        assertEquals(42, m.additionalProperties().get("anotherField"));
    }

    @Test
    @DisplayName("toMap with all null fields returns only additionalProperties")
    void toMap_allNull() {
        PaymentMetadata m = new PaymentMetadata(null, null, null, null, null, Map.of());
        Map<String, Object> result = m.toMap();
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("toMap with all fields set includes everything")
    void toMap_allFieldsSet() {
        PaymentMetadata m = new PaymentMetadata(
            "ord-1", 3, true, 10, "LOW", Map.of("extra", "val")
        );
        Map<String, Object> result = m.toMap();
        assertEquals("ord-1", result.get("orderId"));
        assertEquals(3, result.get("attempts"));
        assertEquals(true, result.get("isNewPaymentMethod"));
        assertEquals(10, result.get("paymentMethodAgeDays"));
        assertEquals("LOW", result.get("customerRiskTier"));
        assertEquals("val", result.get("extra"));
        assertEquals(6, result.size());
    }

    @Test
    @DisplayName("toMap with null additionalProperties returns only known fields")
    void toMap_nullAdditionalProperties() {
        PaymentMetadata m = new PaymentMetadata("ord-1", 1, null, null, null, null);
        Map<String, Object> result = m.toMap();
        assertEquals(2, result.size());
        assertEquals("ord-1", result.get("orderId"));
        assertEquals(1, result.get("attempts"));
    }

    @Test
    @DisplayName("fromMap with attempts as unknown type returns null")
    void fromMap_attemptsUnknownType() {
        Map<String, Object> map = new HashMap<>();
        map.put("attempts", java.util.List.of(1, 2));
        PaymentMetadata m = PaymentMetadata.fromMap(map);
        assertNull(m.attempts());
    }
}
