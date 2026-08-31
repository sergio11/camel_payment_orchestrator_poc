package com.poc.processor;

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
class WireTapTest {

    @Inject
    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    static KafkaProducer<String, String> producer;
    private String resolvedBootstrapServers;

    void resolveOnce() {
        if (resolvedBootstrapServers == null) {
            resolvedBootstrapServers = bootstrapServers;
            Properties prodProps = new Properties();
            prodProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, resolvedBootstrapServers);
            prodProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            prodProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            producer = new KafkaProducer<>(prodProps);
        }
    }

    @AfterAll
    static void teardown() {
        if (producer != null) {
            producer.close();
        }
    }

    private KafkaConsumer<String, String> createConsumer(String groupId) {
        Properties consProps = new Properties();
        consProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, resolvedBootstrapServers);
        consProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        consProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        KafkaConsumer<String, String> c = new KafkaConsumer<>(consProps);
        c.subscribe(Collections.singletonList("payments.events.audit"));
        return c;
    }

    @Test
    @DisplayName("3.46: Verify Wire Tap logs to audit topic")
    void testWireTapLogsToAuditTopic() throws Exception {
        resolveOnce();
        KafkaConsumer<String, String> consumer = createConsumer("test-audit-" + UUID.randomUUID());
        try {
            String paymentId = UUID.randomUUID().toString();
            PaymentMessage payment = new PaymentMessage(
                UUID.randomUUID().toString(), paymentId,
                new BigDecimal("100.00"), "USD", "customer-123",
                "CREDIT_CARD", "US", 0, false, 0, "UTC",
                Map.of(), LocalDateTime.now()
            );

            producer.send(new ProducerRecord<>("payments.events.received", paymentId, toJson(payment))).get(5, TimeUnit.SECONDS);

            String auditEvent = consumeAuditEvent(consumer, paymentId, 30);

            assertNotNull(auditEvent, "Should receive audit event on payments.events.audit topic");
            assertTrue(auditEvent.contains(paymentId), "Audit event should contain paymentId");
        } finally {
            consumer.close();
        }
    }

    private String consumeAuditEvent(KafkaConsumer<String, String> consumer, String paymentId, int timeoutSeconds) throws Exception {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            for (ConsumerRecord<String, String> record : records) {
                if ("payments.events.audit".equals(record.topic()) && record.value().contains(paymentId)) {
                    return record.value();
                }
            }
        }
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
}
