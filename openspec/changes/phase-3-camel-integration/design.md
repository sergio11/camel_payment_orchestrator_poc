# Design: Phase 3 - Camel Integration

## Architecture

### Technology Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| Java | 17 LTS | Runtime |
| Maven | 3.9+ | Build tool |
| quarkus-camel-quarkus | 3.17.x | Camel integration |
| quarkus-camel-resilience4j | 3.17.x | Circuit Breaker |
| quarkus-camel-kafka | 3.17.x | Kafka consumer/producer |
| quarkus-camel-netty-http | 3.17.x | HTTP provider endpoints |
| resilience4j | 2.2.0 | Circuit breaker implementation |
| Kafka Client | 3.5.0 | Kafka connectivity |

### Camel Route Architecture
```
POST /payments
    │
    ▼
Kafka Topic: payments.events.received
    │
    ▼
PaymentProcessorRoute (from(kafka))
    │
    ├─▶ WireTap (audit) ──────▶ Kafka: payments.events.audit
    │
    ├─▶ Enrich (risk data)
    │
    ├─▶ Content-Based Router
    │       │
    │       ├─▶ amount > 10000 ──▶ direct:fraud-review
    │       │
    │       ├─▶ WALLET + amount > 5000 ──▶ direct:fraud-review
    │       │
    │       └─▶ default ──▶ direct:fraud-check
    │
    ├─▶ FraudEngineRoute
    │       │
    │       └─▶ FraudEvaluationProcessor
    │               │
    │               ├─▶ riskScore >= 80 ──▶ REJECT
    │               ├─▶ riskScore >= 50 ──▶ REVIEW
    │               └─▶ riskScore < 50 ──▶ APPROVE
    │
    └─▶ ProviderSelectionRoute
            │
            ├─▶ Provider A (Circuit Breaker)
            │       │
            │       ├─▶ Success ──▶ publish processed event
            │       └─▶ Failure ──▶ Fallback → Provider B
            │
            └─▶ Dead Letter Channel (on failure)
                    │
                    └─▶ Kafka: payments.events.dead-letter
```

### EIP Patterns Implementation

#### Content-Based Router
```java
from("kafka:{{kafka.topic.payments.received}}")
    .routeId("payment-processor")
    .choice()
        .when(simple("${body.amount} > 10000"))
            .to("direct:fraud-review")
        .when(simple("${body.paymentMethod} == 'WALLET' && ${body.amount} > 5000"))
            .to("direct:fraud-review")
        .otherwise()
            .to("direct:fraud-check")
    .end();
```

#### Circuit Breaker (Resilience4j)
```java
from("direct:provider-a")
    .routeId("provider-a")
    .circuitBreaker()
        .resilience4jConfiguration()
            .failureRateThreshold(50)
            .waitDurationInOpenState(5000)
            .slidingWindowSize(10)
            .permittedNumberOfCallsInHalfOpenState(3)
        .end()
        .to("http://provider-a:8081/process")
        .onFallback()
            .to("direct:provider-b")
    .end();
```

#### Retry / Redelivery
```java
errorHandler(
    Builder.redeliveryPolicy()
        .maximumRedeliveries(5)
        .redeliveryDelay(1000)
        .exponentialBackoff()
        .maximumBackoff(30000)
);
```

#### Dead Letter Channel
```java
onException(Exception.class)
    .maximumRedeliveries(3)
    .redeliveryDelay(2000)
    .to("kafka:{{kafka.topic.payments.dead-letter}}")
    .to("direct:payment-failed");
```

#### Wire Tap
```java
.wireTap("direct:audit-pipeline");
```

### Project Structure

