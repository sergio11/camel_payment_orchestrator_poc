package com.poc.processor.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.processor.port.outbound.AuditEventPublisherPort;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

@ApplicationScoped
public class KafkaAuditEventPublisherAdapter implements AuditEventPublisherPort {

    private static final Logger LOG = Logger.getLogger(KafkaAuditEventPublisherAdapter.class);

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @ConfigProperty(name = "kafka.topic.audit")
    String auditTopic;

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
    public void publishAudit(String paymentId, String eventType) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "paymentId", paymentId,
                "eventType", eventType,
                "timestamp", LocalDateTime.now().toString()
            ));
            ProducerRecord<String, String> record = new ProducerRecord<>(
                auditTopic,
                paymentId,
                payload
            );
            producer.send(record).get();
            LOG.debugf("Published audit event for payment %s: %s", paymentId, eventType);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to publish audit event for payment %s", paymentId);
        }
    }
}
