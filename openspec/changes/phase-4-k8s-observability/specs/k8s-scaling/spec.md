## ADDED Requirements

### Requirement: Horizontal Pod Autoscaler for payment-processor
An HPA manifest SHALL exist at kubernetes/base/hpa.yaml targeting the payment-processor Deployment.

#### Scenario: HPA targets correct deployment
- **WHEN** the HPA is applied
- **THEN** it MUST target the payment-processor Deployment in poc-camel namespace

#### Scenario: HPA scales on CPU
- **WHEN** average CPU utilization exceeds 70%
- **THEN** HPA MUST scale up (max 10 replicas)

#### Scenario: HPA scales on memory
- **WHEN** average memory utilization exceeds 80%
- **THEN** HPA MUST scale up (max 10 replicas)

#### Scenario: HPA has minimum replicas
- **WHEN** the HPA is applied
- **THEN** minimum replicas MUST be 2

#### Scenario: HPA scale-down stabilization
- **WHEN** metrics drop below thresholds
- **THEN** scale-down MUST wait 300 seconds (stabilization window) before reducing replicas
