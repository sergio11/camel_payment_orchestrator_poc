package com.poc.shared.event;

import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.shared.exception.InvalidPaymentException;
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

    @Test
    @DisplayName("validate() should not throw for a valid payment message")
    void testValidateValidMessage() {
        PaymentMessage message = createValidMessage();

        assertDoesNotThrow(message::validate);
    }

    @Test
    @DisplayName("validate() should throw InvalidPaymentException when paymentId is null")
    void testValidateNullPaymentId() {
        PaymentMessage message = new PaymentMessage(
            "evt-001", null, new BigDecimal("100.00"), "USD", "cust-1", "CARD", "US",
            1, false, 30, "UTC", PaymentMetadataDTO.empty(), LocalDateTime.now()
        );

        InvalidPaymentException ex = assertThrows(InvalidPaymentException.class, message::validate);
        assertEquals("unknown", ex.getPaymentId());
    }

    @Test
    @DisplayName("validate() should throw InvalidPaymentException when amount is null")
    void testValidateNullAmount() {
        PaymentMessage message = new PaymentMessage(
            "evt-001", "pay-1", null, "USD", "cust-1", "CARD", "US",
            1, false, 30, "UTC", PaymentMetadataDTO.empty(), LocalDateTime.now()
        );

        InvalidPaymentException ex = assertThrows(InvalidPaymentException.class, message::validate);
        assertEquals("pay-1", ex.getPaymentId());
    }

    @Test
    @DisplayName("validate() should throw InvalidPaymentException when currency is null")
    void testValidateNullCurrency() {
        PaymentMessage message = new PaymentMessage(
            "evt-001", "pay-1", new BigDecimal("100.00"), null, "cust-1", "CARD", "US",
            1, false, 30, "UTC", PaymentMetadataDTO.empty(), LocalDateTime.now()
        );

        InvalidPaymentException ex = assertThrows(InvalidPaymentException.class, message::validate);
        assertEquals("pay-1", ex.getPaymentId());
    }

    @Test
    @DisplayName("validate() should throw InvalidPaymentException when customerId is null")
    void testValidateNullCustomerId() {
        PaymentMessage message = new PaymentMessage(
            "evt-001", "pay-1", new BigDecimal("100.00"), "USD", null, "CARD", "US",
            1, false, 30, "UTC", PaymentMetadataDTO.empty(), LocalDateTime.now()
        );

        InvalidPaymentException ex = assertThrows(InvalidPaymentException.class, message::validate);
        assertEquals("pay-1", ex.getPaymentId());
    }

    @Test
    @DisplayName("validate() should throw InvalidPaymentException when paymentMethod is null")
    void testValidateNullPaymentMethod() {
        PaymentMessage message = new PaymentMessage(
            "evt-001", "pay-1", new BigDecimal("100.00"), "USD", "cust-1", null, "US",
            1, false, 30, "UTC", PaymentMetadataDTO.empty(), LocalDateTime.now()
        );

        InvalidPaymentException ex = assertThrows(InvalidPaymentException.class, message::validate);
        assertEquals("pay-1", ex.getPaymentId());
    }

    @Test
    @DisplayName("withMetadata() should return a new message with updated metadata and preserve all other fields")
    void testWithMetadata() {
        LocalDateTime now = LocalDateTime.now();
        PaymentMetadataDTO originalMetadata = PaymentMetadataDTO.empty();
        PaymentMessage original = new PaymentMessage(
            "evt-001", "pay-123", new BigDecimal("250.00"), "EUR", "cust-456",
            "DEBIT_CARD", "DE", 2, true, 180, "Europe/Berlin", originalMetadata, now
        );

        PaymentMetadataDTO newMetadata = new PaymentMetadataDTO("ORD-1", 5, false, 60, "HIGH", null, 30, 50);
        PaymentMessage result = original.withMetadata(newMetadata);

        assertEquals("evt-001", result.eventId());
        assertEquals("pay-123", result.paymentId());
        assertEquals(new BigDecimal("250.00"), result.amount());
        assertEquals("EUR", result.currency());
        assertEquals("cust-456", result.customerId());
        assertEquals("DEBIT_CARD", result.paymentMethod());
        assertEquals("DE", result.country());
        assertEquals(2, result.attemptCount());
        assertTrue(result.isNewPaymentMethod());
        assertEquals(180, result.customerAgeDays());
        assertEquals("Europe/Berlin", result.timeZone());
        assertEquals(newMetadata, result.metadata());
        assertEquals(now, result.timestamp());
        assertNotSame(original, result);
    }

    private PaymentMessage createValidMessage() {
        return new PaymentMessage(
            "evt-001", "pay-123", new BigDecimal("100.00"), "USD", "cust-456",
            "CARD", "US", 1, false, 30, "UTC", PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
    }
}
