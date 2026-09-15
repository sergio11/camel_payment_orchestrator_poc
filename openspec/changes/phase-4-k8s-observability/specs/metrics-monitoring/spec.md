## ADDED Requirements

### Requirement: Prometheus scrape configuration
Prometheus SHALL be configured to scrape application metrics from Kubernetes pods.

#### Scenario: Prometheus scrapes payment-processor metrics
- **WHEN** payment-processor pods are running with prometheus.io/scrape annotation
- **THEN** Prometheus MUST scrape metrics from /q/metrics on port 8080

#### Scenario: Prometheus scrapes api-gateway metrics
- **WHEN** api-gateway pods are running with prometheus.io/scrape annotation
- **THEN** Prometheus MUST scrape metrics from /q/metrics on port 8080

### Requirement: Grafana payment dashboard
A Grafana dashboard SHALL exist at monitoring/grafana-dashboards/payment-dashboard.json.

#### Scenario: Dashboard has request rate panel
- **WHEN** the Grafana dashboard is loaded
- **THEN** a "Payment Request Rate" panel MUST show rate of HTTP requests

#### Scenario: Dashboard has success rate panel
- **WHEN** the Grafana dashboard is loaded
- **THEN** a "Payment Success Rate" gauge panel MUST show percentage of 2xx responses

#### Scenario: Dashboard has latency panel
- **WHEN** the Grafana dashboard is loaded
- **THEN** a "Payment Processing Latency" panel MUST show p50 and p95 latency

#### Scenario: Dashboard has fraud rate panel
- **WHEN** the Grafana dashboard is loaded
- **THEN** a "Fraud Detection Rate" panel MUST show fraud events over time

#### Scenario: Dashboard has memory panel
- **WHEN** the Grafana dashboard is loaded
- **THEN** a "Pod Memory Usage" panel MUST show memory consumption per pod

### Requirement: Prometheus annotations on pods
Both Deployments SHALL include prometheus.io/scrape, prometheus.io/port, and prometheus.io/path annotations.

#### Scenario: Pods have scrape annotations
- **WHEN** a pod is created from the Deployment
- **THEN** it MUST have prometheus.io/scrape="true", prometheus.io/port="8080", prometheus.io/path="/q/metrics"
