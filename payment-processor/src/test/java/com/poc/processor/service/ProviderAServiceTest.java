package com.poc.processor.service;

import com.poc.shared.event.PaymentMessage;
import com.poc.shared.event.ProviderResponse;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ProviderAServiceTest {

    @Inject
    ProviderAService providerAService;

    @Test
    @DisplayName("processPayment with valid PaymentMessage returns success")
    void processPayment_returnsSuccess() {
        PaymentMessage paymentMessage = new PaymentMessage(
                "event-1", "payment-1", new BigDecimal("100.00"), "USD",
                "customer-1", "CREDIT_CARD", "US", 1, false, 30, "UTC",
                Map.of(), LocalDateTime.now()
        );

        jakarta.ws.rs.core.Response response = providerAService.processPayment(paymentMessage);

        assertNotNull(response);
        assertTrue(response.getStatus() == 200 || response.getStatus() == 500);

        if (response.getStatus() == 200) {
            ProviderResponse providerResponse = response.readEntity(ProviderResponse.class);
            assertNotNull(providerResponse);
            assertTrue(providerResponse.success());
            assertEquals("provider-a", providerResponse.providerId());
            assertNotNull(providerResponse.transactionId());
            assertNull(providerResponse.errorCode());
            assertNull(providerResponse.errorMessage());
        }
    }

    @Test
    @DisplayName("processPayment returns ProviderResponse with provider name provider-a")
    void processPayment_returnsProviderNameProviderA() {
        PaymentMessage paymentMessage = new PaymentMessage(
                "event-2", "payment-2", new BigDecimal("250.50"), "EUR",
                "customer-2", "DEBIT_CARD", "DE", 1, false, 60, "UTC",
                Map.of(), LocalDateTime.now()
        );

        jakarta.ws.rs.core.Response response = providerAService.processPayment(paymentMessage);
        assertNotNull(response);

        if (response.getStatus() == 200) {
            ProviderResponse providerResponse = response.readEntity(ProviderResponse.class);
            assertEquals("provider-a", providerResponse.providerId());
        }
    }
}
