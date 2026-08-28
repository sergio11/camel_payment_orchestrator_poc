package com.poc.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.poc.shared.event.PaymentMessage;
import jakarta.enterprise.context.ApplicationScoped;
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
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class KafkaEventPublisher {

    private static final Logger LOG = Logger.getLogger(KafkaEventPublisher.class);

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @ConfigProperty(name = "kafka.topic.payments.received")
    String paymentsReceivedTopic;

    private volatile KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @jakarta.annotation.PostConstruct
    void init() {
        objectMapper.registerModule(new JavaTimeModule());
    }

    private KafkaProducer<String, String> getProducer() {
        if (producer == null) {
            synchronized (this) {
                if (producer == null) {
                    producer = new KafkaProducer<>(Map.of(
                        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                        ProducerConfig.ACKS_CONFIG, "all",
                        ProducerConfig.MAX_BLOCK_MS_CONFIG, "5000",
                        ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "10000",
                        ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000"
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

    public void publishPaymentReceived(String paymentId, BigDecimal amount, String currency,
                                        String customerId, String paymentMethod, String country,
                                        Map<String, Object> metadata) {
        try {
            PaymentMessage message = new PaymentMessage(
                UUID.randomUUID().toString(),
                paymentId,
                amount,
                currency,
                customerId,
                paymentMethod,
                country,
                0,
                false,
                0,
                "UTC",
                metadata,
                LocalDateTime.now()
            );
            String json = objectMapper.writeValueAsString(message);
            getProducer().send(new ProducerRecord<>(paymentsReceivedTopic, paymentId, json), (recordMetadata, exception) -> {
                if (exception != null) {
                    LOG.errorf(exception, "Failed to publish payment %s to Kafka", paymentId);
                } else {
                    LOG.infof("Published payment %s to Kafka topic %s (partition=%d, offset=%d)",
                        paymentId, paymentsReceivedTopic, recordMetadata.partition(), recordMetadata.offset());
                }
            });
        } catch (Exception e) {
            LOG.errorf(e, "Error serializing payment message for %s", paymentId);
        }
    }
}
