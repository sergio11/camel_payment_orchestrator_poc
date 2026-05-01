# Design: Phase 3 - Camel Integration

## Architecture

### Camel Route Architecture
```
POST /payments
    │
    ▼
PaymentProcessorRoute
    │
    ├─▶ WireTap (audit)
    │
    ├─▶ FraudCheckRoute
    │       │
    │       └─▶ Risk Evaluation
    │               │
    │               ├─▶ riskScore >= 80 → REJECT
    │               ├─▶ riskScore >= 50 → REVIEW
    │               └─▶ riskScore < 50 → APPROVE
    │
    └─▶ ProviderSelectionRoute
            │
            ├─▶ Provider A (Circuit Breaker)
            │       │
            │       └─▶ Fallback → Provider B
            │
            └─▶ Dead Letter Channel (on failure)
```

### EIP Patterns Implementation

#### Content-Based Router
Route payments based on:
- Amount > $10,000 → High amount queue
- PaymentMethod = WALLET + amount > $5,000 → Review
- Default → Normal processing

#### Circuit Breaker (Resilience4j)
- Failure rate threshold: 50%
- Wait duration in open state: 5 seconds
- Sliding window size: 10 calls

#### Retry / Redelivery
- Max retries: 5
- Initial delay: 1 second
- Backoff: Exponential (x2)

#### Dead Letter Channel
- Max redeliveries: 3
- On exhaustion → Kafka dead-letter topic

#### Wire Tap
- Parallel route to audit topic
- No modification to main flow

### Fraud Rules Engine
```java
// Rule 1: High amount
if (amount > 15000) riskScore += 50;

// Rule 2: High risk country
if (HIGH_RISK_COUNTRIES.contains(country)) riskScore += 30;

// Rule 3: Rapid retry
if (attemptCount > 3) riskScore += 25;

// Rule 4: New payment method for new customer
if (isNewPaymentMethod && customerAge < 30) riskScore += 20;
```

### Provider Simulation
- **Provider A**: Fast response (100-200ms), 10% failure rate
- **Provider B**: Slower response (500-1000ms), 2% failure rate

## Components

1. **PaymentProcessorRoute**: Main route orchestration
2. **FraudEngineRoute**: Risk evaluation and routing
3. **ProviderSelectionRoute**: Provider selection with fallback
4. **FraudEvaluationProcessor**: Risk score calculation
5. **DynamicRouterBean**: Dynamic provider selection

## Data Flow
1. REST receives payment → publishes to Kafka `payments.events.received`
2. Camel route consumes from Kafka
3. WireTap sends to audit
4. Enrich with risk data
5. Content-Based Router → Fraud Engine
6. Fraud evaluation → Score calculation
7. If approved → Provider Selection
8. Circuit Breaker → Provider A → fallback to B
9. On success → publish to `payments.events.processed`
10. On failure → Dead Letter → `payments.events.failed`

## Security Considerations
- No sensitive data in logs
- Provider credentials via environment variables