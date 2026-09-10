package com.poc.processor.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.poc.processor.port.outbound.PaymentEventPublisherPort;
import com.poc.shared.event.PaymentMessage;
import com.poc.shared.event.ProviderResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

@ApplicationScoped
public class KafkaPaymentEventPublisherAdapter implements PaymentEventPublisherPort {

    private static final Logger LOG = Logger.getLogger(KafkaPaymentEventPublisherAdapter.class);

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @ConfigProperty(name = "kafka.topic.payments.processed")
    String processedTopic;

    @ConfigProperty(name = "kafka.topic.payments.failed")
    String failedTopic;

    @ConfigProperty(name = "kafka.topic.payments.retry")
    String retryTopic;

    @ConfigProperty(name = "kafka.topic.dead.letter")
    String deadLetterTopic;

    private KafkaProducer<String, String> producer;

    @PostConstruct
    void init() {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("acks", "all");
        props.put("retries", 3);
        producer = new KafkaProducer<>(props);
    }

    @PreDestroy
    void close() {
        if (producer != null) {
            producer.close();
        }
    }

    @Override
    public void publishProcessed(PaymentMessage originalMessage, ProviderResponse providerResponse) {
        try {
            String payload = objectMapper.writeValueAsString(providerResponse);
            ProducerRecord<String, String> record = new ProducerRecord<>(
                processedTopic,
                originalMessage.paymentId(),
                payload
            );
            producer.send(record).get();
            LOG.debugf("Published processed event for payment %s", originalMessage.paymentId());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to publish processed event for payment %s", originalMessage.paymentId());
        }
    }

    @Override
    public void publishFailed(PaymentMessage originalMessage, String reason) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("paymentId", originalMessage.paymentId());
            payload.put("amount", originalMessage.amount() != null ? originalMessage.amount().toString() : "0");
            payload.put("currency", originalMessage.currency());
            payload.put("customerId", originalMessage.customerId());
            payload.put("paymentMethod", originalMessage.paymentMethod());
            payload.put("country", originalMessage.country());
            payload.put("failureReason", reason);

            ProducerRecord<String, String> record = new ProducerRecord<>(
                failedTopic,
                originalMessage.paymentId(),
                objectMapper.writeValueAsString(payload)
            );
            producer.send(record).get();
            LOG.debugf("Published failed event for payment %s", originalMessage.paymentId());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to publish failed event for payment %s", originalMessage.paymentId());
        }
    }

    @Override
    public void publishRetry(PaymentMessage originalMessage) {
        try {
            String payload = objectMapper.writeValueAsString(originalMessage);
            ProducerRecord<String, String> record = new ProducerRecord<>(
                retryTopic,
                originalMessage.paymentId(),
                payload
            );
            producer.send(record).get();
            LOG.debugf("Published retry event for payment %s", originalMessage.paymentId());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to publish retry event for payment %s", originalMessage.paymentId());
        }
    }

    @Override
    public void publishDeadLetter(PaymentMessage originalMessage, String reason) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("paymentId", originalMessage.paymentId());
            payload.put("originalPayload", objectMapper.valueToTree(originalMessage));
            payload.put("reason", reason);

            ProducerRecord<String, String> record = new ProducerRecord<>(
                deadLetterTopic,
                originalMessage.paymentId(),
                objectMapper.writeValueAsString(payload)
            );
            producer.send(record).get();
            LOG.debugf("Published dead letter event for payment %s", originalMessage.paymentId());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to publish dead letter event for payment %s", originalMessage.paymentId());
        }
    }
}
