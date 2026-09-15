## ADDED Requirements

### Requirement: Kafka broker available locally
Kafka SHALL run via Podman Compose and be reachable on localhost:9092 (external) and kafka:29092 (internal).

#### Scenario: Kafka accepts connections
- **WHEN** Podman Compose services are started
- **THEN** Kafka broker MUST accept connections on localhost:9092

### Requirement: Prometheus metrics collection
Prometheus SHALL run on localhost:9090 and scrape application metrics.

#### Scenario: Prometheus is accessible
- **WHEN** Podman Compose services are started
- **THEN** Prometheus UI MUST be available at localhost:9090

### Requirement: Jaeger distributed tracing
Jaeger SHALL run on localhost:16686 for trace visualization.

#### Scenario: Jaeger UI is accessible
- **WHEN** Podman Compose services are started
- **THEN** Jaeger UI MUST be available at localhost:16686

### Requirement: Grafana dashboards
Grafana SHALL run on localhost:3000 with pre-provisioned Prometheus datasource and payment dashboard.

#### Scenario: Grafana shows payment dashboard
- **WHEN** Podman Compose services are started
- **THEN** Grafana UI MUST be available at localhost:3000 with a Payment Orchestration dashboard
