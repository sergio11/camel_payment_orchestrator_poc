## ADDED Requirements

### Requirement: Multi-stage Dockerfile for api-gateway
A Dockerfile SHALL exist at docker/Dockerfile.api-gateway with multi-stage Maven build.

#### Scenario: Docker image builds successfully
- **WHEN** `podman build -f docker/Dockerfile.api-gateway` is executed
- **THEN** a container image MUST be produced with the Quarkus app in target/quarkus-app/

#### Scenario: Container runs as non-root user
- **WHEN** the Docker image is started
- **THEN** the process MUST run as user poc (UID 1000), not root

#### Scenario: Health check configured
- **WHEN** the container is running
- **THEN** the Dockerfile HEALTHCHECK MUST verify /q/health/ready is accessible

### Requirement: Multi-stage Dockerfile for payment-processor
A Dockerfile SHALL exist at docker/Dockerfile.payment-processor with multi-stage Maven build.

#### Scenario: Docker image builds successfully
- **WHEN** `podman build -f docker/Dockerfile.payment-processor` is executed
- **THEN** a container image MUST be produced with the Quarkus app in target/quarkus-app/

#### Scenario: Container runs as non-root user
- **WHEN** the Docker image is started
- **THEN** the process MUST run as user poc (UID 1000), not root

### Requirement: Dockerignore excludes unnecessary files
A .dockerignore file at docker/.dockerignore SHALL exclude target/, src/, .git/, *.md, kubernetes/.

#### Scenario: Build context is minimal
- **WHEN** a container image is built
- **THEN** the build context MUST NOT include compiled artifacts, source code, or documentation
