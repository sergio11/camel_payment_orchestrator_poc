package com.poc.gateway.health;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimpleHealthCheckTest {

    private SimpleHealthCheck healthCheck;

    @BeforeEach
    void setUp() {
        healthCheck = new SimpleHealthCheck();
    }

    @Test
    @DisplayName("call() returns non-null HealthCheckResponse")
    void call_returnsResponse() {
        HealthCheckResponse response = healthCheck.call();
        assertNotNull(response);
    }

    @Test
    @DisplayName("call() returns HealthCheckResponse with correct name")
    void call_returnsCorrectName() {
        HealthCheckResponse response = healthCheck.call();
        assertEquals("Payment Gateway Health Check", response.getName());
    }
}
