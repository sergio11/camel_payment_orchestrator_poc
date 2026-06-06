# Tasks: Phase 3 - Camel Integration

## Implementation Tasks

### Step 1: Project Setup (Camel Dependencies)
- [x] 3.1 Create payment-processor module pom.xml with camel-quarkus dependencies
- [x] 3.2 Add quarkus-camel-resilience4j dependency
- [x] 3.3 Add quarkus-camel-kafka dependency
- [x] 3.4 Add quarkus-camel-netty-http dependency
- [x] 3.5 Configure application.properties with Camel, Kafka, Circuit Breaker configs

### Step 2: Kafka Message DTOs
- [x] 3.6 Create PaymentMessage DTO (implements Serializable)
- [x] 3.7 Create FraudResult DTO
- [x] 3.8 Create ProviderResponse DTO

### Step 3: Configuration Classes
- [x] 3.9 Create FraudRulesConfig with @ConfigProperties
- [x] 3.10 Create ProviderConfig with @ConfigProperties

### Step 4: Fraud Engine
- [x] 3.11 Create FraudEvaluationProcessor (implements Processor)
- [x] 3.12 Implement HIGH_AMOUNT rule (amount > 15000 → +50 score)
- [x] 3.13 Implement HIGH_RISK_COUNTRY rule (config countries → +30 score)
- [x] 3.14 Implement RAPID_RETRY rule (attempts > 3 → +25 score)
- [x] 3.15 Implement NEW_PAYMENT_METHOD rule (new method + age < 30 days → +20 score)
- [x] 3.16 Implement UNUSUAL_HOUR rule (2am-5am → +15 score)
- [x] 3.17 Implement risk score capping at 100
- [x] 3.18 Implement action determination (>=80 REJECT, >=50 REVIEW, <50 APPROVE)
- [x] 3.19 Create PaymentEnrichProcessor for risk data enrichment

### Step 5: Routes - Main Processor
- [x] 3.20 Create PaymentProcessorRoute class extending RouteBuilder
- [x] 3.21 Configure Kafka consumer from payments.events.received topic
- [x] 3.22 Implement Wire Tap to direct:audit-pipeline
- [x] 3.23 Implement Content-Based Router logic (amount > 10000, WALLET > 5000)
- [x] 3.24 Configure Dead Letter Channel with max 3 redeliveries

### Step 6: Routes - Fraud
- [x] 3.25 Create FraudEngineRoute with direct:fraud-engine
- [x] 3.26 Implement routing based on FraudResult.action (APPROVE/REVIEW/REJECT)
- [x] 3.27 Create fraud-reject route publishing to fraud.events.detected
- [x] 3.28 Create fraud-review-queue route for manual review

### Step 7: Routes - Provider Selection
- [x] 3.29 Create ContentBasedRouterBean for routing logic
- [x] 3.30 Create ProviderRouterBean with dynamic router method
- [x] 3.31 Implement ProviderSelectionRoute with dynamicRouter
- [x] 3.32 Configure Circuit Breaker on provider-a route (50% threshold, 5s wait)
- [x] 3.33 Configure fallback from provider-a to provider-b
- [x] 3.34 Configure retry with exponential backoff (max 5 retries)

### Step 8: Routes - Wire Tap & Audit
- [x] 3.35 Create audit-pipeline route with Wire Tap destination
- [x] 3.36 Configure audit route to publish to payments.events.audit topic

### Step 9: Mock Providers
- [x] 3.37 Create ProviderAService (100-200ms response, 10% failure)
- [x] 3.38 Create ProviderBService (500-1000ms response, 2% failure)
- [x] 3.39 Configure provider endpoints in application.properties

### Step 10: Testing & Validation
- [x] 3.40 Verify POST /payments triggers fraud check via Kafka
- [x] 3.41 Verify high amount (>10000) routes to fraud-review
- [x] 3.42 Verify WALLET + amount > 5000 routes to fraud-review
- [x] 3.43 Verify provider fallback when Provider A fails
- [x] 3.44 Verify Circuit Breaker opens after 50% failure rate
- [x] 3.45 Verify Dead Letter Channel receives failed payments after 3 retries
- [x] 3.46 Verify Wire Tap logs to audit topic
- [x] 3.47 Verify events published to payments.events.processed on success
- [x] 3.48 Verify events published to payments.events.failed on fraud reject

### Step 11: Documentation
- [x] 3.49 Document EIP patterns used in Phase 3 design.md
- [x] 3.50 Document fraud rules in README
- [x] 3.51 Add flow diagrams for payment processing to README