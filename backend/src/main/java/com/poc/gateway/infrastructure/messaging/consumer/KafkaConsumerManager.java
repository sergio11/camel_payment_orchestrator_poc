package com.poc.gateway.infrastructure.messaging.consumer;

import com.poc.shared.util.KafkaConfigHelper;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.TracingConsumerInterceptor;
import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Arrays;
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
    private PaymentStatusRouter router;

    @Inject
    private OpenTelemetry openTelemetry;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @ConfigProperty(name = "kafka.topic.payments.processed", defaultValue = "payments.events.processed")
    String processedTopic;

    @ConfigProperty(name = "kafka.topic.payments.failed", defaultValue = "payments.events.failed")
    String failedTopic;

    @ConfigProperty(name = "kafka.topic.payments.review", defaultValue = "payments.events.review")
    String reviewTopic;

    @ConfigProperty(name = "kafka.group.id", defaultValue = "payment-status-consumer")
    String groupId;

    private KafkaConsumer<String, String> consumer;
    private ExecutorService executor;
    private volatile boolean running;

    void onStart(@Observes StartupEvent ev) {
        try {
            LOG.info("KafkaConsumerManager: Initializing Kafka consumer...");
            LOG.infof("KafkaConsumerManager: Bootstrap=%s, GroupId=%s", bootstrapServers, groupId);
            var props = KafkaConfigHelper.consumerConfig(bootstrapServers, groupId);
            props.put("interceptor.classes", TracingConsumerInterceptor.class.getName());
            consumer = new KafkaConsumer<>(props);
            consumer.subscribe(Arrays.asList(processedTopic, failedTopic, reviewTopic));
            LOG.infof("KafkaConsumerManager: Subscribed to topics: %s, %s, %s", processedTopic, failedTopic, reviewTopic);

            running = true;
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "kafka-status-consumer");
                t.setDaemon(true);
                return t;
            });
            executor.submit(this::pollLoop);
            LOG.info("KafkaConsumerManager: Started successfully");
        } catch (Exception e) {
            LOG.errorf(e, "KafkaConsumerManager: Failed to start");
        }
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
                if (records == null) {
                    continue;
                }
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
