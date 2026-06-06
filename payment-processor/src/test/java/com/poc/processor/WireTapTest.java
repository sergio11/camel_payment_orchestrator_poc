package com.poc.processor;

import com.poc.shared.event.PaymentMessage;
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
import org.junit.jupiter.api.*;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@Testcontainers
class WireTapTest {

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
        consProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-audit-group-" + UUID.randomUUID());
        consProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        consumer = new KafkaConsumer<>(consProps);
        
        consumer.subscribe(Collections.singletonList("payments.events.audit"));
    }

    @AfterAll
    static void teardown() {
        consumer.close();
        producer.close();
        kafka.stop();
    }

    @BeforeEach
    void reset() {
        consumer.poll(java.time.Duration.ofMillis(100));
    }

    @Test
    @DisplayName("3.46: Verify Wire Tap logs to audit topic")
    void testWireTapLogsToAuditTopic() throws Exception {
        // Given: A payment sent to the main topic
        String paymentId = UUID.randomUUID().toString();
        PaymentMessage payment = new PaymentMessage(
            UUID.randomUUID().toString(),
            paymentId,
            new BigDecimal("100.00"),
            "USD",
            "customer-123",
            "CREDIT_CARD",
            "US",
            Map.of(),
            LocalDateTime.now()
        );

        String json = toJson(payment);
        ProducerRecord<String, String> record = new ProducerRecord<>("payments.events.received", paymentId, json);
        producer.send(record).get(5, TimeUnit.SECONDS);

        // When: Wait for wiretap to publish to audit topic
        String auditEvent = consumeAuditEvent(paymentId, 10);
        
        // Then: Should have audit event with same payment data
        assertNotNull(auditEvent, "Should receive audit event on payments.events.audit topic");
        assertTrue(auditEvent.contains(paymentId), "Audit event should contain paymentId");
    }

    private String consumeAuditEvent(String paymentId, int timeoutSeconds) throws InterruptedException, ExecutionException, TimeoutException {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            long timeoutMs = timeoutSeconds * 1000L;
            
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if ("payments.events.audit".equals(record.topic())) {
                        String json = record.value();
                        if (json.contains(paymentId)) {
                            return json;
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
}