package com.poc.processor.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.poc.processor.port.outbound.FraudDetectionPublisherPort;
import com.poc.processor.domain.FraudEvaluation;
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
public class KafkaFraudDetectionPublisherAdapter implements FraudDetectionPublisherPort {

    private static final Logger LOG = Logger.getLogger(KafkaFraudDetectionPublisherAdapter.class);

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @ConfigProperty(name = "kafka.topic.fraud.detected")
    String fraudDetectedTopic;

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
    public void publishFraudDetected(FraudEvaluation evaluation) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("paymentId", evaluation.paymentId());
            payload.put("amount", evaluation.amount() != null ? evaluation.amount().toString() : "0");
            payload.put("customerId", evaluation.customerId());
            payload.put("riskScore", evaluation.riskScore());
            payload.put("action", evaluation.action());
            payload.put("reason", evaluation.reason());
            payload.set("triggeredRules", objectMapper.valueToTree(evaluation.triggeredRules()));

            ProducerRecord<String, String> record = new ProducerRecord<>(
                fraudDetectedTopic,
                evaluation.paymentId(),
                objectMapper.writeValueAsString(payload)
            );
            producer.send(record).get();
            LOG.debugf("Published fraud detected event for payment %s with action %s",
                evaluation.paymentId(), evaluation.action());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to publish fraud detected event for payment %s", evaluation.paymentId());
        }
    }
}
