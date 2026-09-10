package com.poc.gateway.infrastructure.messaging.consumer;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
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
    private Thread consumerThread;
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
        consumerThread = new Thread(this::pollLoop, "kafka-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
    }

    @PreDestroy
    void stop() {
        running = false;
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
        if (consumer != null) {
            consumer.close();
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
                        LOG.errorf(e, "Error processing record from topic %s", record.topic());
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
