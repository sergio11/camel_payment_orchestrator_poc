package com.poc.processor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import com.poc.camel.testsupport.container.KafkaTestContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.testcontainers.containers.KafkaContainer;

public class KafkaTestResource implements QuarkusTestResourceLifecycleManager {

    private static final List<String> TOPICS = Arrays.asList(
        "payments.events.received",
        "payments.events.processed",
        "payments.events.failed",
        "payments.events.review",
        "payments.events.retry",
        "payments.events.dead-letter",
        "payments.events.audit",
        "fraud.events.detected"
    );

    private KafkaContainer kafka;

    @Override
    public Map<String, String> start() {
        System.setProperty("testcontainers.ryuk.disabled", "true");
        System.setProperty("ryuk.disabled", "true");
        if (System.getenv("DOCKER_HOST") == null) {
            System.setProperty("docker.host", "npipe:////./pipe/podman-machine-default");
        }

        kafka = KafkaTestContainer.getInstance();
        if (!kafka.isRunning()) {
            kafka.start();
        }

        String bootstrapServers = kafka.getBootstrapServers();

        // Pre-create all topics so partition leaders are elected before any test
        // sends messages or consumers subscribe. This eliminates LEADER_NOT_AVAILABLE
        // and UNKNOWN_TOPIC_OR_PARTITION races that cause consumeUntil() to return null.
        preCreateTopics(bootstrapServers);

        System.setProperty("camel.component.kafka.brokers", bootstrapServers);
        return Map.of(
            "kafka.bootstrap.servers", bootstrapServers,
            "camel.component.kafka.brokers", bootstrapServers,
            "camel.route.payment-processor.auto-startup", "true"
        );
    }

    private void preCreateTopics(String bootstrapServers) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 15000);
        try (AdminClient admin = AdminClient.create(props)) {
            // Fail fast when the broker is unreachable instead of letting tests
            // run against a half-ready broker (that produced the NPE cascade
            // in QuarkusTestExtension with runningQuarkusApplication == null).
            admin.describeCluster().nodes().get(60, TimeUnit.SECONDS);
            List<NewTopic> newTopics = TOPICS.stream()
                .map(name -> new NewTopic(name, 1, (short) 1))
                .collect(Collectors.toList());
            try {
                admin.createTopics(newTopics).all().get(60, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof TopicExistsException) {
                    // Singleton container shared across test classes in the same
                    // JVM: topics created by a previous class are reused.
                    System.out.println("[KafkaTestResource] Topics already exist, reusing them");
                } else {
                    throw new RuntimeException(
                        "Kafka topic pre-creation failed on " + bootstrapServers, e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for Kafka test broker", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException("Kafka test broker not ready: " + bootstrapServers, e);
        }
    }

    @Override
    public void stop() {
        // Singleton compartido entre clases E2E; se detiene con la JVM
    }
}
