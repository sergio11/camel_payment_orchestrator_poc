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
class ProviderBServiceTest {

    @Inject
    ProviderBService providerBService;

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

        ProviderGatewayResult result = providerBService.processPayment(paymentMessage);

        assertNotNull(result);

        if (result.success()) {
            assertEquals("provider-b", result.providerId());
            assertNotNull(result.transactionId());
            assertFalse(result.transactionId().isEmpty());
            assertNull(result.errorCode());
            assertNull(result.errorMessage());
        }
    }

    @Test
    @DisplayName("processPayment returns provider-b for any payment message")
    void processPayment_returnsProviderB() {
        PaymentMessage paymentMessage = createPaymentMessage("payment-2", "EUR", "DE");

        ProviderGatewayResult result = providerBService.processPayment(paymentMessage);
        assertNotNull(result);
        assertEquals("provider-b", result.providerId());
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
                    PaymentMetadataDTO.empty(), LocalDateTime.now()
            );
            ProviderGatewayResult result = providerBService.processPayment(msg);
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
        AtomicBoolean interruptedResult = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        Thread testThread = new Thread(() -> {
            PaymentMessage msg = createDefaultPaymentMessage();
            Thread.currentThread().interrupt();
            ProviderGatewayResult result = providerBService.processPayment(msg);

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
    @DisplayName("processPayment latency is between 500ms and 1000ms")
    void processPayment_latency_withinRange() {
        PaymentMessage paymentMessage = createDefaultPaymentMessage();

        long start = System.currentTimeMillis();
        providerBService.processPayment(paymentMessage);
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
                    PaymentMetadataDTO.empty(), LocalDateTime.now()
            );
            ProviderGatewayResult result = providerBService.processPayment(msg);
            assertNotNull(result);
            assertEquals("provider-b", result.providerId());
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
                PaymentMetadataDTO.empty(), LocalDateTime.now()
        );
        ProviderGatewayResult result = providerBService.processPayment(msg);
        assertNotNull(result);
    }

    @Test
    @DisplayName("processPayment success response has transactionId")
    void processPayment_success_hasTransactionId() {
        PaymentMessage paymentMessage = createDefaultPaymentMessage();

        ProviderGatewayResult result = providerBService.processPayment(paymentMessage);
        if (result.success()) {
            assertNotNull(result.transactionId());
        }
    }
}
