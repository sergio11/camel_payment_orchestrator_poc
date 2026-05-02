# API Changes: Phase 3 - Camel Integration

## New Endpoints
None - existing endpoints now trigger Camel processing via Kafka.

## Modified Endpoints

| Endpoint | Phase 2 Behavior | Phase 3 Behavior |
|----------|-----------------|------------------|
| POST /payments | Synchronous: creates payment, returns immediately | Asynchronous: creates, publishes to Kafka, returns with status=PENDING |
| GET /payments/{id} | Returns payment from in-memory store | Returns payment (may include provider field) |

## Deprecated Endpoints
None.

## Schema Changes

### PaymentResponse (Enhanced)
| Field | Phase 2 | Phase 3 |
|-------|---------|---------|
| provider | null | Populated after successful processing |
| status | PENDING only | PENDING → PROCESSING → APPROVED/REJECTED/FAILED |
| failureReason | null | Populated when status=FAILED |

## Internal Changes

### Data Flow Transformation
1. **Phase 2**: POST /payments → In-memory store → Return 201
2. **Phase 3**: POST /payments → In-memory store (PENDING) → Kafka topic → Camel route → Provider → Update status → Kafka success event

### Kafka Topics (New)
| Topic | Purpose | Producer | Consumer |
|-------|---------|----------|----------|
| payments.events.received | New payments | API Gateway | PaymentProcessorRoute |
| payments.events.processed | Successful payments | ProviderSelectionRoute | (External consumers) |
| payments.events.failed | Failed payments | FraudEngineRoute, ProviderSelection | (External consumers) |
| payments.events.dead-letter | Unprocessable | Dead Letter Channel | (Monitoring/retry) |
| fraud.events.detected | Fraud alerts | FraudEngineRoute | (Alerting systems) |
| payments.events.audit | Audit trail | Wire Tap | (Audit logging) |

### Status Flow
```
PENDING → PROCESSING → APPROVED
    ↓           ↓
REJECTED     FAILED
```

### EIP Patterns Added
- Content-Based Router: Route to fraud-review based on amount/method
- Circuit Breaker: Provider A with fallback to Provider B
- Retry: Exponential backoff on transient failures
- Dead Letter Channel: Failed payments after 3 retries
- Wire Tap: Parallel audit trail logging