package com.poc.processor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.poc.camel.testsupport.container.KafkaTestContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
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
        try (AdminClient admin = AdminClient.create(props)) {
            List<NewTopic> newTopics = TOPICS.stream()
                .map(name -> new NewTopic(name, 1, (short) 1))
                .collect(Collectors.toList());
            // createTopics is idempotent for already-existing topics when ignoring errors
            admin.createTopics(newTopics).all().get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Topics may already exist (e.g. second test class in same JVM) — that's fine
            System.out.println("[KafkaTestResource] Topic pre-creation note: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        // Singleton compartido entre clases E2E; se detiene con la JVM
    }
}
