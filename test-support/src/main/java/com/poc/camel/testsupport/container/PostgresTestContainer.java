package com.poc.camel.testsupport.container;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

public class PostgresTestContainer {

    private static final PostgreSQLContainer<?> INSTANCE =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withUsername("test")
            .withPassword("test")
            .withDatabaseName("payments_test")
            .withStartupTimeout(Duration.ofSeconds(300));

    private PostgresTestContainer() {
    }

    public static PostgreSQLContainer<?> getInstance() {
        return INSTANCE;
    }
}
