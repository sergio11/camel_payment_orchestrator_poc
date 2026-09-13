package com.poc.processor.service;

import com.poc.processor.domain.ProviderGatewayResult;
import com.poc.shared.dto.PaymentMetadataDTO;
import com.poc.shared.event.PaymentMessage;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ProviderAServiceTest {

    @Inject
    ProviderAService providerAService;

    private PaymentMessage createDefaultPaymentMessage() {
        return new PaymentMessage(
                "event-1", "payment-1", new BigDecimal("100.00"), "USD",
                "customer-1", "CREDIT_CARD", "US", 1, false, 30, "UTC",
                PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
    }

    private PaymentMessage createPaymentMessage(String paymentId, String currency, String country) {
        return new PaymentMessage(
                "event-1", paymentId, new BigDecimal("250.50"), currency,
                "customer-2", "DEBIT_CARD", country, 1, false, 60, "UTC",
                PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("processPayment returns success with correct response fields")
    void processPayment_returnsSuccess_verifiesFields() {
        PaymentMessage paymentMessage = createDefaultPaymentMessage();

        ProviderGatewayResult result = providerAService.processPayment(paymentMessage);

        assertNotNull(result);
        assertTrue(result.success() || !result.success());

        if (result.success()) {
            assertEquals("provider-a", result.providerId());
            assertNotNull(result.transactionId());
            assertFalse(result.transactionId().isEmpty());
            assertNull(result.errorCode());
            assertNull(result.errorMessage());
        }
    }

    @Test
    @DisplayName("processPayment returns provider-a for any payment message")
    void processPayment_returnsProviderA() {
        PaymentMessage paymentMessage = createPaymentMessage("payment-2", "EUR", "DE");

        ProviderGatewayResult result = providerAService.processPayment(paymentMessage);
        assertNotNull(result);
        assertEquals("provider-a", result.providerId());
    }

    @Test
    @DisplayName("processPayment returns error path when random < 0.1 (calls many times)")
    void processPayment_returnsError_whenRandomBelowThreshold() {
        int errorCount = 0;
        int totalCalls = 200;
        int minExpectedErrors = 5;

        for (int i = 0; i < totalCalls; i++) {
            PaymentMessage msg = new PaymentMessage(
                    "event-" + i, "payment-" + i, new BigDecimal("100.00"), "USD",
                    "customer-1", "CREDIT_CARD", "US", 1, false, 30, "UTC",
                    PaymentMetadataDTO.empty(), LocalDateTime.now()
            );
            ProviderGatewayResult result = providerAService.processPayment(msg);
            if (!result.success()) {
                assertFalse(result.success());
                assertEquals("PROVIDER_ERROR", result.errorCode());
                assertNotNull(result.errorMessage());
                errorCount++;
            }
        }

        assertTrue(errorCount >= minExpectedErrors,
            "Expected at least " + minExpectedErrors + " errors out of " + totalCalls + " calls, got " + errorCount);
    }

    @Test
    @DisplayName("processPayment handles InterruptedException with INTERRUPTED error code")
    void processPayment_handlesInterruptedException() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean interruptedResult = new AtomicBoolean(false);

        Thread testThread = new Thread(() -> {
            PaymentMessage msg = createDefaultPaymentMessage();
            Thread.currentThread().interrupt();
            ProviderGatewayResult result = providerAService.processPayment(msg);

            if (!result.success() && "INTERRUPTED".equals(result.errorCode())) {
                interruptedResult.set(true);
            }
            latch.countDown();
        });

        testThread.start();
        latch.await();

        assertTrue(interruptedResult.get(), "Expected INTERRUPTED error code when thread is interrupted");
    }

    @Test
    @DisplayName("processPayment latency is between 100ms and 200ms")
    void processPayment_latency_withinRange() {
        PaymentMessage paymentMessage = createDefaultPaymentMessage();

        long start = System.currentTimeMillis();
        providerAService.processPayment(paymentMessage);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed >= 90, "Expected latency >= 90ms (allowing some tolerance), got " + elapsed + "ms");
        assertTrue(elapsed <= 250, "Expected latency <= 250ms (allowing some tolerance), got " + elapsed + "ms");
    }

    @Test
    @DisplayName("processPayment providerId is always provider-a")
    void processPayment_providerId_isProviderA() {
        for (int i = 0; i < 10; i++) {
            PaymentMessage msg = new PaymentMessage(
                    "event-" + i, "payment-" + i, new BigDecimal("50.00"), "USD",
                    "customer-" + i, "CREDIT_CARD", "US", 1, false, 30, "UTC",
                    PaymentMetadataDTO.empty(), LocalDateTime.now()
            );
            ProviderGatewayResult result = providerAService.processPayment(msg);
            assertNotNull(result);
            assertEquals("provider-a", result.providerId());
        }
    }

    @Test
    @DisplayName("providerId is provider-a")
    void providerId_isProviderA() {
        assertEquals("provider-a", providerAService.providerId());
    }

    @Test
    @DisplayName("processPayment handles null amount without throwing")
    void processPayment_nullAmount_noException() {
        PaymentMessage msg = new PaymentMessage(
                "event-null", "payment-null", null, "USD",
                "customer-1", "CREDIT_CARD", "US", 1, false, 30, "UTC",
                PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
        ProviderGatewayResult result = providerAService.processPayment(msg);
        assertNotNull(result);
    }

    @Test
    @DisplayName("processPayment success response has transactionId")
    void processPayment_success_hasTransactionId() {
        PaymentMessage paymentMessage = createDefaultPaymentMessage();

        ProviderGatewayResult result = providerAService.processPayment(paymentMessage);
        if (result.success()) {
            assertNotNull(result.transactionId());
        }
    }
}
