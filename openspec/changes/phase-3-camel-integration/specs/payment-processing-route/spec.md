## ADDED Requirements

### Requirement: Consume payment received events from Kafka
The PaymentProcessorRoute SHALL consume PaymentMessage objects from payments.events.received topic.

#### Scenario: Payment message consumed and validated
- **WHEN** a PaymentMessage JSON is published to payments.events.received
- **THEN** the PaymentProcessorRoute MUST pick it up, unmarshal, and validate required fields

#### Scenario: Invalid payment message routed to dead letter
- **WHEN** a PaymentMessage with missing required fields is consumed
- **THEN** it MUST be routed to the poison dead letter channel without retry

### Requirement: Enrich payment with risk data
Payments SHALL be enriched with fraud metadata (attempts, velocity, geo risk) before evaluation.

#### Scenario: Payment enrichment adds risk metadata
- **WHEN** a payment is consumed from Kafka
- **THEN** it MUST be enriched with PaymentMetadataDTO containing attempts, velocity_score, geo_risk_score

### Requirement: Content-based routing to fraud check
Payments SHALL be routed to fraud review or standard fraud check based on amount and payment method.

#### Scenario: High amount routes to fraud review
- **WHEN** payment amount exceeds 10000
- **THEN** payment MUST be routed to the fraud review path

#### Scenario: Wallet with medium amount routes to fraud review
- **WHEN** payment method is WALLET and amount exceeds 5000
- **THEN** payment MUST be routed to the fraud review path

#### Scenario: Standard payment routes to fraud check
- **WHEN** payment amount is <= 10000 and method is not WALLET or amount <= 5000
- **THEN** payment MUST be routed to the standard fraud check path

### Requirement: Retry with exponential backoff
Failed messages SHALL be retried with exponential backoff before dead lettering.

#### Scenario: Retries exhausted routes to retry topic
- **WHEN** maximum redeliveries (3) are exhausted on the main route
- **THEN** the message MUST be published to payments.events.retry topic

#### Scenario: Poison message routed directly to DLQ
- **WHEN** a JsonProcessingException, PaymentProcessingException, or InvalidPaymentException occurs
- **THEN** the message MUST be routed directly to dead letter without retry

### Requirement: Retry consumer republishes to received topic
A retry consumer SHALL read from payments.events.retry and republish to payments.events.received.

#### Scenario: Retry attempt within limit
- **WHEN** a message is consumed from retry topic with retry count < 3
- **THEN** it MUST be republished to payments.events.received for reprocessing

#### Scenario: Retry attempt exceeds limit
- **WHEN** a message is consumed from retry topic with retry count >= 3
- **THEN** it MUST be published to payments.events.dead-letter
