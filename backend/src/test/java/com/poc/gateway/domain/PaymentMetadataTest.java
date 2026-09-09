package com.poc.gateway.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentMetadataTest {

    @Test
    @DisplayName("empty() returns metadata with all null fields")
    void empty_allNullFields() {
        PaymentMetadata m = PaymentMetadata.empty();
        assertNull(m.orderId());
        assertNull(m.attempts());
        assertNull(m.isNewPaymentMethod());
        assertNull(m.paymentMethodAgeDays());
        assertNull(m.customerRiskTier());
        assertNull(m.enrichedAt());
        assertNull(m.velocityScore());
        assertNull(m.geoRiskScore());
    }

    @Test
    @DisplayName("constructor sets all fields correctly")
    void constructor_setsAllFields() {
        PaymentMetadata m = new PaymentMetadata(
            "ord-1", 3, true, 10, "LOW", "2026-01-01", 5, 7
        );
        assertEquals("ord-1", m.orderId());
        assertEquals(3, m.attempts());
        assertTrue(m.isNewPaymentMethod());
        assertEquals(10, m.paymentMethodAgeDays());
        assertEquals("LOW", m.customerRiskTier());
        assertEquals("2026-01-01", m.enrichedAt());
        assertEquals(5, m.velocityScore());
        assertEquals(7, m.geoRiskScore());
    }

    @Test
    @DisplayName("constructor with all nulls")
    void constructor_allNulls() {
        PaymentMetadata m = new PaymentMetadata(
            null, null, null, null, null, null, null, null
        );
        assertNull(m.orderId());
        assertNull(m.attempts());
        assertNull(m.isNewPaymentMethod());
        assertNull(m.paymentMethodAgeDays());
        assertNull(m.customerRiskTier());
        assertNull(m.enrichedAt());
        assertNull(m.velocityScore());
        assertNull(m.geoRiskScore());
    }

    @Test
    @DisplayName("record equality - equal instances")
    void recordEquality_equal() {
        PaymentMetadata m1 = new PaymentMetadata(
            "ord-1", 3, true, 10, "LOW", "2026-01-01", 5, 7
        );
        PaymentMetadata m2 = new PaymentMetadata(
            "ord-1", 3, true, 10, "LOW", "2026-01-01", 5, 7
        );
        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    @DisplayName("record equality - not equal when fields differ")
    void recordEquality_notEqual() {
        PaymentMetadata m1 = new PaymentMetadata(
            "ord-1", 3, true, 10, "LOW", "2026-01-01", 5, 7
        );
        PaymentMetadata m2 = new PaymentMetadata(
            "ord-2", 3, true, 10, "LOW", "2026-01-01", 5, 7
        );
        assertNotEquals(m1, m2);
    }

    @Test
    @DisplayName("getters return correct values for each field")
    void getters_individual() {
        PaymentMetadata m = new PaymentMetadata(
            "ord-99", 10, false, 30, "HIGH", "2026-06-15", 20, 80
        );
        assertEquals("ord-99", m.orderId());
        assertEquals(10, m.attempts());
        assertFalse(m.isNewPaymentMethod());
        assertEquals(30, m.paymentMethodAgeDays());
        assertEquals("HIGH", m.customerRiskTier());
        assertEquals("2026-06-15", m.enrichedAt());
        assertEquals(20, m.velocityScore());
        assertEquals(80, m.geoRiskScore());
    }
}
