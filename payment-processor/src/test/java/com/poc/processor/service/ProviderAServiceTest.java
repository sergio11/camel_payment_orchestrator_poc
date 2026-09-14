package com.poc.processor.service;

import com.poc.processor.domain.ProviderGatewayResult;
import com.poc.processor.port.outbound.PaymentProviderPort;
import com.poc.shared.event.PaymentMessage;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ProviderAServiceTest extends AbstractProviderServiceTest {

    @Inject
    ProviderAService providerAService;

    @Override
    protected PaymentProviderPort providerService() {
        return providerAService;
    }

    @Override
    protected String expectedProviderId() {
        return "provider-a";
    }

    @Test
    @DisplayName("processPayment returns success with correct response fields")
    void processPayment_returnsSuccess_verifiesFields() {
        ProviderGatewayResult result = null;
        for (int i = 0; i < 20; i++) {
            result = providerAService.processPayment(createDefaultPaymentMessage());
            if (result.success()) break;
        }

        assertNotNull(result);
        assertTrue(result.success(), "Expected at least one success in 20 attempts");

        assertEquals("provider-a", result.providerId());
        assertNotNull(result.transactionId());
        assertFalse(result.transactionId().isEmpty());
        assertNull(result.errorCode());
        assertNull(result.errorMessage());
    }
}
