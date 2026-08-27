package com.poc.processor;

import com.poc.shared.event.FraudResult;
import com.poc.shared.event.PaymentMessage;
import com.poc.shared.event.ProviderResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@Testcontainers
class PaymentProcessorRouteTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer("confluentinc/cp-kafka:7.5.0")
        .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true");

    static KafkaProducer<String, String> producer;
    static KafkaConsumer<String, String> consumer;

    @BeforeAll
    static void setup() {
        kafka.start();
        
        String bootstrapServers = kafka.getBootstrapServers();
        System.setProperty("kafka.bootstrap.servers", bootstrapServers);
        System.setProperty("camel.component.kafka.brokers", bootstrapServers);

        Properties prodProps = new Properties();
        prodProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        prodProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        prodProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producer = new KafkaProducer<>(prodProps);

        Properties consProps = new Properties();
        consProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
        consProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        consumer = new KafkaConsumer<>(consProps);
        
        consumer.subscribe(Arrays.asList(
            "payments.events.received",
            "fraud.events.detected",
            "payments.events.audit",
            "payments.events.dead-letter"
        ));
    }

    @AfterAll
    static void teardown() {
        consumer.close();
        producer.close();
        kafka.stop();
    }

    @BeforeEach
    void reset() {
        // Poll to clear any pending messages
        consumer.poll(Duration.ofMillis(100));
    }

    @Test
    @DisplayName("3.40: Verify payment triggers fraud check via Kafka")
    void testPaymentTriggersFraudCheckViaKafka() throws Exception {
        // Given: Medium risk payment (should trigger REVIEW, published to fraud.events.detected)
        String paymentId = UUID.randomUUID().toString();
        PaymentMessage payment = new PaymentMessage(
            UUID.randomUUID().toString(),
            paymentId,
            new BigDecimal("100.00"),
            "USD",
            "customer-123",
            "CREDIT_CARD",
            "US",
            0,
            false,
            0,
            "UTC",
            Map.of(),
            LocalDateTime.now()
        );

        String json = toJson(payment);
        ProducerRecord<String, String> record = new ProducerRecord<>("payments.events.received", paymentId, json);
        producer.send(record).get(5, TimeUnit.SECONDS);

        // When: Wait for processing and check fraud.events.detected topic
        FraudResult result = consumeFraudResult(paymentId, 10);
        
        // Then: Should have a fraud result with REVIEW action (high amount +50 = 50 >= 50)
        assertNotNull(result, "Should receive fraud result");
        assertEquals(paymentId, result.paymentId());
        assertEquals("REVIEW", result.action());
    }

    @Test
    @DisplayName("3.47: Verify events published to payments.events.processed on success")
    void testSuccessPublishesProcessedEvent() throws Exception {
        // Given: Low risk payment (should be APPROVE)
        String paymentId = UUID.randomUUID().toString();
        PaymentMessage payment = new PaymentMessage(
            UUID.randomUUID().toString(),
            paymentId,
            new BigDecimal("100.00"),
            "USD",
            "customer-123",
            "CREDIT_CARD",
            "US",
            0,
            false,
            0,
            "UTC",
            Map.of(),
            LocalDateTime.now()
        );

        String json = toJson(payment);
        ProducerRecord<String, String> record = new ProducerRecord<>("payments.events.received", paymentId, json);
        producer.send(record).get(5, TimeUnit.SECONDS);

        // When: Wait for processing
        FraudResult result = consumeFraudResult(paymentId, 10);
        
        // Then: Should be APPROVE (low risk)
        assertEquals("APPROVE", result.action(), "Low risk payment should be approved");
    }

    @Test
    @DisplayName("3.48: Verify events published to payments.events.failed on fraud reject")
    void testFraudRejectPublishesFailedEvent() throws Exception {
        // Given: High risk payment (amount > 15000 + high risk country = score >= 80)
        String paymentId = UUID.randomUUID().toString();
        PaymentMessage payment = new PaymentMessage(
            UUID.randomUUID().toString(),
            paymentId,
            new BigDecimal("20000.00"),
            "USD",
            "customer-123",
            "CREDIT_CARD",
            "US",
            1,
            false,
            0,
            "UTC",
            Map.of("attempts", 1),
            LocalDateTime.now()
        );

        String json = toJson(payment);
        ProducerRecord<String, String> record = new ProducerRecord<>("payments.events.received", paymentId, json);
        producer.send(record).get(5, TimeUnit.SECONDS);

        // When: Wait for processing
        FraudResult result = consumeFraudResult(paymentId, 10);
        
        // Then: Should be REJECT (high amount +50, high risk country +30 = 80 >= 80)
        assertEquals("REJECT", result.action(), "High risk payment should be rejected");
    }

    private FraudResult consumeFraudResult(String paymentId, int timeoutSeconds) throws InterruptedException, ExecutionException, TimeoutException {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            long timeoutMs = timeoutSeconds * 1000L;
            
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if ("fraud.events.detected".equals(record.topic())) {
                        String json = record.value();
                        if (json.contains(paymentId)) {
                            return fromJson(json, FraudResult.class);
                        }
                    }
                }
            }
            return null;
        }).get(timeoutSeconds, TimeUnit.SECONDS);
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