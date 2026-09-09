package com.poc.processor;

import com.poc.shared.event.FraudResult;
import com.poc.shared.event.PaymentMessage;
import com.poc.shared.event.ProviderResponse;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import com.poc.processor.route.PaymentProcessorRoute;
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
    CamelContext camelContext;

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

            ProviderResponse result = consumeProviderResponse(consumer, paymentId, "payments.events.processed", 30);

            assertNotNull(result, "Should receive provider response");
            assertTrue(result.success(), "Low risk payment should succeed");
            assertNotNull(result.transactionId(), "Provider response should carry a transactionId");
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

            ProviderResponse result = consumeProviderResponse(consumer, paymentId, "payments.events.processed", 30);

            assertTrue(result.success(), "Low risk payment should be approved");
            assertEquals("provider-a", result.providerId(), "First provider should be provider-a");
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
            // Base score 45 (RAPID_RETRY 25 + NEW_PAYMENT_METHOD 20): APPROVE without
            // enrichment, REVIEW once the enriched HIGH customerRiskTier (+10) is added.
            String customerId = highRiskTierCustomer();
            PaymentMessage payment = new PaymentMessage(
                UUID.randomUUID().toString(), paymentId, new BigDecimal("100.00"), "USD",
                customerId, "CREDIT_CARD", "US", 0, false, 0, hour12Zone(),
                Map.of("attempts", 4, "isNewPaymentMethod", true, "paymentMethodAgeDays", 10),
                LocalDateTime.now()
            );

            producer.send(new ProducerRecord<>("payments.events.received", paymentId, toJson(payment))).get(5, TimeUnit.SECONDS);

            FraudResult result = consumeResult(consumer, paymentId, "fraud.events.detected", 30);

            assertNotNull(result, "Should receive fraud result");
            assertEquals("REVIEW", result.action(), "Enriched HIGH risk tier should push score 45 -> 55 (REVIEW)");
            assertTrue(result.triggeredRules().contains("HIGH_RISK_TIER"),
                "HIGH_RISK_TIER proves enrichment ran before fraud evaluation, got: " + result.triggeredRules());
        } finally {
            consumer.close();
        }
    }

    private static String highRiskTierCustomer() {
        String customerId = "cust-tier";
        while (Math.floorMod(customerId.hashCode(), 3) != 2) {
            customerId += "x";
        }
        return customerId;
    }

    private static String hour12Zone() {
        int currentHour = java.time.LocalTime.now(java.time.ZoneId.of("UTC")).getHour();
        int desiredOffset = ((12 - currentHour) % 24 + 24) % 24;
        if (desiredOffset > 14) desiredOffset -= 24;
        return desiredOffset == 0 ? "UTC" : desiredOffset > 0 ? "Etc/GMT-" + desiredOffset : "Etc/GMT+" + (-desiredOffset);
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

    private ProviderResponse consumeProviderResponse(KafkaConsumer<String, String> consumer, String paymentId, String topic, int timeoutSeconds) throws Exception {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            for (ConsumerRecord<String, String> record : records) {
                if (topic.equals(record.topic()) && paymentId.equals(record.key())) {
                    return fromJson(record.value(), ProviderResponse.class);
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

    @Test
    @DisplayName("Verify restorePaymentIdFromKafkaKey covers all branches")
    void testRestorePaymentIdFromKafkaKey() {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader("OriginalPaymentId", "pid-1");
        exchange.getMessage().setHeader("kafka.KEY", "key-1");
        PaymentProcessorRoute.restorePaymentIdFromKafkaKey(exchange);
        assertEquals("pid-1", exchange.getMessage().getHeader("OriginalPaymentId"));

        Exchange exchange2 = new DefaultExchange(camelContext);
        PaymentProcessorRoute.restorePaymentIdFromKafkaKey(exchange2);
        assertNull(exchange2.getMessage().getHeader("OriginalPaymentId"));

        Exchange exchange3 = new DefaultExchange(camelContext);
        exchange3.getMessage().setHeader("kafka.KEY", "key-3");
        PaymentProcessorRoute.restorePaymentIdFromKafkaKey(exchange3);
        assertEquals("key-3", exchange3.getMessage().getHeader("OriginalPaymentId"));
    }

    @Test
    @DisplayName("Verify validatePaymentMessage covers all null checks")
    void testValidatePaymentMessage() {
        PaymentMessage valid = new PaymentMessage("e1", "p1", BigDecimal.TEN, "USD", "c1", "CARD", "US", 0, false, 0, "UTC", Map.of(), LocalDateTime.now());
        PaymentProcessorRoute.validatePaymentMessage(valid);

        assertThrows(IllegalArgumentException.class, () ->
            PaymentProcessorRoute.validatePaymentMessage(new PaymentMessage("e1", null, BigDecimal.TEN, "USD", "c1", "CARD", "US", 0, false, 0, "UTC", Map.of(), LocalDateTime.now())));
        assertThrows(IllegalArgumentException.class, () ->
            PaymentProcessorRoute.validatePaymentMessage(new PaymentMessage("e1", "p1", null, "USD", "c1", "CARD", "US", 0, false, 0, "UTC", Map.of(), LocalDateTime.now())));
        assertThrows(IllegalArgumentException.class, () ->
            PaymentProcessorRoute.validatePaymentMessage(new PaymentMessage("e1", "p1", BigDecimal.TEN, null, "c1", "CARD", "US", 0, false, 0, "UTC", Map.of(), LocalDateTime.now())));
        assertThrows(IllegalArgumentException.class, () ->
            PaymentProcessorRoute.validatePaymentMessage(new PaymentMessage("e1", "p1", BigDecimal.TEN, "USD", null, "CARD", "US", 0, false, 0, "UTC", Map.of(), LocalDateTime.now())));
        assertThrows(IllegalArgumentException.class, () ->
            PaymentProcessorRoute.validatePaymentMessage(new PaymentMessage("e1", "p1", BigDecimal.TEN, "USD", "c1", null, "US", 0, false, 0, "UTC", Map.of(), LocalDateTime.now())));
    }
}
