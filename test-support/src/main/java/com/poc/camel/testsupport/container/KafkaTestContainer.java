package com.poc.camel.testsupport.container;

import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

public class KafkaTestContainer {

    private static final KafkaContainer INSTANCE =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
            .withStartupTimeout(Duration.ofMinutes(3));

    private KafkaTestContainer() {
    }

    public static KafkaContainer getInstance() {
        return INSTANCE;
    }
}
