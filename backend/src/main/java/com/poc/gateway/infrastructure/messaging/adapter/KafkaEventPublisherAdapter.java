package com.poc.gateway.infrastructure.messaging.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.poc.gateway.domain.model.PaymentReceivedEvent;
import com.poc.gateway.domain.port.outbound.EventPublisherPort;
import com.poc.gateway.domain.port.outbound.PaymentEventSerializer;
import com.poc.gateway.infrastructure.messaging.config.KafkaTopicConfig;
import com.poc.shared.util.KafkaConfigHelper;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jboss.logging.Logger;

@ApplicationScoped
public class KafkaEventPublisherAdapter implements EventPublisherPort {

    private static final Logger LOG = Logger.getLogger(KafkaEventPublisherAdapter.class);
    private static final int SEND_TIMEOUT_SECONDS = 30;
    private static final String FIELD_PAYMENT_ID = "paymentId";
    private static final String FIELD_ORIGINAL_PAYLOAD = "originalPayload";

    @Inject
    private KafkaTopicConfig topicConfig;

    @Inject
    private ObjectMapper objectMapper;

    @Inject
    private PaymentEventSerializer serializer;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    private KafkaProducer<String, String> producer;

    @PostConstruct
    void init() {
        producer = new KafkaProducer<>(KafkaConfigHelper.producerConfig(bootstrapServers));
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
            payload.put(FIELD_PAYMENT_ID, event.paymentId());
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
            producer.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
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
                FIELD_PAYMENT_ID, paymentId,
                "status", status
            ));

            ProducerRecord<String, String> record = new ProducerRecord<>(
                topicConfig.statusChanged(),
                paymentId,
                payload
            );
            producer.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
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
            dlqPayload.put(FIELD_PAYMENT_ID, paymentId);
            dlqPayload.put(FIELD_ORIGINAL_PAYLOAD, payload);
            dlqPayload.put("reason", reason);

            ProducerRecord<String, String> record = new ProducerRecord<>(
                topicConfig.deadLetter(),
                paymentId,
                objectMapper.writeValueAsString(dlqPayload)
            );
            producer.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to publish dead letter event for %s", paymentId);
            return false;
        }
    }
}
