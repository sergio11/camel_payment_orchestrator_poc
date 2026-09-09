package com.poc.shared.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaymentPageResponseTest {

    @Test
    @DisplayName("Record construction with all fields should work correctly")
    void testRecordConstruction() {
        PaymentResponseDTO payment = new PaymentResponseDTO(
            "id-1", new BigDecimal("100.00"), "USD", "c1", "CARD", "US", "OK", "P1", null, null,
            LocalDateTime.now(), LocalDateTime.now()
        );
        List<PaymentResponseDTO> payments = List.of(payment);

        PaymentPageResponseDTO response = new PaymentPageResponseDTO(payments, 1L, 10, 0);

        assertEquals(payments, response.payments());
        assertEquals(1L, response.total());
        assertEquals(10, response.limit());
        assertEquals(0, response.offset());
    }

    @Test
    @DisplayName("Record construction with empty list should work")
    void testRecordConstructionWithEmptyList() {
        PaymentPageResponseDTO response = new PaymentPageResponseDTO(Collections.emptyList(), 0L, 10, 0);

        assertTrue(response.payments().isEmpty());
        assertEquals(0L, response.total());
    }

    @Test
    @DisplayName("Record construction with large values should work")
    void testRecordConstructionWithLargeValues() {
        PaymentPageResponseDTO response = new PaymentPageResponseDTO(null, Long.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

        assertNull(response.payments());
        assertEquals(Long.MAX_VALUE, response.total());
        assertEquals(Integer.MAX_VALUE, response.limit());
        assertEquals(Integer.MAX_VALUE, response.offset());
    }
}
