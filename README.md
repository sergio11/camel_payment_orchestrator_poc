<div align="center">

# Payment Orchestration Layer

[![Java 17](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.15-4695EB?style=for-the-badge&logo=quarkus&logoColor=white)](https://quarkus.io/)
[![Apache Camel](https://img.shields.io/badge/Apache_Camel-3.15-E6526F?style=for-the-badge&logo=apachecamel&logoColor=white)](https://camel.apache.org/)
[![Kafka](https://img.shields.io/badge/Apache_Kafka-3.x-231F20?style=for-the-badge&logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-1.28-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white)](https://kubernetes.io/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)
[![Tests](https://img.shields.io/badge/Tests-85+-brightgreen?style=for-the-badge)](#-testing)

A proof-of-concept **Payment Orchestration Layer** built with Apache Camel, Quarkus, and Kafka — demonstrating event-driven architecture, fraud detection, circuit breaker patterns, provider failover, and Kubernetes-native deployment.

---

[📋 Disclaimer](#-disclaimer) · [🚀 Why This Stack?](#-why-this-stack) · [🏗️ Architecture](#%EF%B8%8F-architecture) · [✨ Features](#-features) · [📡 API Reference](#-api-reference) · [⚙️ Configuration](#%EF%B8%8F-configuration) · [🏁 Quick Start](#-quick-start) · [🧪 Testing](#-testing) · [📁 Project Structure](#-project-structure)

</div>

---

## 📋 Disclaimer

This project is developed for **educational and research purposes** only. It is intended to provide hands-on experience and deepen knowledge in **event-driven architecture**, **enterprise integration patterns**, and **payment processing orchestration**. It is **not designed** for deployment in production environments or real-world payment systems.

The fraud detection engine uses simulated risk data. In production, this would be replaced with real risk scoring services, KYC providers, and regulatory compliance systems.

---

## 🚀 Why This Stack?

### ☕ Why Java 17?

Java 17 LTS provides modern language features that significantly improve code quality and reduce boilerplate:

| Feature | Usage in this POC | Benefit |
|---------|-------------------|---------|
| **Records** | `PaymentRequest`, `PaymentResponse`, `PaymentMessage`, `FraudResult` | Immutable DTOs without boilerplate constructors, getters, equals, hashCode |
| **Sealed Interfaces** | `FraudResult` with `Approve`, `Review`, `Reject` permits | Type-safe fraud outcomes with exhaustive switch expressions |
| **Pattern Matching** | `instanceof` in exception mappers | Cleaner exception handling without explicit casts |
| **Switch Expressions** | `PaymentEnrichProcessor` risk tier selection | Expression-based switching with arrow syntax |
| **Text Blocks** | Test JSON payloads | Multi-line strings without concatenation |

### ⚡ Why Quarkus?

Quarkus is a cloud-native Java framework designed for container-first deployment:

- **Developer Experience**: Live coding, fast startup (~30s), hot reload
- **Cloud-Native**: Kubernetes-native with built-in health checks, metrics, OpenAPI
- **MicroProfile**: Standards-based Health, OpenAPI, Config, Metrics via SmallRye
- **Camel Integration**: First-class Apache Camel support via Camel Quarkus
- **GraalVM Ready**: Native compilation for instant startup (not used in this POC)

### 🐪 Why Apache Camel?

Apache Camel provides Enterprise Integration Patterns (EIP) out of the box:

- **Content-Based Router**: Route payments to fraud-check vs fraud-review based on amount, payment method, and country
- **Wire Tap**: Non-blocking parallel audit logging without affecting the main flow
- **Circuit Breaker**: Resilience4j integration for provider fault tolerance
- **Dead Letter Channel**: Capture unrecoverable failures for later analysis
- **Dynamic Router**: Round-robin provider selection with failover capability
- **Kafka Component**: Native consumer/producer integration with Apache Kafka

### 📨 Why Apache Kafka?

Kafka provides the backbone for event-driven architecture:

- **Decoupling**: Backend and payment-processor communicate asynchronously via topics
- **Durability**: Messages persist even if consumers are temporarily unavailable
- **Multi-Consumer**: Same events can be consumed by multiple services (audit, fraud, monitoring)
- **Scalability**: Partition-based parallelism for high-throughput payment processing

### ☸️ Why Kubernetes?

Kubernetes provides production-grade orchestration:

- **HPA**: Auto-scaling based on CPU (70%) and memory (80%) with min 2 / max 10 pods
- **ConfigMaps**: Externalized configuration for fraud rules and provider settings
- **Secrets**: Secure storage for provider credentials
- **Health Probes**: Liveness, readiness, and startup probes for reliable deployments

---

## 💪 Strengths and Weaknesses

### Strengths

| Aspect | Detail |
|--------|--------|
| **Event-Driven Architecture** | Asynchronous processing via Kafka enables loose coupling and horizontal scaling |
| **EIP Patterns** | Content-Based Router, Wire Tap, Circuit Breaker, Dead Letter Channel implemented via Apache Camel |
| **Fraud Detection Engine** | Rule-based scoring with 6 configurable rules and 3 action levels (APPROVE/REVIEW/REJECT) |
| **Fault Tolerance** | Circuit breaker + retry with exponential backoff + provider fallback (A → B → dead letter) |
| **Cloud-Native** | Kubernetes manifests with HPA, ConfigMaps, Secrets, health probes, and Kustomize overlays |
| **Observability** | Three pillars: Prometheus metrics, Jaeger distributed tracing, structured JSON logging |
| **Spec-Driven Development** | OpenAPI 3.0 + AsyncAPI 3.0 specifications with formal phase-based implementation |
| **Java 17 Modern Features** | Records, sealed interfaces, pattern matching, switch expressions throughout the codebase |
| **Test Coverage** | 85+ tests covering unit, integration, and Testcontainers-based Kafka tests |

### Weaknesses / Tradeoffs

| Aspect | Detail |
|--------|--------|
| **In-Memory Storage** | `PaymentRepository` uses `ConcurrentHashMap` — data lost on restart. Production would use PostgreSQL/MongoDB |
| **Simulated Providers** | `ProviderAService` and `ProviderBService` simulate latency and failures with random generation |
| **No Authentication** | REST endpoints are open. Production would require JWT/OAuth2 validation |
| **No Idempotency** | Duplicate payment submissions are not detected. Production would use idempotency keys |
| **No TLS** | Services communicate over plain HTTP. Production would use mTLS |
| **Single-Instance Kafka** | Development uses a single Kafka broker. Production requires a multi-broker cluster |

---

## 🏗️ Architecture

### Component Diagram

```mermaid
graph TB
    Client(["Client"])

    subgraph "API Gateway (port 8080)"
        direction TB
        REST["REST API<br/>PaymentResource"]
        SVC["PaymentService"]
        REPO["PaymentRepository<br/>(in-memory)"]
        KPub["Kafka Publisher"]
        KCon["Kafka Consumer"]
    end

    subgraph "Kafka Cluster"
        direction LR
        T1["payments.events.received"]
        T2["payments.events.processed"]
        T3["payments.events.failed"]
        T4["fraud.events.detected"]
        T5["payments.events.audit"]
        T6["payments.events.dead-letter"]
    end

    subgraph "Payment Processor (port 8081)"
        direction TB
        CBR["Content-Based Router"]
        Fraud["Fraud Engine<br/>(6 rules)"]
        PR["Provider Router<br/>(dynamic)"]
        ProvA["Provider A<br/>(100-200ms, 10% fail)"]
        ProvB["Provider B<br/>(500-1000ms, 2% fail)"]
    end

    subgraph "Observability"
        direction LR
        Prom["Prometheus"]
        Graf["Grafana"]
        Jaeger["Jaeger"]
    end

    Client -->|"POST /payments"| REST
    REST --> SVC
    SVC --> REPO
    SVC --> KPub
    KPub --> T1
    T1 --> CBR
    CBR -->|"high amount / WALLET / high-risk country"| Fraud
    CBR -->|"standard"| Fraud
    Fraud -->|"APPROVE"| PR
    Fraud -->|"REJECT"| T4
    Fraud -->|"REVIEW"| T4
    PR -->|"round-robin"| ProvA
    ProvA -->|"success"| T2
    ProvA -->|"failure"| ProvB
    ProvB -->|"success"| T2
    ProvB -->|"failure"| T6
    T2 --> KCon
    T3 --> KCon
    KCon --> REPO

    style Client fill:#4a9eff,color:#fff,stroke:#2d7dd2
    style REST fill:#ff9800,color:#fff,stroke:#e68900
    style CBR fill:#e91e63,color:#fff,stroke:#c2185b
    style Fraud fill:#f44336,color:#fff,stroke:#d32f2f
    style PR fill:#9c27b0,color:#fff,stroke:#7b1fa2
    style ProvA fill:#4caf50,color:#fff,stroke:#388e3c
    style ProvB fill:#8bc34a,color:#fff,stroke:#689f38
```

### Payment Processing Flow

```mermaid
flowchart TD
    A["POST /payments"] --> B["PaymentService.createPayment()"]
    B --> C["PaymentRepository.save()<br/>status: PENDING"]
    B --> D["Kafka: payments.events.received"]
    D --> E["PaymentProcessorRoute"]
    E --> F["WireTap to payments.events.audit"]
    E --> G["PaymentEnrichProcessor<br/>(risk data enrichment)"]
    G --> H{"Content-Based Router"}
    H -->|"amount > 10,000"| I["direct:fraud-review"]
    H -->|"WALLET + amount > 5,000"| I
    H -->|"high-risk country (XX,YY,ZZ)"| I
    H -->|"default"| J["direct:fraud-check"]
    I --> K["FraudEvaluationProcessor"]
    J --> K
    K --> L{"Risk Score"}
    L -->|"80+ REJECT"| M["fraud.events.detected<br/>payments.events.failed"]
    L -->|"50-79 REVIEW"| N["fraud.events.detected"]
    L -->|"under 50 APPROVE"| O["ProviderSelectionRoute"]
    O --> P{"dynamicRouter<br/>(round-robin)"}
    P --> Q["Provider A<br/>(Circuit Breaker)"]
    Q -->|"success"| R["Kafka: payments.events.processed"]
    Q -->|"failure"| S["Provider B fallback<br/>(Circuit Breaker)"]
    S -->|"success"| R
    S -->|"failure"| T["Kafka: payments.events.dead-letter"]
    R --> U["KafkaPaymentStatusConsumer"]
    U --> V["PaymentRepository.update()<br/>status: APPROVED or FAILED"]
    V --> W["Kafka: payments.events.status.changed"]

    style A fill:#4a9eff,color:#fff,stroke:#2d7dd2
    style H fill:#e91e63,color:#fff,stroke:#c2185b
    style K fill:#f44336,color:#fff,stroke:#d32f2f
    style Q fill:#4caf50,color:#fff,stroke:#388e3c
    style S fill:#8bc34a,color:#fff,stroke:#689f38
    style T fill:#795548,color:#fff,stroke:#5d4037
```

### Circuit Breaker State Machine

```mermaid
stateDiagram-v2
    [*] --> CLOSED: Start
    CLOSED --> OPEN: Failure rate 50% or higher
    OPEN --> HALF_OPEN: Wait 5s
    HALF_OPEN --> CLOSED: 3 successful calls
    HALF_OPEN --> OPEN: Any failure
```

### Fraud Detection Flow

```mermaid
flowchart LR
    A["Payment Message"] --> B{"HIGH_AMOUNT\nabove 15,000?"}
    B -->|Yes| C["+50 points"]
    B -->|No| D{"HIGH_RISK_COUNTRY\nXX, YY, ZZ?"}
    C --> D
    D -->|Yes| E["+30 points"]
    D -->|No| F{"UNUSUAL_HOUR\n2am-5am?"}
    E --> F
    F -->|Yes| G["+15 points"]
    F -->|No| H{"RAPID_RETRY\nabove 3 attempts?"}
    G --> H
    H -->|Yes| I["+25 points"]
    H -->|No| J{"NEW_PAYMENT_METHOD\nunder 30 days?"}
    I --> J
    J -->|Yes| K["+20 points"]
    J -->|No| L{"HIGH_RISK_TIER\n== HIGH?"}
    K --> L
    L -->|Yes| M["+10 points"]
    L -->|No| N{"TOTAL SCORE"}
    M --> N
    N -->|"80+ high risk"| O["🔴 REJECT"]
    N -->|"50-79 medium risk"| P["🟡 REVIEW"]
    N -->|"under 50 low risk"| Q["🟢 APPROVE"]

    style O fill:#f44336,color:#fff
    style P fill:#ff9800,color:#fff
    style Q fill:#4caf50,color:#fff
```

---

## ✨ Features

### 🔗 Enterprise Integration Patterns (EIP)

| Pattern | Implementation | Description |
|---------|---------------|-------------|
| **Content-Based Router** | `ContentBasedRouterBean` | Routes payments to fraud-review vs fraud-check based on amount, payment method, and country |
| **Wire Tap** | `AuditPipelineRoute` | Non-blocking parallel audit logging to Kafka |
| **Circuit Breaker** | Resilience4j via Camel | Provider fault tolerance with 50% failure threshold, 5s wait, 3 half-open calls |
| **Retry with Backoff** | Camel error handler | Exponential backoff: 5 retries, 2s base delay, 2x multiplier |
| **Dead Letter Channel** | `direct:dlq-handler` | Failed payments routed to dead-letter Kafka topic |
| **Dynamic Router** | `ProviderRouterBean` | Round-robin provider selection with failover |
| **Message Enricher** | `PaymentEnrichProcessor` | Enriches payment with simulated risk data (velocity, geo-risk, customer tier) |
| **Message Translator** | `PaymentMapper` | Converts between entity and DTO representations |

### 🛡️ Fraud Detection Engine

| Rule | Condition | Score | Description |
|------|-----------|-------|-------------|
| HIGH_AMOUNT | amount > 15,000 | +50 | Large transaction amount |
| HIGH_RISK_COUNTRY | country in [XX, YY, ZZ] | +30 | High-risk country code |
| RAPID_RETRY | attempts > 3 | +25 | Multiple retry attempts |
| NEW_PAYMENT_METHOD | method age < 30 days | +20 | New payment method for customer |
| UNUSUAL_HOUR | hour between 2-5 AM | +15 | Transaction at unusual time |
| HIGH_RISK_TIER | customerRiskTier == HIGH | +10 | High-risk customer tier (enriched) |

**Risk Score Actions:**
- **≥ 80**: REJECT — Automatic rejection, event published to `fraud.events.detected` and `payments.events.failed`
- **50-79**: REVIEW — Manual review queue, event published to `fraud.events.detected`
- **< 50**: APPROVE — Automatic approval, proceeds to provider selection

**Score Cap:** Maximum risk score capped at 100 (theoretical max without cap: 150)

### 📬 Kafka Topics

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `payments.events.received` | api-gateway | payment-processor | New payment received |
| `payments.events.processed` | payment-processor | api-gateway | Payment approved by provider |
| `payments.events.failed` | payment-processor | api-gateway | Payment rejected/failed |
| `fraud.events.detected` | payment-processor | external | Fraud detection notification |
| `payments.events.status.changed` | api-gateway | external | Status transition event |
| `payments.events.audit` | payment-processor | external | Audit trail (WireTap) |
| `payments.events.dead-letter` | payment-processor | external | Unrecoverable failures |

---

## 📡 API Reference

### 🌐 REST Endpoints

| Method | Path | Description | Status |
|--------|------|-------------|--------|
| `POST` | `/payments` | Create a new payment | `201 Created` |
| `GET` | `/payments` | List payments (filterable) | `200 OK` |
| `GET` | `/payments/{id}` | Get payment by UUID | `200 OK` / `404 Not Found` |
| `GET` | `/payments/{id}/status` | Get payment status | `200 OK` / `404 Not Found` |
| `GET` | `/health/live` | Liveness probe | `200 OK` |
| `GET` | `/health/ready` | Readiness probe | `200 OK` |
| `GET` | `/openapi` | OpenAPI 3.0.3 spec | `200 OK` |
| `GET` | `/swagger-ui` | Swagger UI | `200 OK` |
| `GET` | `/metrics` | Prometheus metrics | `200 OK` |

### 📨 Create Payment

```bash
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 150.00,
    "currency": "USD",
    "customerId": "cust-12345",
    "paymentMethod": "CREDIT_CARD",
    "country": "US",
    "metadata": {"orderId": "order-67890"}
  }'
```

**Response (201):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "amount": 150.00,
  "currency": "USD",
  "customerId": "cust-12345",
  "paymentMethod": "CREDIT_CARD",
  "country": "US",
  "status": "PENDING",
  "metadata": {"orderId": "order-67890"},
  "createdAt": "2026-05-01T10:30:00",
  "updatedAt": "2026-05-01T10:30:00"
}
```

### ✅ Validation Rules

| Field | Rules |
|-------|-------|
| `amount` | Required, min 0.01, max 999999.99, max 2 decimal places |
| `currency` | Required, ISO 4217: USD, EUR, GBP, MXN, JPY |
| `customerId` | Required, max 50 characters |
| `paymentMethod` | Required, enum: CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, WALLET, CRYPTO |
| `country` | Optional, max 2 characters (ISO 3166-1 alpha-2) |
| `metadata` | Optional, arbitrary key-value pairs |

---

## ⚙️ Configuration

### 🖥️ Backend (api-gateway) — `backend/src/main/resources/application.properties`

```properties
# HTTP Server
quarkus.http.port=8080

# Kafka
kafka.bootstrap.servers=localhost:9092
kafka.topic.payments.received=payments.events.received
kafka.topic.payments.processed=payments.events.processed
kafka.topic.payments.failed=payments.events.failed
kafka.topic.payments.status.changed=payments.events.status.changed

# OpenTelemetry
quarkus.opentelemetry.enabled=true
quarkus.opentelemetry.exporter.otlp.endpoint=http://localhost:4317
quarkus.opentelemetry.service.name=payment-gateway

# Logging
quarkus.log.console.json=true
```

### 🔄 Payment Processor — `payment-processor/src/main/resources/application.properties`

```properties
# HTTP Server
quarkus.http.port=8081

# Fraud Rules
fraud.rules.high-amount-threshold=15000
fraud.rules.high-risk-countries=XX,YY,ZZ
fraud.rules.rapid-retry-threshold=3
fraud.rules.new-method-days-threshold=30
fraud.rules.unusual-hour-start=2
fraud.rules.unusual-hour-end=5
fraud.rules.max-risk-score=100
fraud.rules.risk-score-threshold-high=80
fraud.rules.risk-score-threshold-medium=50
fraud.rules.cbr-high-amount-threshold=10000
fraud.rules.cbr-wallet-amount-threshold=5000

# Provider Configuration
provider.a-url=http://localhost:8081/provider-a/process
provider.b-url=http://localhost:8081/provider-b/process
provider.circuit-breaker.failure-threshold=50
provider.circuit-breaker.wait-duration=5s
provider.max-retries=5
provider.retry-backoff=2s
```

---

## 🏁 Quick Start

### 📋 Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java | 17 LTS | Runtime |
| Maven | 3.9+ | Build |
| Podman / Docker | Latest | Container runtime |
| Kind | Latest | Local Kubernetes cluster |
| kubectl | Latest | K8s CLI |

### 1. Build the Project

```bash
./mvnw clean install
```

### 2. Start Infrastructure Services

```bash
# Using Podman Compose (Kafka, Prometheus, Jaeger, Grafana)
podman-compose up -d
```

Services available:
- **Kafka**: localhost:9092
- **Kafka UI**: localhost:8088
- **Prometheus**: localhost:9090
- **Jaeger**: localhost:16686
- **Grafana**: localhost:3000 (admin/admin)

### 3. Run the Services

```bash
# Terminal 1 - API Gateway
./mvnw quarkus:dev -pl backend

# Terminal 2 - Payment Processor
./mvnw quarkus:dev -pl payment-processor
```

### 4. Create a Payment

```bash
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 150.00,
    "currency": "USD",
    "customerId": "cust-001",
    "paymentMethod": "CREDIT_CARD",
    "country": "US"
  }'
```

### 5. Check Payment Status

```bash
curl http://localhost:8080/payments/{id}/status
```

---

## 🧪 Testing

### 🏃 Run All Tests

```bash
./mvnw test
```

### 📦 Run Module Tests

```bash
# Shared module (DTOs, validators)
./mvnw test -pl shared

# Backend module (REST API, service, repository)
./mvnw test -pl backend

# Payment processor module (Camel routes, fraud engine)
./mvnw test -pl payment-processor
```

### 📊 Test Categories

| Category | Framework | Description |
|----------|-----------|-------------|
| Unit Tests | JUnit 5 + Mockito | PaymentService, PaymentRepository, PaymentMapper |
| Validation Tests | Jakarta Validation | PaymentRequest field validation rules |
| Integration Tests | QuarkusTest + REST Assured | REST API endpoint testing |
| Route Tests | QuarkusTest + AdviceWith | Camel route behavior verification |
| Kafka Tests | Testcontainers | End-to-end Kafka integration |
| Concurrency Tests | JUnit 5 | PaymentRepository thread safety |

---

## 📁 Project Structure

```
poc_camel/
├── shared/                              # Shared DTOs, events, validators
│   └── src/main/java/com/poc/shared/
│       ├── dto/                         # PaymentRequest, PaymentResponse, ErrorResponse
│       ├── event/                       # PaymentMessage, FraudResult, ProviderResponse
│       └── validator/                   # @SupportedCurrency custom validator
│
├── backend/                             # API Gateway (port 8080)
│   └── src/main/java/com/poc/gateway/
│       ├── entity/                      # Payment, PaymentStatus
│       ├── repository/                  # PaymentRepository (ConcurrentHashMap)
│       ├── service/                     # PaymentService, KafkaEventPublisher, KafkaPaymentStatusConsumer
│       ├── resource/                    # PaymentResource (JAX-RS)
│       ├── mapper/                      # PaymentMapper
│       └── exception/                   # GlobalExceptionMapper, PaymentNotFoundException
│
├── payment-processor/                   # Camel Route Processor (port 8081)
│   └── src/main/java/com/poc/processor/
│       ├── route/                       # PaymentProcessorRoute, FraudEngineRoute, ProviderSelectionRoute, AuditPipelineRoute
│       ├── processor/                   # FraudEvaluationProcessor, ContentBasedRouterBean, PaymentEnrichProcessor, ProviderRouterBean
│       ├── config/                      # FraudRulesConfig, ProviderConfig, JacksonConfig
│       └── service/                     # ProviderAService, ProviderBService (simulated)
│
├── docker/                              # Multi-stage Dockerfiles
├── kubernetes/                          # K8s manifests (Kustomize)
├── monitoring/                          # Prometheus, Grafana configs
├── openspec/                            # OpenAPI 3.0, AsyncAPI 3.0, phase specs
└── podman-compose.yaml                  # Local infrastructure services
```

---

## 🛠️ Technology Stack

| Component | Technology | Why |
|-----------|-----------|-----|
| Language | Java 17 LTS | Records, sealed classes, pattern matching, switch expressions |
| Framework | Quarkus 3.15 | Cloud-native, fast startup, Kubernetes-native, developer experience |
| Integration | Apache Camel 3.15 | EIP patterns out of box, Kafka/HTTP components, Resilience4j |
| Messaging | Apache Kafka | Event-driven architecture, durable messages, multi-consumer |
| Resilience | Resilience4j | Circuit breaker, retry with backoff, bulkhead patterns |
| Validation | Hibernate Validator 8.x | Jakarta Bean Validation with custom validators |
| Tracing | OpenTelemetry + Jaeger | Distributed tracing with span correlation |
| Metrics | Micrometer + Prometheus | Prometheus-compatible metrics at `/q/metrics` |
| Dashboards | Grafana | Real-time monitoring with payment-specific panels |
| Build | Maven 3.9 | Multi-module build with dependency management |
| Containers | Podman / Docker | Multi-stage builds with JRE Alpine base |
| Orchestration | Kubernetes (Kind) | HPA, ConfigMaps, Secrets, health probes |
| Specs | OpenAPI 3.0.3 + AsyncAPI 3.0 | Formal API and event specifications |

---

## 📐 SDD Workflow

This project follows **Spec-Driven Development (SDD)** with 4 phases:

| Phase | ID | Description | Status |
|-------|----|-------------|--------|
| 1 | foundation | Infrastructure setup (Podman, K8s, monitoring) | Complete |
| 2 | api-layer | REST API implementation | Complete |
| 3 | camel-integration | Camel routes + EIP patterns | Complete |
| 4 | k8s-observability | K8s deployment + monitoring | Complete |

```bash
# List available changes
rake sdd:list

# Initialize a change
rake sdd:init[phase-1-foundation]

# Check change status
rake sdd:check[phase-1-foundation]

# Ship change
rake sdd:ship[phase-1-foundation]
```

---

## 📄 License

This is a Proof of Concept. Not intended for production use.

This project is licensed under the MIT License, an open-source software license that allows developers to freely use, copy, modify, and distribute the software. This includes use in both personal and commercial projects, with the only requirement being that the original copyright notice is retained.

```
MIT License

Copyright (c) 2026 Sergio Sanchez

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

<div align="center">

**Built with ❤️ using Java 17 + Quarkus + Apache Camel**

[⬆️ Back to Top](#-payment-orchestration-layer)

</div>
