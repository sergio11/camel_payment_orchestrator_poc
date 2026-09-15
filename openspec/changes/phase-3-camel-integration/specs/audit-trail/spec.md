## ADDED Requirements

### Requirement: Wire tap for audit logging
All consumed payments SHALL be wire-tapped to the audit pipeline without affecting the main processing flow.

#### Scenario: Payment audited via wire tap
- **WHEN** a payment is consumed from payments.events.received and passes enrichment
- **THEN** a copy of the PaymentMessage body MUST be sent to the direct:audit-pipeline route

### Requirement: Audit event published to Kafka
The audit pipeline SHALL publish the PaymentMessage to payments.events.audit topic.

#### Scenario: Audit event published
- **WHEN** the audit-pipeline route receives a PaymentMessage
- **THEN** it MUST set the AuditPaymentId header and marshal the body to payments.events.audit topic

#### Scenario: Audit uses paymentId as Kafka key
- **WHEN** an audit event is published
- **THEN** the Kafka message key MUST be set to the paymentId for partition ordering
