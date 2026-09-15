## ADDED Requirements

### Requirement: Dynamic provider routing
Payments SHALL be routed to the appropriate provider using a dynamic router pattern.

#### Scenario: Primary provider selected by default
- **WHEN** a payment reaches provider selection
- **THEN** provider-a SHALL be called as the primary provider

### Requirement: Circuit breaker on primary provider
Provider A SHALL have a Resilience4j circuit breaker with configurable thresholds.

#### Scenario: Circuit breaker opens after failure threshold
- **WHEN** provider-a failure rate exceeds the configured threshold (50%)
- **THEN** the circuit breaker MUST open and subsequent requests MUST be redirected to fallback

#### Scenario: Circuit breaker half-open after wait duration
- **WHEN** the configured wait duration (5s) elapses while circuit is open
- **THEN** the circuit MUST enter half-open state and allow limited requests

### Requirement: Fallback to secondary provider
When provider-a fails or circuit breaker is open, provider-b SHALL be called.

#### Scenario: Fallback succeeds
- **WHEN** provider-a fails and provider-b succeeds
- **THEN** payment MUST be processed successfully via provider-b and published to payments.events.processed

#### Scenario: Both providers fail
- **WHEN** both provider-a and provider-b fail
- **THEN** payment MUST be routed to the dead letter channel

### Requirement: Provider response published to Kafka
Successful provider responses SHALL be published to payments.events.processed.

#### Scenario: Provider success publishes processed event
- **WHEN** a provider returns success=true
- **THEN** a ProviderResponse MUST be published to payments.events.processed with the provider details

#### Scenario: Provider failure throws exception
- **WHEN** a provider returns success=false
- **THEN** a RuntimeException MUST be thrown triggering fallback or dead letter
