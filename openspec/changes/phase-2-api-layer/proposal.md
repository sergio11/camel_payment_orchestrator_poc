## Why

The system needs a REST API to accept payment requests, query status, list with
filtering, and update payment status. This is the public entry point for all
payment operations and must support idempotency, validation, and structured
error responses.

## What Changes

- POST /payments to create payments with idempotency support
- GET /payments with customerId, status, limit, offset filtering and pagination
- GET /payments/{id} for payment retrieval by UUID
- PATCH /payments/{id}/status for status updates
- GET /payments/idempotency/{key} for idempotency-based retrieval
- Health check via SmallRye Health (/q/health)
- OpenAPI documentation via SmallRye OpenAPI
- Bean Validation with custom @SupportedCurrency validator
- Global exception mapper with structured ErrorResponseDTO

## Capabilities

### New Capabilities
- `payment-rest-api`: REST endpoints for payment CRUD operations with hexagonal architecture
- `payment-validation`: Bean Validation with custom validators for currency and field constraints
- `error-handling`: Global exception mapper with MarkerBasedClassifier for structured error responses

### Modified Capabilities
(none)

## Impact

Defines the public API contract for the Payment Orchestration Layer.
All subsequent phases (Camel integration, K8s deployment) build on this API layer.
