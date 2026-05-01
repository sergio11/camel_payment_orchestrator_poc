# Design: Phase 4 - K8s + Observability

## Architecture

### Kubernetes Deployment
```
┌─────────────────────────────────────────────────────┐
│                   KIND CLUSTER                       │
├─────────────────────────────────────────────────────┤
│                                                      │
│  ┌─────────────────┐    ┌─────────────────┐       │
│  │  api-gateway    │    │ payment-processor│       │
│  │  Deployment     │    │ Deployment       │       │
│  │  (1 replica)    │    │ (2+ replicas)    │       │
│  └─────────────────┘    └─────────────────┘       │
│          │                       │                  │
│          └───────────┬───────────┘                  │
│                      ▼                              │
│            ┌─────────────────┐                     │
│            │  ClusterIP Svc   │                     │
│            └─────────────────┘                     │
│                                                      │
│  ┌─────────────────────────────────────────────┐   │
│  │  HorizontalPodAutoscaler                     │   │
│  │  min: 2, max: 10, target: 70% CPU           │   │
│  └─────────────────────────────────────────────┘   │
│                                                      │
└─────────────────────────────────────────────────────┘
```

### Observability Stack
```
┌─────────────┐  scrape   ┌─────────────┐  query    ┌──────────┐
│ Application │ ─────────▶│ Prometheus  │ ─────────▶│ Grafana  │
│ (metrics)   │           │ (time series)│           │ (dashboards)
└─────────────┘           └─────────────┘           └──────────┘

┌─────────────┐  trace    ┌─────────────┐  query    ┌──────────┐
│ Application │ ─────────▶│   Jaeger    │ ─────────▶│   UI     │
│ (tracing)   │           │ (collector) │           │          │
└─────────────┘           └─────────────┘           └──────────┘
```

### Configuration Management
- **ConfigMaps**: Kafka servers, Jaeger endpoint, application profile
- **Secrets**: Provider URLs, API keys (simulated)

### Metrics Exposed
| Metric | Type | Description |
|--------|------|-------------|
| payment_requests_total | Counter | Total payment requests |
| payment_latency_seconds | Histogram | Payment processing latency |
| payment_status | Counter | Payments by status |
| fraud_detected_total | Counter | Fraud detections |
| provider_fallback_total | Counter | Provider fallback count |

### Logging Strategy
- JSON structured logging
- Include: timestamp, level, message, paymentId, traceId
- Use Quarkus JSON logging extension

## Components

1. **api-gateway-deployment.yaml**: REST API deployment
2. **payment-processor-deployment.yaml**: Camel processor deployment
3. **services.yaml**: ClusterIP services
4. **configmaps.yaml**: Application configuration
5. **hpa.yaml**: Horizontal Pod Autoscaler
6. **prometheus-config.yaml**: Prometheus scrape config
7. **grafana-dashboards**: Pre-built dashboards

## Data Flow
1. Build container images
2. Load images into Kind
3. Apply K8s manifests
4. Services exposed via port-forward
5. Metrics scraped by Prometheus
6. Traces sent to Jaeger

## Security Considerations
- Secrets base64 encoded (not encrypted)
- No network policies (dev mode)
- No TLS (dev mode)