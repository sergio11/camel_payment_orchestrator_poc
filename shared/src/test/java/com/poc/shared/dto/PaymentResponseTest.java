package com.poc.shared.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PaymentResponseTest {

    @Test
    @DisplayName("Record construction with all fields should work correctly")
    void testRecordConstructionWithAllFields() {
        LocalDateTime now = LocalDateTime.now();
        PaymentMetadataDTO metadata = new PaymentMetadataDTO("order-1", 1, true, 30, "LOW", null, 10, 5);

        PaymentResponseDTO response = new PaymentResponseDTO(
            "id-123",
            new BigDecimal("99.99"),
            "USD",
            "cust-456",
            "CREDIT_CARD",
            "US",
            "COMPLETED",
            "STRIPE",
            null,
            metadata,
            now,
            now
        );

        assertEquals("id-123", response.id());
        assertEquals(new BigDecimal("99.99"), response.amount());
        assertEquals("USD", response.currency());
        assertEquals("cust-456", response.customerId());
        assertEquals("CREDIT_CARD", response.paymentMethod());
        assertEquals("US", response.country());
        assertEquals("COMPLETED", response.status());
        assertEquals("STRIPE", response.provider());
        assertNull(response.failureReason());
        assertEquals(metadata, response.metadata());
        assertEquals(now, response.createdAt());
        assertEquals(now, response.updatedAt());
    }

    @Test
    @DisplayName("Record construction with null provider and failureReason should work")
    void testRecordConstructionWithNulls() {
        LocalDateTime now = LocalDateTime.now();

        PaymentResponseDTO response = new PaymentResponseDTO(
            "id-789",
            new BigDecimal("50.00"),
            "EUR",
            "cust-012",
            "DEBIT_CARD",
            "DE",
            "PENDING",
            null,
            "Timeout",
            PaymentMetadataDTO.empty(),
            now,
            now
        );

        assertEquals("id-789", response.id());
        assertNull(response.provider());
        assertEquals("Timeout", response.failureReason());
        assertEquals(PaymentMetadataDTO.empty(), response.metadata());
    }

    @Test
    @DisplayName("Record equality should work correctly")
    void testRecordEquality() {
        LocalDateTime now = LocalDateTime.now();

        PaymentResponseDTO response1 = new PaymentResponseDTO(
            "id-1", new BigDecimal("10.00"), "USD", "c1", "CARD", "US", "OK", "P1", null, PaymentMetadataDTO.empty(), now, now
        );
        PaymentResponseDTO response2 = new PaymentResponseDTO(
            "id-1", new BigDecimal("10.00"), "USD", "c1", "CARD", "US", "OK", "P1", null, PaymentMetadataDTO.empty(), now, now
        );

        assertEquals(response1, response2);
    }
}
