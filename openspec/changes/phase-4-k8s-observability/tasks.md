# Tasks: Phase 4 - K8s + Observability

## Implementation Tasks

### Step 1: Docker Configuration
- [x] 4.1 Create docker/Dockerfile.api-gateway with multi-stage maven build
- [x] 4.2 Create docker/Dockerfile.payment-processor with multi-stage maven build
- [x] 4.3 Create docker/.dockerignore with target/, src/, *.md exclusions
- [x] 4.4 Verify Dockerfile syntax and base images are accessible

### Step 2: Kubernetes Base Manifests
- [x] 4.5 Create kubernetes/base/api-gateway-deployment.yaml with full specs
- [x] 4.6 Create kubernetes/base/payment-processor-deployment.yaml with full specs
- [x] 4.7 Create kubernetes/base/services.yaml (api-gateway, payment-processor ClusterIP)
- [x] 4.8 Create kubernetes/base/configmaps.yaml (app-config, fraud-rules-config)
- [x] 4.9 Create kubernetes/base/secrets.yaml (provider-secrets)
- [x] 4.10 Create kubernetes/base/hpa.yaml with CPU 70%, memory 80%, min 2, max 10
- [x] 4.11 Create kubernetes/base/kustomization.yaml with all resources

### Step 3: Kustomization Overlays
- [x] 4.12 Create kubernetes/overlays/dev/kustomization.yaml
- [x] 4.13 Configure namespace: poc-camel in overlays
- [x] 4.14 Configure replicas (api-gateway: 1, payment-processor: 2)
- [x] 4.15 Create kubernetes/overlays/dev/patches/ directory structure

### Step 4: Observability - Prometheus
- [x] 4.16 Add prometheus.io annotations to deployments (scrape, port, path)
- [x] 4.17 Create monitoring/prometheus-config.yaml with scrape configs
- [x] 4.18 Configure scrape for payment-processor (metrics_path=/q/metrics)
- [x] 4.19 Configure scrape for api-gateway (metrics_path=/q/metrics)
- [x] 4.20 Add Kafka scrape config (kafka:9092)

### Step 5: Observability - Grafana
- [x] 4.21 Create monitoring/grafana-dashboards/payment-dashboard.json
- [x] 4.22 Add Payment Request Rate panel (graph, rate)
- [x] 4.23 Add Payment Success Rate panel (gauge, percentage)
- [x] 4.24 Add Payment Latency panel (graph, p50/p95)
- [x] 4.25 Add Fraud Detection Rate panel (graph)
- [x] 4.26 Add Provider Fallback Rate panel (graph)
- [x] 4.27 Add Pod Memory Usage panel (graph)
- [x] 4.28 Create monitoring/grafana-provisioning/datasources/prometheus.yaml

### Step 6: Observability - Jaeger
- [x] 4.29 Configure jaeger.endpoint in configmaps.yaml
- [x] 4.30 Add OTEL_JAVA_AGENT configuration to deployments
- [x] 4.31 Configure jaeger.service.name per component
- [x] 4.32 Add traceId/spanId to log format

### Step 7: Observability - Logging
- [x] 4.33 Configure quarkus.log.console.json=true in application.properties
- [x] 4.34 Configure log format with traceId=%X{traceId}
- [x] 4.35 Configure category levels (camel: INFO, kafka: WARN)

### Step 8: Rake Integration
- [x] 4.36 Update Rakefile with k8s:build task (podman build both images)
- [x] 4.37 Update Rakefile with k8s:load task (kind load docker-image)
- [x] 4.38 Add k8s:logs[pods] task
- [x] 4.39 Add k8s:port[service,port] task
- [x] 4.40 Add k8s:scale[deployment,replicas] task
- [x] 4.41 Add k8s:restart[deployment] task
- [x] 4.42 Add k8s:describe[resource] task
- [x] 4.43 Add k8s:all task (show all resources)

### Step 9: Validation - Build & Deploy
- [ ] 4.44 Verify podman build succeeds for api-gateway
- [ ] 4.45 Verify podman build succeeds for payment-processor
- [ ] 4.46 Run rake k8s:build - both images built
- [ ] 4.47 Run rake k8s:load - images loaded into Kind
- [ ] 4.48 Run rake k8s:deploy - all resources created

### Step 10: Validation - K8s Resources
- [ ] 4.49 Verify kubectl get pods - pods running (api-gateway, payment-processor)
- [ ] 4.50 Verify kubectl get svc - services available
- [ ] 4.51 Verify kubectl get hpa - HPA created
- [ ] 4.52 Verify kubectl get configmaps - app-config, fraud-rules-config
- [ ] 4.53 Verify kubectl get secrets - provider-secrets

### Step 11: Validation - Observability
- [ ] 4.54 Verify Prometheus accessible at localhost:9090
- [ ] 4.55 Verify Prometheus scrapes payment-processor metrics
- [ ] 4.56 Verify Grafana accessible at localhost:3000 (admin/admin)
- [ ] 4.57 Verify Grafana shows payment dashboard
- [ ] 4.58 Verify Jaeger accessible at localhost:16686
- [ ] 4.59 Verify JSON logs show traceId in output
- [ ] 4.60 Verify health endpoints work: /q/health/live, /q/health/ready

## Documentation Tasks
- [ ] 4.61 Document Kubernetes deployment process in README
- [ ] 4.62 Document observability stack setup (Prometheus, Grafana, Jaeger)
- [ ] 4.63 Document HPA configuration and scaling behavior
- [ ] 4.64 Document troubleshooting steps (common issues and solutions)