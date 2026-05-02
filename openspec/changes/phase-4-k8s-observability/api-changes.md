# API Changes: Phase 4 - K8s + Observability

## New Endpoints
None - no REST API changes in this phase.

## Modified Endpoints
None - existing endpoints work the same way.

## Deprecated Endpoints
None.

## Schema Changes
None.

## Infrastructure Changes

### Kubernetes Resources

| Resource Type | Name | Purpose |
|---------------|------|---------|
| Deployment | api-gateway | REST API (1 replica) |
| Deployment | payment-processor | Camel processor (2-10 replicas via HPA) |
| Service | api-gateway | ClusterIP:8080 |
| Service | payment-processor | ClusterIP:8080 |
| ConfigMap | app-config | Application configuration |
| ConfigMap | fraud-rules-config | Fraud detection thresholds |
| Secret | provider-secrets | Provider URLs |
| HPA | payment-processor-hpa | Auto-scaling (2-10 replicas) |

### New Endpoints for Observability

| Endpoint | Component | Purpose |
|----------|-----------|---------|
| /q/metrics | Both | Prometheus metrics scrape endpoint |
| /q/health/live | Both | Kubernetes liveness probe |
| /q/health/ready | Both | Kubernetes readiness probe |
| /q/health | Both | Combined health check |
| /q/openapi | api-gateway | OpenAPI specification |
| /q/swagger-ui | api-gateway | Swagger documentation |

### Environment Variables

| Variable | Source | Purpose |
|----------|--------|---------|
| QUARKUS_PROFILE | ConfigMap | Application profile (dev) |
| KAFKA_BOOTSTRAP_SERVERS | ConfigMap | Kafka connection |
| JAEGER_ENDPOINT | ConfigMap | Tracing collector |
| provider-a-url | Secret | Provider A endpoint |
| provider-b-url | Secret | Provider B endpoint |

### Health Probe Configuration

| Probe | Path | Initial Delay | Period | Timeout | Failure Threshold |
|-------|------|---------------|--------|---------|-------------------|
| Liveness | /q/health/live | 30s | 30s | 10s | 3 |
| Readiness | /q/health/ready | 5s | 10s | 5s | 3 |

### Resource Limits

| Component | CPU Request | CPU Limit | Memory Request | Memory Limit |
|-----------|-------------|-----------|----------------|---------------|
| api-gateway | 100m | 500m | 256Mi | 512Mi |
| payment-processor | 250m | 1000m | 512Mi | 1Gi |

### Auto-Scaling Behavior

| Metric | Threshold | Action |
|--------|-----------|--------|
| CPU | > 70% | Scale up (max 10) |
| Memory | > 80% | Scale up (max 10) |
| CPU | < 30% | Scale down (min 2) |
| Memory | < 60% | Scale down (min 2) |

### Access Methods

| Service | Local Access | Kind Access |
|---------|--------------|-------------|
| API Gateway | kubectl port-forward svc/api-gateway 8080:8080 | http://localhost:8080 |
| Payment Processor | kubectl port-forward svc/payment-processor 8081:8080 | http://localhost:8081 |
| Prometheus | kubectl port-forward svc/prometheus 9090:9090 | http://localhost:9090 |
| Grafana | kubectl port-forward svc/grafana 3000:3000 | http://localhost:3000 (admin/admin) |
| Jaeger | kubectl port-forward svc/jaeger 16686:16686 | http://localhost:16686 |

### Kafka (External to K8s)

| Service | Host | Port | Purpose |
|---------|------|------|---------|
| Kafka | localhost (Podman) | 9092 | Message broker |
| Kafka UI | localhost | 8088 | Topic management |