```
payment-processor/
├── pom.xml
└── src/main/
    ├── java/com/poc/processor/
    │   ├── PaymentProcessorRoute.java          # Main route
    │   ├── FraudEngineRoute.java                # Fraud evaluation
    │   ├── ProviderSelectionRoute.java         # Provider routing
    │   ├── processor/
    │   │   ├── FraudEvaluationProcessor.java   # Risk score calculation
    │   │   └── PaymentEnrichProcessor.java     # Enrich payment data
    │   ├── router/
    │   │   ├── ContentBasedRouterBean.java     # Dynamic routing logic
    │   │   └── ProviderRouterBean.java         # Provider selection
    │   ├── model/
    │   │   ├── PaymentMessage.java             # Kafka message DTO
    │   │   ├── FraudResult.java                # Fraud evaluation result
    │   │   └── ProviderResponse.java           # Provider response
    │   ├── config/
    │   │   ├── FraudRulesConfig.java           # Fraud rules configuration
    │   │   └── ProviderConfig.java             # Provider URLs config
    │   └── exception/
    │       ├── PaymentProcessingException.java
    │       ├── FraudRejectedException.java
    │       └── ProviderUnavailableException.java
    └── resources/
        └── application.properties
```

### application.properties

```properties
# Camel Configuration
camel.context.name=payment-processor
camel.springboot.name=payment-processor

# Kafka Consumer
camel.component.kafka.brokers={{kafka.bootstrap.servers}}
camel.component.kafka.group-id=payment-processor

# Kafka Topics
kafka.topic.payments.received=payments.events.received
kafka.topic.payments.processed=payments.events.processed
kafka.topic.payments.failed=payments.events.failed
kafka.topic.payments.dead-letter=payments.events.dead-letter
kafka.topic.fraud.detected=fraud.events.detected
kafka.topic.payments.audit=payments.events.audit

# Circuit Breaker Configuration
camel.resilience4j.circuit-breaker.configs.default.failure-rate-threshold=50
camel.resilience4j.circuit-breaker.configs.default.wait-duration-in-open-state=5s
camel.resilience4j.circuit-breaker.configs.default.sliding-window-size=10
camel.resilience4j.circuit-breaker.configs.default.permitted-number-of-calls-in-half-open-state=3

# Retry Configuration
camel.errorhandler.maximum-redeliveries=5
camel.errorhandler.redelivery-delay=1000
camel.errorhandler.exponential-backoff=true
camel.errorhandler.maximum-backoff=30000

# Provider URLs
provider.a.url=http://provider-a:8081
provider.b.url=http://provider-b:8082
```

### Fraud Rules Engine

```java
@ApplicationScoped
public class FraudEvaluationProcessor implements Processor {
    
    @Inject
    FraudRulesConfig config;
    
    @Override
    public void process(Exchange exchange) throws Exception {
        PaymentMessage payment = exchange.getIn().getBody(PaymentMessage.class);
        
        int riskScore = 0;
        List<String> triggeredRules = new ArrayList<>();
        
        // Rule 1: High amount - score >= 15000 adds 50 points
        if (payment.getAmount().compareTo(new BigDecimal("15000")) > 0) {
            riskScore += 50;
            triggeredRules.add("HIGH_AMOUNT");
        }
        
        // Rule 2: High risk country - countries from config
        if (config.getHighRiskCountries().contains(payment.getCountry())) {
            riskScore += 30;
            triggeredRules.add("HIGH_RISK_COUNTRY");
        }
        
        // Rule 3: Rapid retry - more than 3 attempts in last hour
        if (payment.getAttemptCount() != null && payment.getAttemptCount() > 3) {
            riskScore += 25;
            triggeredRules.add("RAPID_RETRY");
        }
        
        // Rule 4: New payment method for new customer
        if (Boolean.TRUE.equals(payment.getIsNewPaymentMethod()) && 
            payment.getCustomerAgeDays() != null && 
            payment.getCustomerAgeDays() < 30) {
            riskScore += 20;
            triggeredRules.add("NEW_PAYMENT_METHOD");
        }
        
        // Rule 5: Unusual hour (2am-5am local time)
        LocalTime hour = LocalTime.now(payment.getTimeZone());
        if (hour.isAfter(LocalTime.of(2, 0)) && hour.isBefore(LocalTime.of(5, 0))) {
            riskScore += 15;
            triggeredRules.add("UNUSUAL_HOUR");
        }
        
        // Cap risk score at 100
        riskScore = Math.min(riskScore, 100);
        
        FraudResult result = new FraudResult();
        result.setRiskScore(riskScore);
        result.setTriggeredRules(triggeredRules);
        result.setAction(determineAction(riskScore));
        
        exchange.getIn().setBody(result);
    }
    
    private String determineAction(int riskScore) {
        if (riskScore >= 80) return "REJECT";
        if (riskScore >= 50) return "REVIEW";
        return "APPROVE";
    }
}
```

