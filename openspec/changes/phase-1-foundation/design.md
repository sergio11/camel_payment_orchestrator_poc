# Design: Phase 1 - Foundation

## Architecture

### Project Structure
```
poc_camel/
├── Rakefile                    # SDD workflow automation
├── Gemfile                     # Ruby dependencies
├── package.json                # Node tools (spectral)
├── podman-compose.yaml         # Local services
├── config/
│   └── templates/change/        # SDD templates
├── specs/
│   ├── openapi/                # OpenAPI specs
│   ├── asyncapi/              # AsyncAPI specs
│   └── changes/               # SDD changes
├── kubernetes/
│   └── base/                  # K8s manifests
└── monitoring/                # Prometheus, Grafana configs
```

### Technology Stack
- **Runtime**: Java 17 LTS
- **Framework**: Quarkus 3.x + Camel Quarkus
- **Build**: Maven 3.9
- **Services**: Podman Kube (`podman kube play`)
  - Kafka 3.5 (broker) - Podman Pod
  - Prometheus 2.47 (metrics) - Podman Pod
  - Jaeger 1.48 (tracing) - Podman Pod
  - Grafana 10.1 (dashboards) - Podman Pod
- **Kubernetes Simulation**: Podman Kube manifests (YAML)

### Local Services Configuration
- Kafka: localhost:9092 (external), kafka:29092 (internal)
- Prometheus: localhost:9090
- Jaeger: localhost:16686
- Grafana: localhost:3000

### Rake Tasks Structure
- `sdd:*` - Spec-Driven Development workflow
- `podman:*` - Local services management
- `k8s:*` - Kubernetes operations
- `spec:*` - Spec validation/generation
- `dev:*` - Development helpers

## Components
1. **Project Scaffolding**: Maven multi-module setup
2. **Podman Services**: Docker Compose converted to Podman
3. **Kind Cluster**: Single-node cluster for local K8s
4. **Rake Automation**: Task automation for SDD workflow

## Data Flow
- N/A (no application logic yet)
- Services connectivity verification only

## Configuration
Environment variables managed via `.env` file with fallback to defaults.

## Security Considerations
- No secrets in configs (future phase)
- Development-only settings
- Local network only