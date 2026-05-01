# Risk Assessment: Phase 4 - K8s + Observability

## Identified Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Kind cluster resources limited | Medium | High | Monitor resource usage, limit replicas |
| Container image size too large | Medium | Low | Use Quarkus native build |
| Prometheus scrapes failing | Medium | Low | Check annotations, network policies |
| HPA not working correctly | High | Medium | Test with load generator |
| Memory limits too low | Medium | Low | Monitor and adjust in testing |

## Contingency Plans
- If resource limits: Increase Kind cluster resources
- If image size: Use GraalVM native build
- If HPA issues: Manually scale with kubectl
- If Prometheus issues: Use port-forward for direct access