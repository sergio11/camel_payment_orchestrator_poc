package com.poc.shared.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UpdatePaymentStatusRequestDTOTest {

    @Test
    @DisplayName("Record construction with valid status should work")
    void testRecordConstruction() {
        UpdatePaymentStatusRequestDTO dto = new UpdatePaymentStatusRequestDTO("APPROVED");

        assertEquals("APPROVED", dto.status());
    }

    @Test
    @DisplayName("status should be accessible via accessor")
    void testStatusAccessor() {
        UpdatePaymentStatusRequestDTO dto = new UpdatePaymentStatusRequestDTO("REJECTED");

        assertEquals("REJECTED", dto.status());
    }
}
