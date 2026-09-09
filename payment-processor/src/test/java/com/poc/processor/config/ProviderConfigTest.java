package com.poc.processor.config;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ProviderConfigTest {

    @Inject
    ProviderConfig config;

    @Test
    @DisplayName("providerAUrl returns configured URL")
    void providerAUrl() {
        assertNotNull(config.providerAUrl());
        assertEquals("http://localhost:8081/provider-a/process", config.providerAUrl());
    }

    @Test
    @DisplayName("providerBUrl returns configured URL")
    void providerBUrl() {
        assertNotNull(config.providerBUrl());
        assertEquals("http://localhost:8081/provider-b/process", config.providerBUrl());
    }

    @Test
    @DisplayName("circuitBreakerFailureThreshold returns 50")
    void circuitBreakerFailureThreshold() {
        assertEquals(50, config.circuitBreakerFailureThreshold());
    }

    @Test
    @DisplayName("circuitBreakerWaitDuration returns configured duration")
    void circuitBreakerWaitDuration() {
        assertNotNull(config.circuitBreakerWaitDuration());
        assertEquals(Duration.ofSeconds(1), config.circuitBreakerWaitDuration());
    }

    @Test
    @DisplayName("circuitBreakerSlidingWindowSize returns configured value")
    void circuitBreakerSlidingWindowSize() {
        assertEquals(10, config.circuitBreakerSlidingWindowSize());
    }
}
