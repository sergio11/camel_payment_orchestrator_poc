package com.poc.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.gateway.entity.PaymentStatus;
import com.poc.gateway.repository.PaymentRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.Startup;import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
@Startup
public class KafkaPaymentStatusConsumer {

    private static final Logger LOG = Logger.getLogger(KafkaPaymentStatusConsumer.class);

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @ConfigProperty(name = "kafka.topic.payments.processed")
    String paymentsProcessedTopic;

    @ConfigProperty(name = "kafka.topic.payments.failed")
    String paymentsFailedTopic;

    @ConfigProperty(name = "kafka.topic.payments.review", defaultValue = "payments.events.review")
    String paymentsReviewTopic;

    @Inject
    PaymentRepository repository;

    @Inject
    KafkaEventPublisher kafkaEventPublisher;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Instance<MeterRegistry> meterRegistries;

    private volatile KafkaConsumer<String, String> consumer;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private Thread consumerThread;

    @PostConstruct
    void init() {
        startConsumerThread();
    }

    @PreDestroy
    void stop() {
        running.set(false);
        if (consumer != null) {
            consumer.wakeup();
        }
    }

    private void startConsumerThread() {
        consumerThread = new Thread(() -> {
            while (running.get()) {
                try {
                    consumeLoop();
                } catch (Exception e) {
                    LOG.errorf(e, "Consumer thread died unexpectedly");
                    if (running.get()) {
                        LOG.info("Restarting consumer thread in 5 seconds...");
                        try { Thread.sleep(5000); } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }, "payment-status-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
    }

    private void consumeLoop() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "payment-gateway-status-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList(paymentsProcessedTopic, paymentsFailedTopic, paymentsReviewTopic));

        LOG.info("Payment status consumer started, listening to: " + paymentsProcessedTopic + ", " + paymentsFailedTopic + ", " + paymentsReviewTopic);

        try {
            while (running.get()) {
                try {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    Map<TopicPartition, OffsetAndMetadata> offsetsToCommit = new HashMap<>();
                    for (ConsumerRecord<String, String> record : records) {
                        try {
                            processRecord(record);
                            offsetsToCommit.put(
                                new TopicPartition(record.topic(), record.partition()),
                                new OffsetAndMetadata(record.offset() + 1));
                        } catch (TransientConsumerException e) {
                            LOG.errorf(e, "Transient error for record topic %s partition %d offset %d key %s, NOT committing (will be redelivered)",
                                record.topic(), record.partition(), record.offset(), record.key());
                            incrementCounter("payment.consumer.retry", record.topic());
                            break;
                        } catch (Exception e) {
                            LOG.errorf(e, "Error processing record from topic %s partition %d offset %d key %s, routing poison to DLQ",
                                record.topic(), record.partition(), record.offset(), record.key());
                            routePoisonToDlq(record, e.getMessage());
                            offsetsToCommit.put(
                                new TopicPartition(record.topic(), record.partition()),
                                new OffsetAndMetadata(record.offset() + 1));
                        }
                    }
                    if (!offsetsToCommit.isEmpty()) {
                        consumer.commitSync(offsetsToCommit);
                    }
                } catch (org.apache.kafka.common.errors.WakeupException e) {
                    if (running.get()) {
                        LOG.warn("Consumer wakeup received");
                    }
                    break;
                } catch (Exception e) {
                    LOG.errorf(e, "Consumer loop error, retrying in 5s");
                    try { Thread.sleep(5000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } finally {
            if (consumer != null) {
                consumer.close();
            }
        }
    }

    private void processRecord(ConsumerRecord<String, String> record) throws Exception {
        String paymentId = record.key();
        if (paymentId == null) {
            routePoisonToDlq(record, "null key");
            return;
        }

        java.util.UUID uuid;
        try {
            uuid = java.util.UUID.fromString(paymentId);
        } catch (IllegalArgumentException e) {
            routePoisonToDlq(record, "invalid UUID: " + paymentId);
            return;
        }

        PaymentStatus newStatus;
        if (paymentsProcessedTopic.equals(record.topic())) {
            newStatus = PaymentStatus.APPROVED;
        } else if (paymentsFailedTopic.equals(record.topic())) {
            newStatus = PaymentStatus.FAILED;
        } else if (paymentsReviewTopic.equals(record.topic())) {
            newStatus = PaymentStatus.REVIEW;
        } else {
            routePoisonToDlq(record, "unexpected topic: " + record.topic());
            return;
        }

        Optional<com.poc.gateway.entity.Payment> paymentOpt;
        try {
            paymentOpt = repository.findById(uuid);
        } catch (Exception e) {
            throw new TransientConsumerException("DB lookup failed for " + paymentId, e);
        }
        if (paymentOpt.isEmpty()) {
            routePoisonToDlq(record, "payment not found: " + paymentId);
            return;
        }

        Optional<com.poc.gateway.entity.Payment> updated;
        try {
            updated = repository.updateIfPending(uuid, newStatus);
        } catch (Exception e) {
            throw new TransientConsumerException("DB update failed for " + paymentId, e);
        }
        if (updated.isEmpty()) {
            LOG.warnf("Skipping status transition for payment %s: already transitioned (only PENDING may transition)", paymentId);
            incrementCounter("payment.consumer.skipped", record.topic());
            return;
        }

        LOG.infof("Payment %s %s", paymentId, newStatus);
        boolean statusPublished;
        try {
            statusPublished = kafkaEventPublisher.publishStatusChanged(paymentId, PaymentStatus.PENDING.name(), newStatus.name());
        } catch (Exception e) {
            throw new TransientConsumerException("status-changed publish failed for " + paymentId, e);
        }
        if (!statusPublished) {
            throw new TransientConsumerException("status-changed publish returned false for " + paymentId, null);
        }
    }

    private void routePoisonToDlq(ConsumerRecord<String, String> record, String reason) {
        LOG.warnf("Poison record topic=%s partition=%d offset=%d key=%s reason=%s, routing to DLQ",
            record.topic(), record.partition(), record.offset(), record.key(), reason);
        incrementCounter("payment.consumer.poison", record.topic());
        boolean published = false;
        try {
            published = kafkaEventPublisher.publishDeadLetter(record.key(), record.topic(), reason, record.value());
        } catch (Exception e) {
            LOG.errorf(e, "DLQ publish threw for key %s", record.key());
        }
        if (published) {
            incrementCounter("payment.consumer.dlq", record.topic());
        } else {
            LOG.errorf("DLQ publish failed for key %s reason=%s, offset still committed to avoid infinite loop", record.key(), reason);
            incrementCounter("payment.consumer.dlqFailed", record.topic());
        }
    }

    private void incrementCounter(String name, String topic) {
        try {
            if (meterRegistries != null && !meterRegistries.isUnsatisfied()) {
                meterRegistries.get().counter(name, "topic", String.valueOf(topic)).increment();
            } else {
                LOG.debugf("counter %s{topic=%s} +1", name, topic);
            }
        } catch (Exception e) {
            LOG.debugf("counter %s{topic=%s} +1 (MeterRegistry unavailable)", name, topic);
        }
    }

    static class TransientConsumerException extends Exception {
        TransientConsumerException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
