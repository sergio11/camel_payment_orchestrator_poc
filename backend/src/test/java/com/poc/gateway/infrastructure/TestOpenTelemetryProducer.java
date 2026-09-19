package com.poc.gateway.infrastructure;

import io.opentelemetry.api.OpenTelemetry;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

public class TestOpenTelemetryProducer {

    @Produces
    @Dependent
    public OpenTelemetry openTelemetry() {
        return OpenTelemetry.noop();
    }
}
