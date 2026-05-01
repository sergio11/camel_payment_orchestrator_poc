# Tasks: Phase 4 - K8s + Observability

## Implementation Tasks

- [ ] Create Dockerfile for api-gateway
- [ ] Create Dockerfile for payment-processor
- [ ] Update kustomization.yaml with all resources
- [ ] Create HPA manifest with CPU/memory metrics
- [ ] Configure Prometheus scrape annotations
- [ ] Add Jaeger tracing configuration
- [ ] Configure JSON logging in application.properties
- [ ] Create ConfigMaps with environment config
- [ ] Create Secrets for provider URLs
- [ ] Update Rake tasks for K8s deployment

## Validation Tasks

- [ ] Run `kind load docker-image` for both images
- [ ] Run `rake k8s:deploy` - all resources created
- [ ] Run `kubectl get pods` - pods running
- [ ] Run `kubectl get svc` - services available
- [ ] Verify HPA scales under load
- [ ] Verify Prometheus scrapes metrics
- [ ] Verify Grafana shows dashboards
- [ ] Verify Jaeger shows traces

## Documentation Tasks

- [ ] Document Kubernetes deployment process
- [ ] Document observability stack setup
- [ ] Document HPA configuration
- [ ] Document troubleshooting steps