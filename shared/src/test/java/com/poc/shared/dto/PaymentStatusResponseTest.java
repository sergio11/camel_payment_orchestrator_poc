package com.poc.shared.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PaymentStatusResponseTest {

    @Test
    @DisplayName("Record construction with all fields should work correctly")
    void testRecordConstruction() {
        LocalDateTime now = LocalDateTime.now();

        PaymentStatusResponse response = new PaymentStatusResponse("pay-123", "COMPLETED", now);

        assertEquals("pay-123", response.id());
        assertEquals("COMPLETED", response.status());
        assertEquals(now, response.lastUpdated());
    }

    @Test
    @DisplayName("Record equality should work correctly")
    void testRecordEquality() {
        LocalDateTime now = LocalDateTime.now();

        PaymentStatusResponse response1 = new PaymentStatusResponse("id", "PENDING", now);
        PaymentStatusResponse response2 = new PaymentStatusResponse("id", "PENDING", now);

        assertEquals(response1, response2);
    }

    @Test
    @DisplayName("Record with null fields should work")
    void testRecordWithNulls() {
        PaymentStatusResponse response = new PaymentStatusResponse(null, null, null);

        assertNull(response.id());
        assertNull(response.status());
        assertNull(response.lastUpdated());
    }
}
