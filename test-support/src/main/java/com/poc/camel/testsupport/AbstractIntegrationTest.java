package com.poc.camel.testsupport;

import com.poc.camel.testsupport.container.KafkaTestContainer;
import com.poc.camel.testsupport.container.PostgresTestContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

public abstract class AbstractIntegrationTest implements QuarkusTestResourceLifecycleManager {

    static {
        System.setProperty("testcontainers.ryuk.disabled", "true");
        System.setProperty("ryuk.disabled", "true");
        if (System.getenv("DOCKER_HOST") == null) {
            System.setProperty("docker.host", "npipe:////./pipe/podman-machine-default");
        }
    }

    protected static PostgreSQLContainer<?> postgres;
    protected static KafkaContainer kafka;

    @Override
    public Map<String, String> start() {
        postgres = PostgresTestContainer.getInstance();
        kafka = KafkaTestContainer.getInstance();

        if (!postgres.isRunning()) {
            postgres.start();
        }
        if (!kafka.isRunning()) {
            kafka.start();
        }

        String jdbcUrl = postgres.getJdbcUrl();
        String bootstrapServers = kafka.getBootstrapServers();

        System.setProperty("kafka.bootstrap.servers", bootstrapServers);
        System.setProperty("camel.component.kafka.brokers", bootstrapServers);

        return Map.of(
            "quarkus.datasource.jdbc.url", jdbcUrl,
            "quarkus.datasource.username", postgres.getUsername(),
            "quarkus.datasource.password", postgres.getPassword(),
            "kafka.bootstrap.servers", bootstrapServers,
            "camel.component.kafka.brokers", bootstrapServers
        );
    }

    @Override
    public void stop() {
        // Containers are singletons; they stop when JVM shuts down
    }

    @Override
    public int order() {
        return 0;
    }
}
