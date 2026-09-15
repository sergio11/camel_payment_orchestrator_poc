# Security Policy

## Overview

This document describes the security measures implemented in the Payment Orchestration Layer proof-of-concept. This project is designed for **educational and research purposes only** and is **not intended for production deployment**.

---

## Scope

| In Scope | Out of Scope |
|----------|--------------|
| Kubernetes manifests (security context, network policies) | Real payment processing |
| Docker/container configuration | External secrets management (Vault, etc.) |
| Application-level CORS and Swagger UI settings | JWT/OAuth2 authentication |
| Secrets management for local development | mTLS between services |
| Grafana and monitoring security | Multi-broker Kafka with SASL/SSL |

---

## Reporting a Vulnerability

If you discover a security issue in this POC, please report it responsibly:

1. **Do not** open a public GitHub issue for security vulnerabilities
2. Contact the maintainer directly via email
3. Include a description of the vulnerability, steps to reproduce, and potential impact
4. Allow reasonable time for remediation before public disclosure

---

## Secrets Management

### Demo Secrets

All demo secrets have been rotated to `poc-demo-*` values. These are **not real credentials** and must never be used in production.

| Secret | Value | Purpose |
|--------|-------|---------|
| Grafana admin password | `poc-demo-pass-2026` | Local demo only |
| K8s `secrets.yaml` | Template with placeholders | Local development |

### Gitignored Files

The following files contain sensitive data and are excluded via `.gitignore`:

- `.env` — Environment variables for local development
- `secrets.yaml` — Kubernetes secrets manifest
- `monitoring/grafana.env` — Grafana credentials

Reference templates are provided:

- `secrets.yaml.example` — Contains placeholder values
- `monitoring/grafana.env.example` — Contains placeholder values

### Git History

Old secret values may still exist in git history. The decision was made **not to rewrite history**. If you suspect exposure:

1. Rotate the affected credentials immediately
2. Review `git diff` before publishing any changes
3. Use `monitoring/grafana.env` (gitignored) or Vault for real credential injection

---

## Kubernetes Security Hardening

### Security Context (Base Manifests)

| Setting | Value | Rationale |
|---------|-------|-----------|
| `readOnlyRootFilesystem` | `true` | Prevents runtime file modifications |
| `allowPrivilegeEscalation` | `false` | Blocks privilege escalation attacks |
| `capabilities.drop` | `ALL` | Removes all Linux capabilities |
| `runAsNonRoot` | `true` | Prevents running as root |
| `runAsUser` | `1000` | Non-root UID |
| `seccompProfile.type` | `RuntimeDefault` | Applies default seccomp profile |
| `/tmp` | `emptyDir` | Writable temp directory for read-only root |

### Image Pull Policy

| Environment | Policy | Reason |
|-------------|--------|--------|
| Base / Dev | `IfNotPresent` | Faster local development |
| Prod overlay | `Always` | Ensures latest images in production |

### Service Account

- Dedicated service account: `poc-camel-sa`
- RBAC roles (Role, RoleBinding) are **not yet defined** — recommended for production deployments

---

## Network Policies

A **default deny-all** policy is applied, with explicit allow rules:

| Source | Destination | Port/Protocol | Purpose |
|--------|-------------|---------------|---------|
| api-gateway | payment-processor | TCP | Payment processing |
| api-gateway | kafka | TCP/9092 | Event publishing |
| payment-processor | kafka | TCP/9092 | Event consuming/producing |
| All apps | jaeger | TCP/4317 | OpenTelemetry traces |
| prometheus | All apps | TCP/9090 | Metrics scraping |

---

## CORS & API Documentation

### CORS

CORS origins are restricted via environment variable:

```
${CORS_ORIGINS:https://localhost:3000}
```

- Default: `https://localhost:3000` (local development)
- No wildcard (`*`) is used in any profile

### Swagger UI

| Profile | Enabled | Path |
|---------|---------|------|
| `%dev` | Yes | `/swagger-ui` |
| `%test` | Yes | `/swagger-ui` |
| `%prod` | **No** | Disabled |

OpenAPI spec is available at `/openapi` in all profiles.

---

## Container Images

| Image | Tag Strategy | Notes |
|-------|-------------|-------|
| Application images | `:latest` | Rebuilt on each deploy |
| `kafka-ui` | Pinned (`v0.7.2`) | Stability for UI tooling |
| `openspec` | Pinned | Stability for spec validation |

---

## Grafana

### Demo Credentials

- **Username:** `poc-admin`
- **Password:** `poc-demo-pass-2026`

These credentials are **local-only** and used exclusively with:

- `podman-compose.yaml` (local infrastructure)
- `monitoring/grafana.ini` (demo configuration)

### Rotation Procedure

If demo credentials are accidentally exposed:

1. Rotate immediately in all environments
2. Use `monitoring/grafana.env` (gitignored) for real credentials
3. See `monitoring/grafana.env.example` for the expected format

---

## Production Recommendations

The following security measures are **not implemented** in this POC but are recommended for production:

| Area | Recommendation |
|------|----------------|
| Authentication | JWT/OAuth2 on REST endpoints |
| Transport Security | mTLS between all services |
| Secrets Management | Vault, ExternalSecrets, or SealedSecrets |
| Kafka Security | Multi-broker cluster with SASL/SSL |
| Fraud Detection | Real risk scoring services replacing simulated engine |
| RBAC | Define Role and RoleBinding manifests for `poc-camel-sa` |