### FraudRulesConfig

```java
@ApplicationScoped
@ConfigProperties(prefix = "fraud")
public class FraudRulesConfig {
    
    private BigDecimal highAmountThreshold = new BigDecimal("15000");
    private Set<String> highRiskCountries = Set.of("XX", "YY", "ZZ"); // Example codes
    private int maxRapidRetries = 3;
    private int newCustomerAgeDays = 30;
    private int riskScoreThresholdHigh = 80;
    private int riskScoreThresholdMedium = 50;
    
    // Getters and setters
}
```

### Provider Simulation

#### Provider A (Fast but less reliable)
```java
@Path("/process")
@ApplicationScoped
public class ProviderAService {
    
    @POST
    public Response processPayment(PaymentMessage payment) {
        // Simulate fast response (100-200ms)
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(100, 200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Simulate 10% failure rate
        if (ThreadLocalRandom.current().nextInt(100) < 10) {
            return Response.status(500)
                .entity(new ProviderResponse("PROVIDER_ERROR", "Provider A failed"))
                .build();
        }
        
        String transactionId = "TXN-A-" + UUID.randomUUID().toString().substring(0, 8);
        return Response.ok(new ProviderResponse("SUCCESS", transactionId)).build();
    }
}
```

#### Provider B (Slower but more reliable)
```java
@Path("/process")
@ApplicationScoped
public class ProviderBService {
    
    @POST
    public Response processPayment(PaymentMessage payment) {
        // Simulate slower response (500-1000ms)
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(500, 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Simulate 2% failure rate
        if (ThreadLocalRandom.current().nextInt(100) < 2) {
            return Response.status(500)
                .entity(new ProviderResponse("PROVIDER_ERROR", "Provider B failed"))
                .build();
        }
        
        String transactionId = "TXN-B-" + UUID.randomUUID().toString().substring(0, 8);
        return Response.ok(new ProviderResponse("SUCCESS", transactionId)).build();
    }
}
```

### ProviderRouterBean (Dynamic Router)

```java
@ApplicationScoped
public class ProviderRouterBean {
    
    @ConfigProperty(name = "provider.a.url")
    String providerAUrl;
    
    @ConfigProperty(name = "provider.b.url")
    String providerBUrl;
    
    private final AtomicReference<String> currentProvider = new AtomicReference<>("provider-a");
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    
    public String decideNextProvider(Exchange exchange) {
        // Simple round-robin with circuit breaker awareness
        if (consecutiveFailures.get() > 5) {
            // Force provider B if too many failures on A
            consecutiveFailures.set(0);
            return "direct:provider-b";
        }
        
        return "direct:provider-a";
    }
    
    public void recordSuccess() {
        consecutiveFailures.set(0);
    }
    
    public void recordFailure() {
        consecutiveFailures.incrementAndGet();
    }
}
```

### PaymentProcessorRoute (Main Route)

