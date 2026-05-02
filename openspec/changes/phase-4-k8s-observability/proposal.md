# Proposal: Phase 4 - K8s + Observability

## Overview
Deploy the Payment Orchestration Layer to Kubernetes with full observability stack (metrics, logging, tracing).

## Problem Statement
The system needs to be:
- Deployable to Kubernetes (Kind for local, K8s for production)
- Observable with metrics, logs, and distributed tracing
- Configurable via ConfigMaps and Secrets
- Scalable with Horizontal Pod Autoscaler

## Goals
- Create Kubernetes manifests for all components
- Configure Horizontal Pod Autoscaler (HPA)
- Set up Prometheus metrics collection
- Configure Grafana dashboards
- Set up Jaeger distributed tracing
- Configure logging with structured JSON
- Set up ConfigMaps for configuration
- Set up Secrets for sensitive data

## Non-Goals
- Production-grade security (TLS, RBAC)
- Complex service mesh
- Multi-cluster deployment

## Success Metrics
- Application deploys to Kind cluster
- HPA scales based on CPU/memory
- Prometheus scrapes metrics
- Grafana shows dashboards
- Jaeger shows distributed traces
- Logs in structured JSON format

## Timeline
- Start: Week 5
- Target: Week 5-6 completion

## Dependencies
- Phase 3 (Camel Integration) must be complete
- Kind cluster running (from Phase 1)