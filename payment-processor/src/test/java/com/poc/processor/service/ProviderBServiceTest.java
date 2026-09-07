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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ProviderBServiceTest {

    @Inject
    ProviderBService providerBService;

    private PaymentMessage createDefaultPaymentMessage() {
        return new PaymentMessage(
                "event-1", "payment-1", new BigDecimal("100.00"), "USD",
                "customer-1", "CREDIT_CARD", "US", 1, false, 30, "UTC",
                Map.of(), LocalDateTime.now()
        );
    }

    private PaymentMessage createPaymentMessage(String paymentId, String currency, String country) {
        return new PaymentMessage(
                "event-1", paymentId, new BigDecimal("250.50"), currency,
                "customer-2", "DEBIT_CARD", country, 1, false, 60, "UTC",
                Map.of(), LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("processPayment returns success with correct response fields")
    void processPayment_returnsSuccess_verifiesFields() {
        PaymentMessage paymentMessage = createDefaultPaymentMessage();

        jakarta.ws.rs.core.Response response = providerBService.processPayment(paymentMessage);

        assertNotNull(response);
        assertTrue(response.getStatus() == 200 || response.getStatus() == 500);

        if (response.getStatus() == 200) {
            ProviderResponse providerResponse = response.readEntity(ProviderResponse.class);
            assertNotNull(providerResponse);
            assertTrue(providerResponse.success());
            assertEquals("provider-b", providerResponse.providerId());
            assertNotNull(providerResponse.transactionId());
            assertFalse(providerResponse.transactionId().isEmpty());
            assertNull(providerResponse.errorCode());
            assertNull(providerResponse.errorMessage());
            assertNotNull(providerResponse.processedAt());
        }
    }

    @Test
    @DisplayName("processPayment returns provider-b for any payment message")
    void processPayment_returnsProviderB() {
        PaymentMessage paymentMessage = createPaymentMessage("payment-2", "EUR", "DE");

        jakarta.ws.rs.core.Response response = providerBService.processPayment(paymentMessage);
        assertNotNull(response);

        if (response.getStatus() == 200) {
            ProviderResponse providerResponse = response.readEntity(ProviderResponse.class);
            assertEquals("provider-b", providerResponse.providerId());
        }
    }

    @Test
    @DisplayName("processPayment returns error path when random < 0.02 (calls many times)")
    void processPayment_returnsError_whenRandomBelowThreshold() {
        int errorCount = 0;
        int totalCalls = 200;
        int minExpectedErrors = 1;

        for (int i = 0; i < totalCalls; i++) {
            PaymentMessage msg = new PaymentMessage(
                    "event-" + i, "payment-" + i, new BigDecimal("100.00"), "USD",
                    "customer-1", "CREDIT_CARD", "US", 1, false, 30, "UTC",
                    Map.of(), LocalDateTime.now()
            );
            jakarta.ws.rs.core.Response response = providerBService.processPayment(msg);
            if (response.getStatus() == 500) {
                ProviderResponse providerResponse = response.readEntity(ProviderResponse.class);
                assertFalse(providerResponse.success());
                assertEquals("PROVIDER_ERROR", providerResponse.errorCode());
                assertNotNull(providerResponse.errorMessage());
                errorCount++;
            }
        }

        assertTrue(errorCount >= minExpectedErrors,
            "Expected at least " + minExpectedErrors + " errors out of " + totalCalls + " calls, got " + errorCount);
    }

    @Test
    @DisplayName("processPayment handles InterruptedException with INTERRUPTED error code")
    void processPayment_handlesInterruptedException() throws Exception {
        AtomicBoolean interruptedResult = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        Thread testThread = new Thread(() -> {
            PaymentMessage msg = createDefaultPaymentMessage();
            // Interrupt the thread before calling processPayment to force the InterruptedException path
            Thread.currentThread().interrupt();
            jakarta.ws.rs.core.Response response = providerBService.processPayment(msg);

            if (response.getStatus() == 500) {
                ProviderResponse providerResponse = response.readEntity(ProviderResponse.class);
                if ("INTERRUPTED".equals(providerResponse.errorCode())) {
                    interruptedResult.set(true);
                }
            }
            latch.countDown();
        });

        testThread.start();
        latch.await();

        assertTrue(interruptedResult.get(), "Expected INTERRUPTED error code when thread is interrupted");
    }

    @Test
    @DisplayName("processPayment latency is between 500ms and 1000ms")
    void processPayment_latency_withinRange() {
        PaymentMessage paymentMessage = createDefaultPaymentMessage();

        long start = System.currentTimeMillis();
        jakarta.ws.rs.core.Response response = providerBService.processPayment(paymentMessage);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed >= 490, "Expected latency >= 490ms (allowing some tolerance), got " + elapsed + "ms");
        assertTrue(elapsed <= 1050, "Expected latency <= 1050ms (allowing some tolerance), got " + elapsed + "ms");
    }

    @Test
    @DisplayName("processPayment providerId is always provider-b")
    void processPayment_providerId_isProviderB() {
        for (int i = 0; i < 10; i++) {
            PaymentMessage msg = new PaymentMessage(
                    "event-" + i, "payment-" + i, new BigDecimal("50.00"), "USD",
                    "customer-" + i, "CREDIT_CARD", "US", 1, false, 30, "UTC",
                    Map.of(), LocalDateTime.now()
            );
            jakarta.ws.rs.core.Response response = providerBService.processPayment(msg);
            assertNotNull(response);

            if (response.getStatus() == 200) {
                ProviderResponse providerResponse = response.readEntity(ProviderResponse.class);
                assertEquals("provider-b", providerResponse.providerId());
            } else if (response.getStatus() == 500) {
                ProviderResponse providerResponse = response.readEntity(ProviderResponse.class);
                assertEquals("provider-b", providerResponse.providerId());
            }
        }
    }

    @Test
    @DisplayName("providerId is provider-b")
    void providerId_isProviderB() {
        assertEquals("provider-b", providerBService.providerId());
    }

    @Test
    @DisplayName("processPayment handles null amount without throwing")
    void processPayment_nullAmount_noException() {
        PaymentMessage msg = new PaymentMessage(
                "event-null", "payment-null", null, "USD",
                "customer-1", "CREDIT_CARD", "US", 1, false, 30, "UTC",
                Map.of(), LocalDateTime.now()
        );
        jakarta.ws.rs.core.Response response = providerBService.processPayment(msg);
        assertNotNull(response);
        assertTrue(response.getStatus() == 200 || response.getStatus() == 500);
    }

    @Test
    @DisplayName("processPayment success response has transactionId and processedAt")
    void processPayment_success_hasTransactionIdAndTimestamp() {
        PaymentMessage paymentMessage = createDefaultPaymentMessage();

        jakarta.ws.rs.core.Response response = providerBService.processPayment(paymentMessage);
        if (response.getStatus() == 200) {
            ProviderResponse providerResponse = response.readEntity(ProviderResponse.class);
            assertNotNull(providerResponse.transactionId());
            assertNotNull(providerResponse.processedAt());
            assertTrue(providerResponse.processedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
        }
    }
}
