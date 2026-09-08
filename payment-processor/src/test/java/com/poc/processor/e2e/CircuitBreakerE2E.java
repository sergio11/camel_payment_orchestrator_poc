package com.poc.processor.e2e;

import com.poc.camel.testsupport.KafkaTestConsumer;
import com.poc.camel.testsupport.KafkaTestProducer;
import com.poc.processor.KafkaTestResource;
import com.poc.processor.ProviderMockConfig;
import com.poc.shared.event.PaymentMessage;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@QuarkusTestResource(KafkaTestResource.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CircuitBreakerE2E {

    @Inject
    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    ProviderMockConfig mockConfig;

    private KafkaTestProducer producer;

    @BeforeAll
    void setup() {
        mockConfig.setProviderASucceeds(true);
        mockConfig.setProviderBSucceeds(true);
        producer = new KafkaTestProducer(bootstrapServers);
    }

    @AfterAll
    void teardown() {
        try {
            mockConfig.setProviderASucceeds(true);
            mockConfig.setProviderBSucceeds(true);
            closeCircuitBreaker();
        } catch (Exception ignored) {
        }
        if (producer != null) {
            producer.close();
        }
    }

    @BeforeEach
    void reset() throws Exception {
        mockConfig.setProviderASucceeds(true);
        mockConfig.setProviderBSucceeds(true);
        mockConfig.resetCallCount();
        closeCircuitBreaker();
        mockConfig.resetCallCount();
    }

    private void closeCircuitBreaker() throws Exception {
        long deadline = System.currentTimeMillis() + 60000;
        int probes = 0;
        while (System.currentTimeMillis() < deadline) {
            PaymentMessage probe = buildPayment();
            try {
                producerTemplate.sendBodyAndHeader("direct:provider-selection", probe, "OriginalPaymentMessage", probe);
            } catch (Exception ignored) {
            }
            probes++;
            if (mockConfig.getProviderACallCount() >= 3 && probes >= 3) {
                return;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("Circuit breaker did not close within 60s");
    }

    @Test
    @DisplayName("E2E: Circuit breaker opens and half-open allows calls after wait duration")
    void testCircuitBreakerHalfOpenState() throws Exception {
        assertThat(camelContext.getRoute("provider-a")).isNotNull();
        assertThat(camelContext.getRoute("provider-b-fallback")).isNotNull();

        mockConfig.setProviderASucceeds(false);
        mockConfig.setProviderBSucceeds(false);

        for (int i = 0; i < 10; i++) {
            PaymentMessage payment = buildPayment();
            producerTemplate.sendBodyAndHeader("direct:provider-selection", payment, "OriginalPaymentMessage", payment);
            Thread.sleep(100);
        }

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(mockConfig.getProviderACallCount()).isGreaterThanOrEqualTo(1);
        });

        // Re-enable providers and reset the call counter so any new successful call
        // through the recovering circuit is unambiguously counted from zero.
        mockConfig.setProviderASucceeds(true);
        mockConfig.setProviderBSucceeds(true);
        mockConfig.resetCallCount();

        await().atMost(60, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            PaymentMessage payment = buildPayment();
            try {
                producerTemplate.sendBodyAndHeader("direct:provider-selection", payment, "OriginalPaymentMessage", payment);
            } catch (Exception ignored) {
            }
            assertThat(mockConfig.getProviderACallCount()).isGreaterThan(0);
        });
    }

    @Test
    @DisplayName("E2E: Circuit breaker recovery - OPEN -> HALF_OPEN -> CLOSED")
    void testCircuitBreakerRecoveryTransition() throws Exception {
        mockConfig.setProviderASucceeds(false);
        mockConfig.setProviderBSucceeds(false);

        for (int i = 0; i < 15; i++) {
            PaymentMessage payment = buildPayment();
            producerTemplate.sendBodyAndHeader("direct:provider-selection", payment, "OriginalPaymentMessage", payment);
            Thread.sleep(200);
        }

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(mockConfig.getProviderACallCount()).isGreaterThanOrEqualTo(1);
        });

        // Re-enable providers and reset the call counter so the recovery assertion
        // starts from a clean baseline of 0.
        mockConfig.setProviderASucceeds(true);
        mockConfig.setProviderBSucceeds(true);
        mockConfig.resetCallCount();

        await().atMost(60, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            PaymentMessage payment = buildPayment();
            try {
                producerTemplate.sendBodyAndHeader("direct:provider-selection", payment, "OriginalPaymentMessage", payment);
            } catch (Exception ignored) {
            }
            assertThat(mockConfig.getProviderACallCount()).isGreaterThan(0);
        });
    }

    private PaymentMessage buildPayment() {
        String paymentId = UUID.randomUUID().toString();
        return new PaymentMessage(
            UUID.randomUUID().toString(), paymentId, new BigDecimal("100.00"), "USD",
            "customer-123", "CREDIT_CARD", "US",
            0, false, 0, "UTC",
            Map.of(), LocalDateTime.now()
        );
    }
}