```java
@ApplicationScoped
public class PaymentProcessorRoute extends RouteBuilder {
    
    @Override
    public void configure() throws Exception {
        
        // Main route - consume from Kafka
        from("kafka:{{kafka.topic.payments.received}}")
            .routeId("payment-processor")
            .log("Processing payment: ${body.paymentId}")
            
            // Wire Tap for audit - parallel route, no impact on main flow
            .wireTap("direct:audit-pipeline")
            
            // Enrich payment with risk data
            .process(new PaymentEnrichProcessor())
            
            // Content-Based Router - route based on payment characteristics
            .choice()
                .when(simple("${body.amount} > 10000"))
                    .to("direct:fraud-review")
                    .log("High value payment -> fraud review")
                .when(simple("${body.paymentMethod} == 'WALLET' && ${body.amount} > 5000"))
                    .to("direct:fraud-review")
                    .log("High risk wallet payment -> fraud review")
                .otherwise()
                    .to("direct:fraud-check")
                    .log("Normal payment -> fraud check")
            .end()
            
            // Dead Letter Channel for unhandled exceptions
            .onException(Exception.class)
                .maximumRedeliveries(3)
                .redeliveryDelay(2000)
                .to("kafka:{{kafka.topic.payments.dead-letter}}")
                .to("direct:payment-failed")
            .end();
        
        // Audit pipeline - Wire Tap destination
        from("direct:audit-pipeline")
            .routeId("audit-pipeline")
            .log("Audit: Payment ${body.paymentId} - ${body.status}")
            .to("kafka:{{kafka.topic.payments.audit}}");
        
        // Fraud review route - high value payments
        from("direct:fraud-review")
            .routeId("fraud-review")
            .to("direct:fraud-engine");
        
        // Fraud check route - normal payments
        from("direct:fraud-check")
            .routeId("fraud-check")
            .to("direct:fraud-engine");
        
        // Fraud engine route
        from("direct:fraud-engine")
            .routeId("fraud-engine")
            .process(new FraudEvaluationProcessor())
            .choice()
                .when(simple("${body.action} == 'REJECT'"))
                    .to("direct:fraud-reject")
                    .to("kafka:{{kafka.topic.fraud.detected}}")
                .when(simple("${body.action} == 'REVIEW'"))
                    .to("direct:fraud-review-queue")
                .otherwise()
                    .to("direct:provider-selection")
            .end();
        
        // Fraud rejected route
        from("direct:fraud-reject")
            .routeId("fraud-reject")
            .log("Payment REJECTED due to fraud: ${body.paymentId}")
            .to("kafka:{{kafka.topic.payments.failed}}");
        
        // Provider selection route
        from("direct:provider-selection")
            .routeId("provider-selection")
            .dynamicRouter(method(ProviderRouterBean.class, "decideNextProvider"))
            .to("direct:process-payment");
        
        // Provider A with Circuit Breaker
        from("direct:provider-a")
            .routeId("provider-a")
            .circuitBreaker()
                .resilience4jConfiguration()
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(5000)
                    .slidingWindowSize(10)
                    .permittedNumberOfCallsInHalfOpenState(3)
                .end()
            .toD("${exchangeProperty.providerAUrl}")
            .process(exchange -> ProviderRouterBean.recordSuccess())
            .to("direct:payment-success")
            .onFallback()
                .to("direct:provider-b")
                .log("Provider A failed, falling back to Provider B");
        
        // Provider B - more reliable
        from("direct:provider-b")
            .routeId("provider-b")
            .toD("${exchangeProperty.providerBUrl}")
            .process(exchange -> ProviderRouterBean.recordSuccess())
            .to("direct:payment-success");
        
        // Process payment endpoint (used by dynamic router)
        from("direct:process-payment")
            .routeId("process-payment")
            .to("direct:provider-a");
        
        // Payment success route
        from("direct:payment-success")
            .routeId("payment-success")
            .log("Payment SUCCESS: ${body.paymentId}")
            .to("kafka:{{kafka.topic.payments.processed}}");
        
        // Payment failed route
        from("direct:payment-failed")
            .routeId("payment-failed")
            .log("Payment FAILED: ${body.paymentId}")
            .to("kafka:{{kafka.topic.payments.failed}}");
    }
}
```

### Kafka Message DTOs

```java
public class PaymentMessage implements Serializable {
    private String paymentId;
    private BigDecimal amount;
    private String currency;
    private String customerId;
    private String paymentMethod;
    private String country;
    private String status;
    private Integer attemptCount;
    private Boolean isNewPaymentMethod;
    private Integer customerAgeDays;
    private ZoneId timeZone;
    private LocalDateTime createdAt;
    // Getters, setters
}

public class FraudResult implements Serializable {
    private int riskScore;
    private List<String> triggeredRules;
    private String action; // APPROVE, REVIEW, REJECT
    // Getters, setters
}

public class ProviderResponse implements Serializable {
    private String status; // SUCCESS, FAILED
    private String transactionId;
    private String message;
    // Getters, setters
}
```

