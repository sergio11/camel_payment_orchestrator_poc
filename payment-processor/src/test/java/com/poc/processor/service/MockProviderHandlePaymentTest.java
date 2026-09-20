package com.poc.processor.service;

import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.shared.event.PaymentMessage;
import com.poc.shared.event.ProviderResponse;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class MockProviderHandlePaymentTest {

    static class AlwaysFailProvider extends AbstractMockProviderService {
        AlwaysFailProvider() {
            super("test-fail", 1.0, 0, 0);
        }
    }

    static class NeverFailProvider extends AbstractMockProviderService {
        NeverFailProvider() {
            super("test-ok", 0.0, 0, 0);
        }
    }

    private PaymentMessage message() {
        return new PaymentMessage(
            "evt-1", "pay-1", new BigDecimal("100.00"), "USD",
            "cust-1", "CREDIT_CARD", "US", 1, false, 30, "UTC",
            PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("handlePayment returns 500 on provider failure")
    void handlePayment_failure_returns500() {
        Response response = new AlwaysFailProvider().handlePayment(message());

        assertEquals(500, response.getStatus());
        assertInstanceOf(ProviderResponse.class, response.getEntity());
        ProviderResponse body = (ProviderResponse) response.getEntity();
        assertFalse(body.success());
        assertEquals("PROVIDER_ERROR", body.errorCode());
        assertEquals("test-fail", body.providerId());
    }

    @Test
    @DisplayName("handlePayment returns 200 on provider success")
    void handlePayment_success_returns200() {
        Response response = new NeverFailProvider().handlePayment(message());

        assertEquals(200, response.getStatus());
        assertInstanceOf(ProviderResponse.class, response.getEntity());
        ProviderResponse body = (ProviderResponse) response.getEntity();
        assertTrue(body.success());
        assertNotNull(body.transactionId());
        assertEquals("test-ok", body.providerId());
    }
}
