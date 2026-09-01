package com.poc.shared.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PaymentResponseTest {

    @Test
    @DisplayName("Record construction with all fields should work correctly")
    void testRecordConstructionWithAllFields() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> metadata = Map.of("key1", "value1");

        PaymentResponse response = new PaymentResponse(
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

        PaymentResponse response = new PaymentResponse(
            "id-789",
            new BigDecimal("50.00"),
            "EUR",
            "cust-012",
            "DEBIT_CARD",
            "DE",
            "PENDING",
            null,
            "Timeout",
            null,
            now,
            now
        );

        assertEquals("id-789", response.id());
        assertNull(response.provider());
        assertEquals("Timeout", response.failureReason());
        assertNull(response.metadata());
    }

    @Test
    @DisplayName("Record equality should work correctly")
    void testRecordEquality() {
        LocalDateTime now = LocalDateTime.now();

        PaymentResponse response1 = new PaymentResponse(
            "id-1", new BigDecimal("10.00"), "USD", "c1", "CARD", "US", "OK", "P1", null, null, now, now
        );
        PaymentResponse response2 = new PaymentResponse(
            "id-1", new BigDecimal("10.00"), "USD", "c1", "CARD", "US", "OK", "P1", null, null, now, now
        );

        assertEquals(response1, response2);
    }
}
