package com.poc.gateway.infrastructure.messaging.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.poc.gateway.domain.model.PaymentReceivedEvent;
import com.poc.gateway.domain.port.outbound.EventPublisherPort;
import com.poc.gateway.domain.port.outbound.PaymentEventSerializer;
import com.poc.gateway.infrastructure.messaging.config.KafkaTopicConfig;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
    public boolean publishPaymentReceived(PaymentReceivedEvent event) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("paymentId", event.paymentId());
            payload.put("amount", event.amount() != null ? event.amount().toString() : "0");
            payload.put("currency", event.currency());
            payload.put("customerId", event.customerId());
            payload.put("paymentMethod", event.paymentMethod());
            payload.put("country", event.country());
            if (event.metadata() != null) {
                payload.set("metadata", objectMapper.valueToTree(event.metadata()));
            }

            ProducerRecord<String, String> record = new ProducerRecord<>(
                topicConfig.received(),
                event.paymentId(),
                objectMapper.writeValueAsString(payload)
            );
            producer.send(record).get(30, java.util.concurrent.TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to publish payment received event for %s", event.paymentId());
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
