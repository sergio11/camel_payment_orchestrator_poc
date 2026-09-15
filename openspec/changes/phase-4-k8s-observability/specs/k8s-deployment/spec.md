## ADDED Requirements

### Requirement: API Gateway Kubernetes Deployment
A Deployment manifest SHALL exist at kubernetes/base/api-gateway-deployment.yaml.

#### Scenario: Deployment has correct replicas and labels
- **WHEN** the Deployment is applied
- **THEN** pods MUST be created with labels app=api-gateway, component=gateway, version=v1

#### Scenario: Deployment has health probes
- **WHEN** pods are running
- **THEN** liveness probe MUST check /q/health/live and readiness probe MUST check /q/health/ready

#### Scenario: Deployment has resource limits
- **WHEN** pods are created
- **THEN** resource requests MUST be 256Mi memory / 100m CPU and limits MUST be 512Mi / 500m

#### Scenario: Deployment runs as non-root
- **WHEN** pods are created
- **THEN** securityContext MUST set runAsNonRoot=true and runAsUser=1000

### Requirement: Payment Processor Kubernetes Deployment
A Deployment manifest SHALL exist at kubernetes/base/payment-processor-deployment.yaml.

#### Scenario: Deployment has correct replicas
- **WHEN** the Deployment is applied
- **THEN** 2 replicas MUST be created (scalable via HPA)

#### Scenario: Deployment has health probes
- **WHEN** pods are running
- **THEN** liveness probe MUST check /q/health/live and readiness probe MUST check /q/health/ready

#### Scenario: Deployment has resource limits
- **WHEN** pods are created
- **THEN** resource requests MUST be 512Mi memory / 250m CPU and limits MUST be 1Gi / 1000m

### Requirement: Kubernetes Services
ClusterIP Services SHALL exist for api-gateway and payment-processor.

#### Scenario: API Gateway service is accessible
- **WHEN** the Service is applied
- **THEN** ClusterIP service MUST route port 8080 to api-gateway pods

#### Scenario: Payment Processor service is accessible
- **WHEN** the Service is applied
- **THEN** ClusterIP service MUST route port 8080 to payment-processor pods

### Requirement: ConfigMaps for application configuration
ConfigMaps SHALL exist for app-config and fraud-rules-config.

#### Scenario: App config contains Kafka and Jaeger settings
- **WHEN** the app-config ConfigMap is applied
- **THEN** it MUST contain kafka.servers, jaeger.endpoint, and profile keys

#### Scenario: Fraud rules config contains thresholds
- **WHEN** the fraud-rules-config ConfigMap is applied
- **THEN** it MUST contain fraud.high-amount-threshold, fraud.risk-score-threshold-high, and other rule parameters

### Requirement: Secrets for provider configuration
A Secret named provider-secrets SHALL contain provider URLs.

#### Scenario: Provider secrets are available
- **WHEN** the provider-secrets Secret is applied
- **THEN** it MUST contain provider-a-url and provider-b-url

### Requirement: Kustomize base and overlays
Kustomize configuration SHALL exist at kubernetes/base/ and kubernetes/overlays/dev/.

#### Scenario: Base kustomization includes all resources
- **WHEN** `kubectl kustomize kubernetes/base/` is executed
- **THEN** all deployments, services, configmaps, secrets, and HPA MUST be included

#### Scenario: Dev overlay applies overrides
- **WHEN** `kubectl kustomize kubernetes/overlays/dev/` is executed
- **THEN** namePrefix=dev- MUST be applied and replicas MUST match dev configuration
