# Tasks: Phase 3 - Camel Integration

## Implementation Tasks

- [ ] Add Camel Quarkus dependencies to pom.xml
- [ ] Configure Camel in application.properties
- [ ] Create PaymentProcessorRoute main route
- [ ] Implement Content-Based Router logic
- [ ] Create FraudEngineRoute
- [ ] Implement FraudEvaluationProcessor
- [ ] Add fraud rules (amount, country, rapid retry, new method)
- [ ] Create ProviderSelectionRoute
- [ ] Implement Circuit Breaker with Resilience4j
- [ ] Configure retry with exponential backoff
- [ ] Configure Dead Letter Channel
- [ ] Implement Wire Tap for audit logging
- [ ] Create mock Provider A endpoint
- [ ] Create mock Provider B endpoint
- [ ] Configure Kafka producer/consumer
- [ ] Add metrics to Camel routes

## Validation Tasks

- [ ] Verify payment triggers fraud check
- [ ] Verify high amount routes to review
- [ ] Verify provider fallback works
- [ ] Verify circuit breaker opens after failures
- [ ] Verify dead letter receives failed payments
- [ ] Verify wire tap logs to audit topic
- [ ] Verify events published to Kafka

## Documentation Tasks

- [ ] Document EIP patterns used
- [ ] Document fraud rules
- [ ] Add flow diagrams to README