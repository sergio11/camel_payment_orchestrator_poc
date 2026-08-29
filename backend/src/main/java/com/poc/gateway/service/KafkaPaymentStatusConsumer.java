package com.poc.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.poc.gateway.entity.PaymentStatus;
import com.poc.gateway.repository.PaymentRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class KafkaPaymentStatusConsumer {

    private static final Logger LOG = Logger.getLogger(KafkaPaymentStatusConsumer.class);

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @ConfigProperty(name = "kafka.topic.payments.processed")
    String paymentsProcessedTopic;

    @ConfigProperty(name = "kafka.topic.payments.failed")
    String paymentsFailedTopic;

    @Inject
    PaymentRepository repository;

    @Inject
    KafkaEventPublisher kafkaEventPublisher;

    private volatile KafkaConsumer<String, String> consumer;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private Thread consumerThread;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    void init() {
        objectMapper.registerModule(new JavaTimeModule());
        consumerThread = new Thread(this::consume, "payment-status-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
    }

    @PreDestroy
    void stop() {
        running.set(false);
        if (consumer != null) {
            consumer.wakeup();
        }
    }

    private void consume() {
        try {
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "payment-gateway-status-group");
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

            consumer = new KafkaConsumer<>(props);
            consumer.subscribe(Arrays.asList(paymentsProcessedTopic, paymentsFailedTopic));

            LOG.info("Payment status consumer started, listening to: " + paymentsProcessedTopic + ", " + paymentsFailedTopic);

            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        processRecord(record);
                    } catch (Exception e) {
                        LOG.errorf(e, "Error processing record from topic %s", record.topic());
                    }
                }
            }
        } catch (Exception e) {
            LOG.errorf(e, "Payment status consumer error");
        } finally {
            if (consumer != null) {
                consumer.close();
            }
        }
    }

    private void processRecord(ConsumerRecord<String, String> record) throws Exception {
        String paymentId = record.key();
        if (paymentId == null) {
            LOG.warn("Received record with null paymentId, skipping");
            return;
        }

        java.util.UUID uuid;
        try {
            uuid = java.util.UUID.fromString(paymentId);
        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid UUID format for paymentId: %s", paymentId);
            return;
        }

        Optional<com.poc.gateway.entity.Payment> paymentOpt = repository.findById(uuid);
        if (paymentOpt.isEmpty()) {
            LOG.warnf("Payment not found: %s", paymentId);
            return;
        }

        com.poc.gateway.entity.Payment payment = paymentOpt.get();
        PaymentStatus newStatus;

        if (paymentsProcessedTopic.equals(record.topic())) {
            newStatus = PaymentStatus.APPROVED;
            LOG.infof("Payment %s APPROVED", paymentId);
        } else {
            newStatus = PaymentStatus.FAILED;
            LOG.infof("Payment %s FAILED", paymentId);
        }

        String previousStatus = payment.status().name();
        com.poc.gateway.entity.Payment updated = payment.withStatus(newStatus);
        repository.save(updated);
        kafkaEventPublisher.publishStatusChanged(paymentId, previousStatus, newStatus.name());
    }
}
