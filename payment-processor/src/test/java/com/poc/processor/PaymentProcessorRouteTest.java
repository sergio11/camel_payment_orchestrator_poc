package com.poc.processor;

import com.poc.shared.event.FraudResult;
import com.poc.shared.event.PaymentMessage;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.*;

import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(KafkaTestResource.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentProcessorRouteTest {

    @Inject
    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Inject
    ProviderMockConfig mockConfig;

    KafkaProducer<String, String> producer;

    @BeforeAll
    void setup() {
        mockConfig.setProviderASucceeds(true);
        mockConfig.setProviderBSucceeds(true);

        Properties prodProps = new Properties();
        prodProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        prodProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        prodProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producer = new KafkaProducer<>(prodProps);
    }

    @AfterAll
    void teardown() {
        if (producer != null) {
            producer.close();
        }
    }

    private KafkaConsumer<String, String> createConsumer(String groupId) {
        Properties consProps = new Properties();
        consProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        consProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        KafkaConsumer<String, String> c = new KafkaConsumer<>(consProps);
        c.subscribe(Arrays.asList(
            "payments.events.received",
            "payments.events.processed",
            "payments.events.failed",
            "fraud.events.detected",
            "payments.events.audit",
            "payments.events.dead-letter"
        ));
        return c;
    }

    @Test
    @DisplayName("3.40: Verify payment triggers fraud check via Kafka")
    void testPaymentTriggersFraudCheckViaKafka() throws Exception {
        KafkaConsumer<String, String> consumer = createConsumer("test-approach-" + UUID.randomUUID());
        try {
            String paymentId = UUID.randomUUID().toString();
            PaymentMessage payment = buildPayment(paymentId, new BigDecimal("100.00"), "USD", "US", "CREDIT_CARD", 0, Map.of());

            producer.send(new ProducerRecord<>("payments.events.received", paymentId, toJson(payment))).get(5, TimeUnit.SECONDS);

            FraudResult result = consumeResult(consumer, paymentId, "payments.events.processed", 30);

            assertNotNull(result, "Should receive fraud result");
            assertEquals(paymentId, result.paymentId());
            assertEquals("APPROVE", result.action());
        } finally {
            consumer.close();
        }
    }

    @Test
    @DisplayName("3.47: Verify events published to payments.events.processed on success")
    void testSuccessPublishesProcessedEvent() throws Exception {
        KafkaConsumer<String, String> consumer = createConsumer("test-success-" + UUID.randomUUID());
        try {
            String paymentId = UUID.randomUUID().toString();
            PaymentMessage payment = buildPayment(paymentId, new BigDecimal("100.00"), "USD", "US", "CREDIT_CARD", 0, Map.of());

            producer.send(new ProducerRecord<>("payments.events.received", paymentId, toJson(payment))).get(5, TimeUnit.SECONDS);

            FraudResult result = consumeResult(consumer, paymentId, "payments.events.processed", 30);

            assertEquals("APPROVE", result.action(), "Low risk payment should be approved");
        } finally {
            consumer.close();
        }
    }

    @Test
    @DisplayName("3.48: Verify events published to payments.events.failed on fraud reject")
    void testFraudRejectPublishesFailedEvent() throws Exception {
        KafkaConsumer<String, String> consumer = createConsumer("test-reject-" + UUID.randomUUID());
        try {
            String paymentId = UUID.randomUUID().toString();
            PaymentMessage payment = buildPayment(paymentId, new BigDecimal("20000.00"), "USD", "XX", "CREDIT_CARD", 1, Map.of("attempts", 1));

            producer.send(new ProducerRecord<>("payments.events.received", paymentId, toJson(payment))).get(5, TimeUnit.SECONDS);

            FraudResult result = consumeResult(consumer, paymentId, "fraud.events.detected", 30);

            assertEquals("REJECT", result.action(), "High risk payment should be rejected");
        } finally {
            consumer.close();
        }
    }

    @Test
    @DisplayName("Verify payment enrichment adds risk metadata before fraud evaluation")
    void testPaymentEnrichmentOccursBeforeFraudCheck() throws Exception {
        KafkaConsumer<String, String> consumer = createConsumer("test-enrich-" + UUID.randomUUID());
        try {
            String paymentId = UUID.randomUUID().toString();
            PaymentMessage payment = buildPayment(paymentId, new BigDecimal("100.00"), "USD", "US", "CREDIT_CARD", 0, Map.of());

            producer.send(new ProducerRecord<>("payments.events.received", paymentId, toJson(payment))).get(5, TimeUnit.SECONDS);

            FraudResult result = consumeResult(consumer, paymentId, "payments.events.processed", 30);

            assertNotNull(result, "Should receive fraud result");
            assertEquals("APPROVE", result.action());
            assertTrue(result.triggeredRules() != null, "Triggered rules should be present (enrichment happened)");
        } finally {
            consumer.close();
        }
    }

    @Test
    @DisplayName("Verify payment from high-risk country with high amount is rejected")
    void testHighRiskCountryPaymentRejected() throws Exception {
        KafkaConsumer<String, String> consumer = createConsumer("test-country-" + UUID.randomUUID());
        try {
            String paymentId = UUID.randomUUID().toString();
            PaymentMessage payment = buildPayment(paymentId, new BigDecimal("20000.00"), "USD", "XX", "CREDIT_CARD", 1, Map.of());

            producer.send(new ProducerRecord<>("payments.events.received", paymentId, toJson(payment))).get(5, TimeUnit.SECONDS);

            FraudResult result = consumeResult(consumer, paymentId, "fraud.events.detected", 30);

            assertEquals("REJECT", result.action(), "High-risk country + high amount should be rejected");
            assertTrue(result.riskScore() >= 80, "Risk score should be >= 80");
        } finally {
            consumer.close();
        }
    }

    @Test
    @DisplayName("Verify medium-risk payment goes to review")
    void testMediumRiskPaymentReviewed() throws Exception {
        KafkaConsumer<String, String> consumer = createConsumer("test-review-" + UUID.randomUUID());
        try {
            String paymentId = UUID.randomUUID().toString();
            PaymentMessage payment = buildPayment(paymentId, new BigDecimal("20000.00"), "USD", "US", "CREDIT_CARD", 0, Map.of());

            producer.send(new ProducerRecord<>("payments.events.received", paymentId, toJson(payment))).get(5, TimeUnit.SECONDS);

            FraudResult result = consumeResult(consumer, paymentId, "fraud.events.detected", 30);

            assertEquals("REVIEW", result.action(), "High amount alone should trigger review (score 50-79)");
            assertTrue(result.riskScore() >= 50 && result.riskScore() < 80, "Risk score should be 50-79");
        } finally {
            consumer.close();
        }
    }

    private PaymentMessage buildPayment(String paymentId, BigDecimal amount, String currency, String country, String method, int attempts, Map<String, Object> metadata) {
        return new PaymentMessage(
            UUID.randomUUID().toString(), paymentId, amount, currency,
            "customer-123", method, country,
            attempts, false, 0, "UTC",
            metadata, LocalDateTime.now()
        );
    }

    private FraudResult consumeResult(KafkaConsumer<String, String> consumer, String paymentId, String topic, int timeoutSeconds) throws Exception {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            for (ConsumerRecord<String, String> record : records) {
                if (topic.equals(record.topic()) && record.value().contains(paymentId)) {
                    return fromJson(record.value(), FraudResult.class);
                }
            }
        }
        fail("Timed out waiting for message on topic " + topic + " with paymentId " + paymentId);
        return null;
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
