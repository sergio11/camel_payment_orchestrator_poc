package com.poc.shared.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentMetadataDTOTest {

    @Test
    @DisplayName("empty() should return a DTO with all null fields")
    void testEmpty() {
        PaymentMetadataDTO dto = PaymentMetadataDTO.empty();

        assertNull(dto.orderId());
        assertNull(dto.attempts());
        assertNull(dto.isNewPaymentMethod());
        assertNull(dto.paymentMethodAgeDays());
        assertNull(dto.customerRiskTier());
        assertNull(dto.enrichedAt());
        assertNull(dto.velocityScore());
        assertNull(dto.geoRiskScore());
    }

    @Test
    @DisplayName("conEnrichment should update risk tier, velocity, and geo risk scores")
    void testConEnrichmentUpdatesRiskFields() {
        PaymentMetadataDTO original = new PaymentMetadataDTO("ORD-1", 3, true, 30, null, null, null, null);

        PaymentMetadataDTO enriched = original.conEnrichment("HIGH", 50, 75);

        assertEquals("HIGH", enriched.customerRiskTier());
        assertEquals(50, enriched.velocityScore());
        assertEquals(75, enriched.geoRiskScore());
    }

    @Test
    @DisplayName("conEnrichment should preserve original fields")
    void testConEnrichmentPreservesOriginalFields() {
        PaymentMetadataDTO original = new PaymentMetadataDTO("ORD-1", 3, true, 30, null, null, null, null);

        PaymentMetadataDTO enriched = original.conEnrichment("LOW", 10, 20);

        assertEquals("ORD-1", enriched.orderId());
        assertEquals(3, enriched.attempts());
        assertTrue(enriched.isNewPaymentMethod());
        assertEquals(30, enriched.paymentMethodAgeDays());
    }

    @Test
    @DisplayName("conEnrichment should set enrichedAt to a non-null timestamp")
    void testConEnrichmentSetsEnrichedAt() {
        PaymentMetadataDTO original = PaymentMetadataDTO.empty();

        PaymentMetadataDTO enriched = original.conEnrichment("MEDIUM", 25, 40);

        assertNotNull(enriched.enrichedAt());
        assertFalse(enriched.enrichedAt().isBlank());
    }

    @Test
    @DisplayName("Record construction with all fields should work")
    void testRecordConstruction() {
        PaymentMetadataDTO dto = new PaymentMetadataDTO("ORD-2", 1, false, 15, "LOW", "2025-01-01T00:00:00", 5, 10);

        assertEquals("ORD-2", dto.orderId());
        assertEquals(1, dto.attempts());
        assertFalse(dto.isNewPaymentMethod());
        assertEquals(15, dto.paymentMethodAgeDays());
        assertEquals("LOW", dto.customerRiskTier());
        assertEquals("2025-01-01T00:00:00", dto.enrichedAt());
        assertEquals(5, dto.velocityScore());
        assertEquals(10, dto.geoRiskScore());
    }
}
