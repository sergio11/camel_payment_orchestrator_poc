## Why

Payments need to flow through fraud checking, provider selection, and resilience
patterns (circuit breaker, retry, dead letter) before completion. The synchronous
API must be transformed into an asynchronous event-driven pipeline using Apache
Camel with Kafka as the message backbone.

## What Changes

- Apache Camel routes for payment processing pipeline (PaymentProcessorRoute)
- Fraud evaluation engine with configurable scoring rules (FraudEngineRoute)
- Provider selection with circuit breaker and fallback (ProviderSelectionRoute)
- Kafka event publishing for all payment lifecycle stages
- Wire tap for audit trail (AuditPipelineRoute)
- Retry topic and dead letter channel for error handling
- Mock provider services (ProviderA, ProviderB)

## Capabilities

### New Capabilities
- `payment-processing-route`: Main Camel route consuming from Kafka, enriching, and routing
- `fraud-engine`: Fraud evaluation with scoring rules and action determination
- `provider-selection`: Dynamic routing with Resilience4j circuit breaker and fallback
- `kafka-events`: Event publishing for processed, failed, review, audit, retry, dead-letter
- `audit-trail`: Wire tap for payment audit logging without affecting main flow

### Modified Capabilities
(none)

## Impact

Transforms the synchronous API into a fully asynchronous event-driven payment
processing pipeline with fraud detection and provider routing.