### Content-Based Router Logic

```java
@ApplicationScoped
public class ContentBasedRouterBean {
    
    public String routePayment(Exchange exchange) {
        PaymentMessage payment = exchange.getIn().getBody(PaymentMessage.class);
        
        // High amount threshold
        if (payment.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            return "direct:fraud-review";
        }
        
        // WALLET method with high amount
        if ("WALLET".equals(payment.getPaymentMethod()) && 
            payment.getAmount().compareTo(new BigDecimal("5000")) > 0) {
            return "direct:fraud-review";
        }
        
        // High risk country
        Set<String> highRiskCountries = Set.of("XX", "YY", "ZZ");
        if (highRiskCountries.contains(payment.getCountry())) {
            return "direct:fraud-review";
        }
        
        // Default - normal fraud check
        return "direct:fraud-check";
    }
}
```

### Provider Configuration

```java
@ApplicationScoped
@ConfigProperties(prefix = "provider")
public class ProviderConfig {
    
    private String aUrl;
    private String bUrl;
    private int timeoutMs = 30000;
    private int maxRetries = 3;
    
    // High risk countries for fraud detection
    private List<String> highRiskCountries = List.of("XX", "YY", "ZZ");
    
    // Getters and setters
}
```

### Kafka Producer Configuration

```properties
# In application.properties
camel.component.kafka.topic.payment.received=payments.events.received
camel.component.kafka.topic.payment.processed=payments.events.processed
camel.component.kafka.topic.payment.failed=payments.events.failed
camel.component.kafka.topic.payment.dead-letter=payments.events.dead-letter
camel.component.kafka.topic.fraud.detected=fraud.events.detected

# Auto-create topics
kafka.auto-create-topics=true

# Consumer group
camel.component.kafka.group-id=payment-processor
```

## Data Flow

1. REST API (Phase 2) receives payment POST → saves to in-memory store → publishes to Kafka `payments.events.received`
2. PaymentProcessorRoute consumes from Kafka topic
3. WireTap sends parallel message to audit pipeline → publishes to `payments.events.audit`
4. PaymentEnrichProcessor enriches payment with risk data (attempt count, customer age, etc.)
5. ContentBasedRouter determines fraud check path based on amount, method, country
6. FraudEvaluationProcessor calculates risk score based on rules
7. If action=APPROVE → route to ProviderSelection
8. If action=REJECT → publish to `payments.events.failed` and `fraud.events.detected`
9. If action=REVIEW → route to manual review queue
10. ProviderSelection uses Circuit Breaker → calls Provider A
11. If Provider A fails → fallback to Provider B
12. On success → publish to `payments.events.processed`
13. On failure (after retries exhausted) → Dead Letter Channel → `payments.events.dead-letter`

## Error Handling Summary

| Scenario | Handling |
|----------|----------|
| Kafka connection failure | Retry with exponential backoff |
| Provider A failure | Fallback to Provider B |
| Circuit Breaker open | Reject immediately, don't retry |
| All providers failed | Dead Letter Channel → Kafka |
| Fraud reject | Publish to fraud.events.detected |
| Unexpected exception | Dead Letter → payment-failed route |

## Security Considerations

- No sensitive data (card numbers, CVV) in logs
- Provider credentials via environment variables (PROVIDER_A_URL, PROVIDER_B_URL)
- Kafka SASL authentication for production (future)
- TLS for provider communication (future)

## Monitoring Metrics

```properties
# Camel metrics enabled
camel.metrics.enabled=true

# Custom metrics
quarkus.micrometer.export.prometheus.enabled=true
quarkus.micrometer.binder.http-server.enabled=true
```

Metrics exposed:
- `camel.routes.count` - Number of routes
- `camel.exchanges.total` - Total exchanges processed
- `camel.exchanges.failures` - Failed exchanges
- `camel.processor.calls` - Processor call counts
- `payment.processor.latency` - Processing latency histogram
- `payment.provider.fallback.count` - Provider fallback count