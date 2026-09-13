package com.poc.gateway.health;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

@Readiness
@ApplicationScoped
public class SimpleHealthCheck implements HealthCheck {

    @ConfigProperty(name = "kafka.bootstrap.servers", defaultValue = "")
    String bootstrapServers;

    private AdminClient adminClient;

    @PostConstruct
    void init() {
        if (bootstrapServers != null && !bootstrapServers.isBlank()) {
            adminClient = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000,
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5000
            ));
        }
    }

    @PreDestroy
    void destroy() {
        if (adminClient != null) {
            try {
                adminClient.close();
            } catch (Exception e) {
                // Ignore on shutdown
            }
        }
    }

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("Payment Gateway Health Check");
        if (adminClient == null) {
            builder.up();
            return builder.build();
        }
        try {
            adminClient.listTopics().listings().get(5, TimeUnit.SECONDS);
            builder.up().withData("kafka", "reachable");
        } catch (Exception e) {
            builder.down().withData("kafka", e.getMessage() == null ? e.toString() : e.getMessage());
        }
        return builder.build();
    }
}
