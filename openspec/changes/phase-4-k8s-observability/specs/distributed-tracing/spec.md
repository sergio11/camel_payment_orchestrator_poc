## ADDED Requirements

### Requirement: OpenTelemetry tracing enabled
Quarkus OpenTelemetry extension SHALL be enabled for distributed tracing.

#### Scenario: Traces exported via OTLP
- **WHEN** the application processes a request
- **THEN** traces MUST be exported to the configured OTLP endpoint (localhost:4317)

#### Scenario: Service name configured per component
- **WHEN** the application starts
- **THEN** OpenTelemetry MUST use the configured service name (payment-gateway or payment-processor)

### Requirement: Jaeger trace visualization
Jaeger SHALL be available for trace visualization and search.

#### Scenario: Jaeger UI accessible
- **WHEN** the monitoring stack is running
- **THEN** Jaeger UI MUST be accessible at localhost:16686

#### Scenario: Traces appear in Jaeger
- **WHEN** a payment flows through the system
- **THEN** distributed traces spanning api-gateway and payment-processor MUST appear in Jaeger
