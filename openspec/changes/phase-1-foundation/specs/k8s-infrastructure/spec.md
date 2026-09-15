## ADDED Requirements

### Requirement: Kubernetes infrastructure manifests
Base manifests SHALL exist for Kafka, Postgres, Jaeger, and Monitoring in kubernetes/infrastructure/.

#### Scenario: Infrastructure manifests are valid YAML
- **WHEN** `kubectl kustomize` or `kubectl apply --dry-run=client` is run against infrastructure manifests
- **THEN** valid Kubernetes resource definitions MUST be produced

### Requirement: Kustomize base directory
The kubernetes/base/ directory SHALL contain deployments, services, configmaps, secrets, HPA, and kustomization.yaml.

#### Scenario: Kustomize build succeeds
- **WHEN** `kubectl kustomize kubernetes/base/` is executed
- **THEN** valid Kubernetes YAML for api-gateway and payment-processor MUST be generated

### Requirement: Kustomize overlays for environments
The kubernetes/overlays/dev/ directory SHALL provide environment-specific overrides.

#### Scenario: Dev overlay applies correctly
- **WHEN** `kubectl kustomize kubernetes/overlays/dev/` is executed
- **THEN** base resources MUST be overlaid with dev-specific namePrefix, replicas, and labels
