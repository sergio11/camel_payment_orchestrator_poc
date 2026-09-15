## Why

The application needs to be deployable to Kubernetes with full observability
(metrics, distributed tracing, structured logging) for operational readiness.
This includes container images, deployment manifests, autoscaling, and monitoring
dashboards.

## What Changes

- Dockerfiles for both services with multi-stage Maven builds and non-root users
- Kubernetes Deployments, Services, ConfigMaps, Secrets, HPA
- Kustomize overlays for dev environment
- Prometheus metrics scraping with pod annotations
- Grafana dashboards for payment monitoring (7 panels)
- Jaeger distributed tracing via OpenTelemetry
- Structured JSON logging with traceId correlation
- Rake tasks for K8s build, load, deploy, smoke test

## Capabilities

### New Capabilities
- `container-images`: Multi-stage Dockerfiles for api-gateway and payment-processor
- `k8s-deployment`: Kubernetes manifests for both services with health probes and resource limits
- `k8s-scaling`: Horizontal Pod Autoscaler with CPU/memory metrics
- `metrics-monitoring`: Prometheus scrape configuration and Grafana dashboards
- `distributed-tracing`: Jaeger via OpenTelemetry with OTLP exporter
- `structured-logging`: JSON logs with traceId/spanId correlation

### Modified Capabilities
(none)

## Impact

Enables production-like deployment and monitoring of the Payment Orchestration Layer
on Kubernetes with Kind for local development.
