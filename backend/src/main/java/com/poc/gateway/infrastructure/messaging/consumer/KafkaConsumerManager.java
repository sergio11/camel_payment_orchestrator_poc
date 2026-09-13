package com.poc.gateway.infrastructure.messaging.consumer;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.jboss.logging.Logger;

@ApplicationScoped
public class KafkaConsumerManager {

    private static final Logger LOG = Logger.getLogger(KafkaConsumerManager.class);

    @Inject
    PaymentStatusRouter router;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @ConfigProperty(name = "kafka.topic.payments.processed")
    String processedTopic;

    @ConfigProperty(name = "kafka.group.id", defaultValue = "payment-status-consumer")
    String groupId;

    private KafkaConsumer<String, String> consumer;
    private ExecutorService executor;
    private volatile boolean running;

    @PostConstruct
    void start() {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("group.id", groupId);
        props.put("enable.auto.commit", "false");
        props.put("auto.offset.reset", "earliest");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(processedTopic));

        running = true;
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "kafka-consumer");
            t.setDaemon(true);
            return t;
        });
        executor.submit(this::pollLoop);
    }

    @PreDestroy
    void stop() {
        running = false;
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    executor.awaitTermination(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (consumer != null) {
            try {
                consumer.close();
            } catch (Exception e) {
                LOG.warnf(e, "Error closing Kafka consumer");
            }
        }
    }

    private void pollLoop() {
        while (running) {
            try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                records.forEach(record -> {
                    try {
                        router.route(record);
                    } catch (Exception e) {
                        LOG.errorf(e, "Error processing record from topic %s partition %d offset %d",
                            record.topic(), record.partition(), record.offset());
                    }
                });
                consumer.commitSync();
            } catch (Exception e) {
                if (running) {
                    LOG.errorf(e, "Error in consumer poll loop");
                }
            }
        }
    }
}
