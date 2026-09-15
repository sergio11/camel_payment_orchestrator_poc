## ADDED Requirements

### Requirement: Publish payment received event
The API Gateway SHALL publish a PaymentReceived event to payments.events.received when a payment is created.

#### Scenario: Payment received event published
- **WHEN** a payment is created via POST /payments
- **THEN** a JSON payload with paymentId, amount, currency, customerId, paymentMethod, country, metadata MUST be published to payments.events.received

### Requirement: Publish payment processed event
ProviderSelectionRoute SHALL publish a ProviderResponse to payments.events.processed on success.

#### Scenario: Provider success event
- **WHEN** a provider returns a successful response
- **THEN** a ProviderResponse with providerId, transactionId, success=true, processedAt MUST be published to payments.events.processed

### Requirement: Publish payment failed event
FraudEngineRoute SHALL publish the original PaymentMessage to payments.events.failed on fraud rejection.

#### Scenario: Fraud rejection event
- **WHEN** fraud action is REJECT
- **THEN** the original PaymentMessage MUST be published to payments.events.failed

### Requirement: Publish payment review event
FraudEngineRoute SHALL publish the original PaymentMessage to payments.events.review on fraud review.

#### Scenario: Fraud review event
- **WHEN** fraud action is REVIEW
- **THEN** the original PaymentMessage MUST be published to payments.events.review

### Requirement: Publish fraud detected event
FraudEngineRoute SHALL publish FraudResult to fraud.events.detected for all fraud evaluations.

#### Scenario: Fraud result event
- **WHEN** fraud evaluation completes
- **THEN** a FraudResult (APPROVE/REVIEW/REJECT) MUST be published to fraud.events.detected

### Requirement: Publish payment status changed event
The API Gateway SHALL publish a status change event to payments.events.status.changed.

#### Scenario: Status changed event
- **WHEN** payment status is updated via PATCH /payments/{id}/status
- **THEN** a JSON payload with paymentId and status MUST be published to payments.events.status.changed

### Requirement: Publish payment dead letter event
Dead letter handlers SHALL publish failed payloads to payments.events.dead-letter.

#### Scenario: Dead letter from API Gateway
- **WHEN** the API Gateway encounters a fatal error
- **THEN** a payload with paymentId, originalPayload, and reason MUST be published to payments.events.dead-letter

#### Scenario: Dead letter from payment-processor
- **WHEN** the payment-processor dead letter channel triggers
- **THEN** the original message body MUST be published to payments.events.dead-letter

### Requirement: Publish payment retry event
PaymentProcessorRoute SHALL publish exhausted messages to payments.events.retry.

#### Scenario: Retry topic receives exhausted message
- **WHEN** maximum redeliveries are exhausted on the main route
- **THEN** the PaymentMessage MUST be published to payments.events.retry
