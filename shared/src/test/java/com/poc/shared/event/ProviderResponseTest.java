package com.poc.shared.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ProviderResponseTest {

    @Test
    @DisplayName("Record construction with all fields should work correctly")
    void testRecordConstruction() {
        LocalDateTime now = LocalDateTime.now();

        ProviderResponse response = new ProviderResponse(
            "stripe",
            "txn-abc-123",
            true,
            null,
            null,
            now
        );

        assertEquals("stripe", response.providerId());
        assertEquals("txn-abc-123", response.transactionId());
        assertTrue(response.success());
        assertNull(response.errorCode());
        assertNull(response.errorMessage());
        assertEquals(now, response.processedAt());
    }

    @Test
    @DisplayName("Record construction with error fields should work")
    void testRecordConstructionWithError() {
        LocalDateTime now = LocalDateTime.now();

        ProviderResponse response = new ProviderResponse(
            "paypal",
            "txn-def-456",
            false,
            "CARD_DECLINED",
            "Your card was declined",
            now
        );

        assertEquals("paypal", response.providerId());
        assertEquals("txn-def-456", response.transactionId());
        assertFalse(response.success());
        assertEquals("CARD_DECLINED", response.errorCode());
        assertEquals("Your card was declined", response.errorMessage());
        assertEquals(now, response.processedAt());
    }

    @Test
    @DisplayName("Record equality should work correctly")
    void testRecordEquality() {
        LocalDateTime now = LocalDateTime.now();

        ProviderResponse r1 = new ProviderResponse("p1", "t1", true, null, null, now);
        ProviderResponse r2 = new ProviderResponse("p1", "t1", true, null, null, now);

        assertEquals(r1, r2);
    }

    @Test
    @DisplayName("Record with null fields should work")
    void testRecordWithNulls() {
        ProviderResponse response = new ProviderResponse(null, null, false, null, null, null);

        assertNull(response.providerId());
        assertNull(response.transactionId());
        assertFalse(response.success());
        assertNull(response.processedAt());
    }
}
