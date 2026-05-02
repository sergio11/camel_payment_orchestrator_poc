# Tasks: Phase 3 - Camel Integration

## Implementation Tasks

### Step 1: Project Setup (Camel Dependencies)
- [ ] 3.1 Create payment-processor module pom.xml with camel-quarkus dependencies
- [ ] 3.2 Add quarkus-camel-resilience4j dependency
- [ ] 3.3 Add quarkus-camel-kafka dependency
- [ ] 3.4 Add quarkus-camel-netty-http dependency
- [ ] 3.5 Configure application.properties with Camel, Kafka, Circuit Breaker configs

### Step 2: Kafka Message DTOs
- [ ] 3.6 Create PaymentMessage DTO (implements Serializable)
- [ ] 3.7 Create FraudResult DTO
- [ ] 3.8 Create ProviderResponse DTO

### Step 3: Configuration Classes
- [ ] 3.9 Create FraudRulesConfig with @ConfigProperties
- [ ] 3.10 Create ProviderConfig with @ConfigProperties

### Step 4: Fraud Engine
- [ ] 3.11 Create FraudEvaluationProcessor (implements Processor)
- [ ] 3.12 Implement HIGH_AMOUNT rule (amount > 15000 → +50 score)
- [ ] 3.13 Implement HIGH_RISK_COUNTRY rule (config countries → +30 score)
- [ ] 3.14 Implement RAPID_RETRY rule (attempts > 3 → +25 score)
- [ ] 3.15 Implement NEW_PAYMENT_METHOD rule (new method + age < 30 days → +20 score)
- [ ] 3.16 Implement UNUSUAL_HOUR rule (2am-5am → +15 score)
- [ ] 3.17 Implement risk score capping at 100
- [ ] 3.18 Implement action determination (>=80 REJECT, >=50 REVIEW, <50 APPROVE)
- [ ] 3.19 Create PaymentEnrichProcessor for risk data enrichment

### Step 5: Routes - Main Processor
- [ ] 3.20 Create PaymentProcessorRoute class extending RouteBuilder
- [ ] 3.21 Configure Kafka consumer from payments.events.received topic
- [ ] 3.22 Implement Wire Tap to direct:audit-pipeline
- [ ] 3.23 Implement Content-Based Router logic (amount > 10000, WALLET > 5000)
- [ ] 3.24 Configure Dead Letter Channel with max 3 redeliveries

### Step 6: Routes - Fraud
- [ ] 3.25 Create FraudEngineRoute with direct:fraud-engine
- [ ] 3.26 Implement routing based on FraudResult.action (APPROVE/REVIEW/REJECT)
- [ ] 3.27 Create fraud-reject route publishing to fraud.events.detected
- [ ] 3.28 Create fraud-review-queue route for manual review

### Step 7: Routes - Provider Selection
- [ ] 3.29 Create ContentBasedRouterBean for routing logic
- [ ] 3.30 Create ProviderRouterBean with dynamic router method
- [ ] 3.31 Implement ProviderSelectionRoute with dynamicRouter
- [ ] 3.32 Configure Circuit Breaker on provider-a route (50% threshold, 5s wait)
- [ ] 3.33 Configure fallback from provider-a to provider-b
- [ ] 3.34 Configure retry with exponential backoff (max 5 retries)

### Step 8: Routes - Wire Tap & Audit
- [ ] 3.35 Create audit-pipeline route with Wire Tap destination
- [ ] 3.36 Configure audit route to publish to payments.events.audit topic

### Step 9: Mock Providers
- [ ] 3.37 Create ProviderAService (100-200ms response, 10% failure)
- [ ] 3.38 Create ProviderBService (500-1000ms response, 2% failure)
- [ ] 3.39 Configure provider endpoints in application.properties

### Step 10: Testing & Validation
- [ ] 3.40 Verify payment triggers fraud check via Kafka
- [ ] 3.41 Verify high amount (>10000) routes to fraud-review
- [ ] 3.42 Verify WALLET + amount > 5000 routes to fraud-review
- [ ] 3.43 Verify provider fallback when Provider A fails
- [ ] 3.44 Verify Circuit Breaker opens after 50% failure rate
- [ ] 3.45 Verify Dead Letter Channel receives failed payments after 3 retries
- [ ] 3.46 Verify Wire Tap logs to audit topic
- [ ] 3.47 Verify events published to payments.events.processed on success
- [ ] 3.48 Verify events published to payments.events.failed on fraud reject

## Documentation Tasks
- [ ] 3.49 Document EIP patterns used in Phase 3 design.md
- [ ] 3.50 Document fraud rules in README
- [ ] 3.51 Add flow diagrams for payment processing to README