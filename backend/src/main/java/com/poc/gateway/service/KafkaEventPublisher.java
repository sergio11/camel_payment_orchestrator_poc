package com.poc.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.shared.event.PaymentMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@ApplicationScoped
public class KafkaEventPublisher {

    private static final Logger LOG = Logger.getLogger(KafkaEventPublisher.class);

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @ConfigProperty(name = "kafka.topic.payments.received")
    String paymentsReceivedTopic;

    @ConfigProperty(name = "kafka.topic.payments.status.changed")
    String statusChangedTopic;

    @Inject
    ObjectMapper objectMapper;

    private volatile KafkaProducer<String, String> producer;

    private KafkaProducer<String, String> getProducer() {
        if (producer == null) {
            synchronized (this) {
                if (producer == null) {
                    producer = new KafkaProducer<>(Map.of(
                        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                        ProducerConfig.ACKS_CONFIG, "all",
                        ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
                        ProducerConfig.RETRIES_CONFIG, 3,
                        ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 15000,
                        ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000,
                        ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000
                    ));
                }
            }
        }
        return producer;
    }

    @PreDestroy
    void cleanup() {
        if (producer != null) {
            producer.close();
        }
    }

    public boolean publishPaymentReceived(String paymentId, BigDecimal amount, String currency,
                                    String customerId, String paymentMethod, String country,
                                    Map<String, Object> metadata) {
        try {
            PaymentMessage message = new PaymentMessage(
                UUID.randomUUID().toString(),
                paymentId, amount, currency, customerId, paymentMethod, country,
                0, false, 0, "UTC",
                metadata,
                LocalDateTime.now(ZoneOffset.UTC)
            );
            String json = objectMapper.writeValueAsString(message);
            ProducerRecord<String, String> record = new ProducerRecord<>(paymentsReceivedTopic, paymentId, json);
            getProducer().send(record, (recordMetadata, exception) -> {
                if (exception != null) {
                    LOG.errorf(exception, "Async callback: Failed to publish payment %s to Kafka", paymentId);
                } else {
                    LOG.infof("Published payment %s to Kafka topic %s (partition=%d, offset=%d)",
                        paymentId, paymentsReceivedTopic, recordMetadata.partition(), recordMetadata.offset());
                }
            }).get(10, TimeUnit.SECONDS);
            return true;
        } catch (TimeoutException e) {
            LOG.errorf(e, "Timeout publishing payment %s to Kafka", paymentId);
            return false;
        } catch (Exception e) {
            LOG.errorf(e, "Error publishing payment %s to Kafka", paymentId);
            return false;
        }
    }

    public void publishStatusChanged(String paymentId, String previousStatus, String newStatus) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "paymentId", paymentId,
                "previousStatus", previousStatus,
                "newStatus", newStatus,
                "timestamp", LocalDateTime.now(ZoneOffset.UTC).toString()
            ));
            getProducer().send(new ProducerRecord<>(statusChangedTopic, paymentId, payload),
                (metadata, exception) -> {
                    if (exception != null) {
                        LOG.errorf(exception, "Failed to publish status changed for %s", paymentId);
                    } else {
                        LOG.infof("Published status changed for %s: %s -> %s", paymentId, previousStatus, newStatus);
                    }
                });
        } catch (Exception e) {
            LOG.errorf(e, "Error serializing status changed for %s", paymentId);
        }
    }
}
