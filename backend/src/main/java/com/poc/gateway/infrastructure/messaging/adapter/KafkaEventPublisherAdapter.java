package com.poc.gateway.infrastructure.messaging.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.poc.gateway.domain.Payment;
import com.poc.gateway.domain.PaymentMetadata;
import com.poc.gateway.domain.port.outbound.EventPublisherPort;
import com.poc.gateway.domain.port.outbound.PaymentEventSerializer;
import com.poc.gateway.infrastructure.messaging.config.KafkaTopicConfig;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jboss.logging.Logger;

@ApplicationScoped
public class KafkaEventPublisherAdapter implements EventPublisherPort {

    private static final Logger LOG = Logger.getLogger(KafkaEventPublisherAdapter.class);

    @Inject
    KafkaTopicConfig topicConfig;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    PaymentEventSerializer serializer;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    private KafkaProducer<String, String> producer;

    @PostConstruct
    void init() {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("acks", "all");
        props.put("retries", 3);
        props.put("linger.ms", 5);
        props.put("batch.size", 16384);
        props.put("buffer.memory", 33554432);
        props.put("max.in.flight.requests.per.connection", 5);
        props.put("delivery.timeout.ms", 120000);
        producer = new KafkaProducer<>(props);
    }

    @PreDestroy
    void close() {
        if (producer != null) {
            producer.close();
        }
    }

    @Override
    public boolean publishPaymentReceived(String paymentId, BigDecimal amount, String currency,
            String customerId, String paymentMethod, String country, PaymentMetadata metadata) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("paymentId", paymentId);
            payload.put("amount", amount != null ? amount.toString() : "0");
            payload.put("currency", currency);
            payload.put("customerId", customerId);
            payload.put("paymentMethod", paymentMethod);
            payload.put("country", country);
            if (metadata != null) {
                payload.set("metadata", objectMapper.valueToTree(metadata));
            }

            ProducerRecord<String, String> record = new ProducerRecord<>(
                topicConfig.received(),
                paymentId,
                objectMapper.writeValueAsString(payload)
            );
            producer.send(record).get(30, java.util.concurrent.TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to publish payment received event for %s", paymentId);
            return false;
        }
    }

    @Override
    public boolean publishStatusChanged(String paymentId, String status) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "paymentId", paymentId,
                "status", status
            ));

            ProducerRecord<String, String> record = new ProducerRecord<>(
                topicConfig.statusChanged(),
                paymentId,
                payload
            );
            producer.send(record).get(30, java.util.concurrent.TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to publish status changed event for %s", paymentId);
            return false;
        }
    }

    @Override
    public boolean publishDeadLetter(String paymentId, String payload, String reason) {
        try {
            ObjectNode dlqPayload = objectMapper.createObjectNode();
            dlqPayload.put("paymentId", paymentId);
            dlqPayload.put("originalPayload", payload);
            dlqPayload.put("reason", reason);

            ProducerRecord<String, String> record = new ProducerRecord<>(
                topicConfig.deadLetter(),
                paymentId,
                objectMapper.writeValueAsString(dlqPayload)
            );
            producer.send(record).get(30, java.util.concurrent.TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to publish dead letter event for %s", paymentId);
            return false;
        }
    }
}
