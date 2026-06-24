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

    private KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    void init() {
        objectMapper.registerModule(new JavaTimeModule());
        producer = new KafkaProducer<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
            ProducerConfig.ACKS_CONFIG, "all"
        ));
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
                metadata,
                LocalDateTime.now()
            );
            String json = objectMapper.writeValueAsString(message);
            producer.send(new ProducerRecord<>(paymentsReceivedTopic, paymentId, json), (metadata2, exception) -> {
                if (exception != null) {
                    LOG.errorf(exception, "Failed to publish payment %s to Kafka", paymentId);
                } else {
                    LOG.infof("Published payment %s to Kafka topic %s (partition=%d, offset=%d)",
                        paymentId, paymentsReceivedTopic, metadata2.partition(), metadata2.offset());
                }
            });
        } catch (Exception e) {
            LOG.errorf(e, "Error serializing payment message for %s", paymentId);
        }
    }
}
