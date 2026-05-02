# Risk Assessment: Phase 4 - K8s + Observability

## Identified Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Kind cluster resources limited (CPU/RAM) | High | High | Limit replicas, monitor resource usage, increase cluster size if needed |
| Container image build fails | High | Low | Verify Maven build works locally, check network access to Maven Central |
| Container image too large (>1GB) | Medium | Low | Use multi-stage build, Alpine base image, Quarkus native build option |
| Prometheus scrape failing | High | Medium | Verify prometheus.io annotations, check network policies, use port-forward |
| Grafana dashboard empty | Medium | Low | Verify Prometheus datasource, check metric names, import dashboard JSON |
| Jaeger traces not showing | Medium | Medium | Verify Jaeger endpoint, check OTEL configuration, verify service names |
| HPA not scaling correctly | High | Medium | Test with load generator (hey/k6), verify metrics exposed, check HPA events |
| Memory limits too low causing OOMKilled | High | Medium | Monitor memory usage, adjust limits based on actual consumption |
| Liveness/Readiness probes failing | High | Medium | Verify /q/health endpoints, check probe timing, increase initialDelaySeconds |
| ConfigMap updates not reflected | Low | Medium | Restart pods after ConfigMap changes: kubectl rollout restart deployment |
| Secrets not accessible | High | Low | Verify secret exists, check envFrom secretRef, verify namespace |
| Kind cluster crash due to resource exhaustion | High | Low | Set resource quotas, limit pod count, monitor cluster health |

## Contingency Plans

| Scenario | Action |
|----------|--------|
| Kind resources limited | Increase Kind cluster resources: kind create cluster --name poc-camel --replicas 1 --wait 5m |
| Image build fails | Check Maven: mvn clean package; verify pom.xml dependencies |
| Prometheus not scraping | Verify annotations: kubectl get pods -o jsonpath='{range .items[*]}{.metadata.name}{.metadata.annotations}{"\n"}{end}' |
| Grafana empty | Add datasource manually: Configuration > Data Sources > Add Prometheus |
| Jaeger no traces | Check OTEL env vars: OTEL_EXPORTER_JAEGER_ENDPOINT, OTEL_SERVICE_NAME |
| HPA not working | Check metrics: kubectl top pods; kubectl describe hpa |
| OOMKilled | Increase memory limits in deployment, check JVM heap settings |
| Probe failures | Check logs: kubectl logs <pod> --previous; verify health endpoint locally |
| ConfigMap not updating | Restart: kubectl rollout restart deployment/<name> -n poc-camel |
| Secret access denied | Verify: kubectl get secrets -n poc-camel; check role bindings |

## Testing Requirements

- Minimum 8 validation tests covering:
  1. Build: `podman build` succeeds for both images
  2. Deploy: `kubectl apply -k overlays/dev` creates all resources
  3. Pods: All pods reach Running state within 2 minutes
  4. Services: ClusterIP services respond on port 8080
  5. HPA: HPA resource created with correct min/max
  6. Prometheus: Targets show payment-processor and api-gateway
  7. Grafana: Dashboard loads with data (may need mock traffic)
  8. Jaeger: Service visible in Jaeger UI (may need trace generation)

## Technical Risks

| Risk | Description | Mitigation |
|------|-------------|------------|
| Multi-arch build | Images may not work on different architectures | Use amd64 platform explicitly: --platform linux/amd64 |
| Image tagging | Latest tag may cause caching issues | Use semantic versioning or commit SHA |
| Namespace | Default namespace may conflict | Always use poc-camel namespace |
| Resource quotas | No resource quotas may cause cluster exhaustion | Set ResourceQuota if needed |
| Network policies | Pods may not communicate | Use ClusterIP services, verify DNS |
| Volume mounts | EmptyDir may not persist data | Use PersistentVolumeClaim for production |
| RBAC | ServiceAccount may lack permissions | Use default ServiceAccount for dev |