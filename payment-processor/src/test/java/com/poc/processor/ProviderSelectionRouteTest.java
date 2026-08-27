package com.poc.processor;

import com.poc.shared.event.PaymentMessage;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(KafkaTestResource.class)
@TestInstance(Lifecycle.PER_CLASS)
class ProviderSelectionRouteTest {

    @Inject
    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    KafkaProducer<String, String> producer;
    KafkaConsumer<String, String> consumer;

    @BeforeAll
    void setup() {
        Properties prodProps = new Properties();
        prodProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        prodProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        prodProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producer = new KafkaProducer<>(prodProps);

        Properties consProps = new Properties();
        consProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-provider-group-" + UUID.randomUUID());
        consProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        consumer = new KafkaConsumer<>(consProps);
        consumer.subscribe(Arrays.asList(
            "payments.events.dead-letter",
            "fraud.events.detected"
        ));
    }

    @AfterAll
    void teardown() {
        consumer.close();
        producer.close();
    }

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @BeforeEach
    void reset() {
        consumer.poll(Duration.ofMillis(100));
    }

    private PaymentMessage createTestPayment(String paymentId) {
        return new PaymentMessage(
            UUID.randomUUID().toString(),
            paymentId,
            new BigDecimal("100.00"),
            "USD",
            "cust-test",
            "CREDIT_CARD",
            "US",
            0,
            false,
            0,
            "UTC",
            Map.of(),
            LocalDateTime.now()
        );
    }

    private String providerAFailureResponse() {
        return """
            {
                "providerId": "provider-a",
                "transactionId": null,
                "success": false,
                "errorCode": "PROVIDER_A_DOWN",
                "errorMessage": "Provider A is unavailable",
                "processedAt": "2026-01-01T00:00:00"
            }
            """;
    }

    private String providerBFailureResponse() {
        return """
            {
                "providerId": "provider-b",
                "transactionId": null,
                "success": false,
                "errorCode": "PROVIDER_B_DOWN",
                "errorMessage": "Provider B is unavailable",
                "processedAt": "2026-01-01T00:00:00"
            }
            """;
    }

    private String providerBSuccessResponse() {
        return """
            {
                "providerId": "provider-b",
                "transactionId": "txn-123",
                "success": true,
                "errorCode": null,
                "errorMessage": null,
                "processedAt": "2026-01-01T00:00:00"
            }
            """;
    }

    private void mockProviderAFailure() throws Exception {
        AdviceWith.adviceWith(camelContext, "call-provider-a", builder -> {
            builder.interceptSendToEndpoint("netty-http:*")
                .skipSendToOriginalEndpoint()
                .process(exchange -> {
                    String mockResponse = providerAFailureResponse();
                    exchange.getMessage().setBody(mockResponse, String.class);
                });
        });
    }

    private void mockProviderB(boolean success) throws Exception {
        AdviceWith.adviceWith(camelContext, "call-provider-b", builder -> {
            builder.interceptSendToEndpoint("netty-http:*")
                .skipSendToOriginalEndpoint()
                .process(exchange -> {
                    String mockResponse = success ? providerBSuccessResponse() : providerBFailureResponse();
                    exchange.getMessage().setBody(mockResponse, String.class);
                });
        });
    }

    private boolean waitForDeadLetterMessage(String paymentId, long timeoutMs) throws InterruptedException {
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if ("payments.events.dead-letter".equals(record.topic())) {
                    if (record.value().contains(paymentId)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Test
    @DisplayName("3.43: Verify provider fallback when Provider A fails")
    void testProviderFallbackWhenProviderAFails() throws Exception {
        mockProviderAFailure();
        mockProviderB(true);

        String paymentId = UUID.randomUUID().toString();
        PaymentMessage payment = createTestPayment(paymentId);

        producerTemplate.sendBody("direct:provider-selection", payment);

        boolean deadLetterReceived = waitForDeadLetterMessage(paymentId, 30000);

        assertFalse(deadLetterReceived,
            "Payment should NOT be in dead letter queue - Provider B succeeded as fallback");
    }

    @Test
    @DisplayName("3.44: Verify Circuit Breaker opens after 50% failure rate")
    void testCircuitBreakerOpensAfterFailureRate() throws Exception {
        assertNotNull(camelContext.getRoute("provider-a"),
            "Provider A route with circuit breaker should exist");
        assertNotNull(camelContext.getRoute("provider-b-fallback"),
            "Provider B fallback route should exist");

        AtomicInteger providerACallCount = new AtomicInteger(0);

        AdviceWith.adviceWith(camelContext, "call-provider-a", builder -> {
            builder.interceptSendToEndpoint("netty-http:*")
                .skipSendToOriginalEndpoint()
                .process(exchange -> {
                    providerACallCount.incrementAndGet();
                    exchange.getMessage().setBody(providerAFailureResponse(), String.class);
                });
        });

        int paymentsToSend = 15;
        for (int i = 0; i < paymentsToSend; i++) {
            String paymentId = UUID.randomUUID().toString();
            PaymentMessage payment = createTestPayment(paymentId);
            producerTemplate.sendBody("direct:provider-selection", payment);
            Thread.sleep(200);
        }

        Thread.sleep(5000);

        int callsMade = providerACallCount.get();
        assertTrue(callsMade >= 1,
            "Provider A should have been called at least once, actual: " + callsMade);
        assertTrue(callsMade < paymentsToSend,
            "Circuit breaker should have opened after failures, reducing calls. " +
            "Expected < " + paymentsToSend + " calls, actual: " + callsMade);
    }

    @Test
    @DisplayName("3.45: Verify Dead Letter Channel receives failed payments after retries")
    void testDeadLetterChannelReceivesFailedPayments() throws Exception {
        mockProviderAFailure();
        mockProviderB(false);

        String paymentId = UUID.randomUUID().toString();
        PaymentMessage payment = createTestPayment(paymentId);

        producerTemplate.sendBody("direct:provider-selection", payment);

        boolean deadLetterReceived = waitForDeadLetterMessage(paymentId, 45000);

        assertTrue(deadLetterReceived,
            "Payment should be in dead letter queue - both providers failed");
    }
}
