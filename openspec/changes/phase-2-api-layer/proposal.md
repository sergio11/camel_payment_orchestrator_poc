# Proposal: Phase 2 - API Layer

## Overview
Implement the REST API layer for the Payment Orchestration Layer using Quarkus, following a spec-first approach with OpenAPI 3.0 specification.

## Problem Statement
The system needs a REST API to:
- Accept payment requests via POST /payments
- Query payment status via GET /payments/{id}
- List payments with filtering via GET /payments
- Provide health check endpoint

## Goals
- Define OpenAPI specification for all payment endpoints
- Implement Quarkus REST resources with proper validation
- Configure Bean Validation for request bodies
- Implement proper error handling (400, 404, 422, 500)
- Enable OpenAPI documentation endpoint
- Set up basic in-memory payment storage

## Non-Goals
- No actual payment processing (deferred to Phase 3)
- No Kafka integration yet
- No database (in-memory only)
- No authentication/authorization

## Success Metrics
- All endpoints return correct HTTP status codes
- Invalid requests return 400 with error details
- OpenAPI docs available at /q/openapi
- Health endpoint returns UP status

## Timeline
- Start: Week 2
- Target: Week 2 completion

## Dependencies
- Phase 1 foundation must be complete