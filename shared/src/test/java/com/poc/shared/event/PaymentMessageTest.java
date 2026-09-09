package com.poc.shared.event;

import com.poc.shared.dto.PaymentMetadataDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PaymentMessageTest {

    @Test
    @DisplayName("Record construction with all fields should work correctly")
    void testRecordConstructionWithAllFields() {
        LocalDateTime now = LocalDateTime.now();
        PaymentMetadataDTO metadata = new PaymentMetadataDTO(null, 1, true, 30, "LOW", null, 10, 5);

        PaymentMessage message = new PaymentMessage(
            "evt-001",
            "pay-123",
            new BigDecimal("150.00"),
            "USD",
            "cust-456",
            "CREDIT_CARD",
            "US",
            1,
            true,
            365,
            "America/New_York",
            metadata,
            now
        );

        assertEquals("evt-001", message.eventId());
        assertEquals("pay-123", message.paymentId());
        assertEquals(new BigDecimal("150.00"), message.amount());
        assertEquals("USD", message.currency());
        assertEquals("cust-456", message.customerId());
        assertEquals("CREDIT_CARD", message.paymentMethod());
        assertEquals("US", message.country());
        assertEquals(1, message.attemptCount());
        assertTrue(message.isNewPaymentMethod());
        assertEquals(365, message.customerAgeDays());
        assertEquals("America/New_York", message.timeZone());
        assertEquals(metadata, message.metadata());
        assertEquals(now, message.timestamp());
    }

    @Test
    @DisplayName("All getters should return correct values")
    void testGetters() {
        LocalDateTime timestamp = LocalDateTime.of(2025, 1, 15, 10, 30);

        PaymentMessage message = new PaymentMessage(
            "evt-002",
            "pay-789",
            new BigDecimal("99.99"),
            "EUR",
            "cust-012",
            "DEBIT_CARD",
            "DE",
            3,
            false,
            180,
            "Europe/Berlin",
            PaymentMetadataDTO.empty(),
            timestamp
        );

        assertEquals("evt-002", message.eventId());
        assertEquals("pay-789", message.paymentId());
        assertEquals(new BigDecimal("99.99"), message.amount());
        assertEquals("EUR", message.currency());
        assertEquals("cust-012", message.customerId());
        assertEquals("DEBIT_CARD", message.paymentMethod());
        assertEquals("DE", message.country());
        assertEquals(3, message.attemptCount());
        assertFalse(message.isNewPaymentMethod());
        assertEquals(180, message.customerAgeDays());
        assertEquals("Europe/Berlin", message.timeZone());
        assertEquals(PaymentMetadataDTO.empty(), message.metadata());
        assertEquals(timestamp, message.timestamp());
    }

    @Test
    @DisplayName("Record equality should work correctly")
    void testRecordEquality() {
        LocalDateTime now = LocalDateTime.now();

        PaymentMessage msg1 = new PaymentMessage(
            "e1", "p1", new BigDecimal("10.00"), "USD", "c1", "CARD", "US", 1, false, 30, "UTC", PaymentMetadataDTO.empty(), now
        );
        PaymentMessage msg2 = new PaymentMessage(
            "e1", "p1", new BigDecimal("10.00"), "USD", "c1", "CARD", "US", 1, false, 30, "UTC", PaymentMetadataDTO.empty(), now
        );

        assertEquals(msg1, msg2);
    }
}
