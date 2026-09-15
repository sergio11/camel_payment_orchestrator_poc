## ADDED Requirements

### Requirement: Create payment via POST /payments
Clients SHALL be able to create payments by POSTing a PaymentRequestDTO with optional Idempotency-Key header.

#### Scenario: Valid payment request returns 201
- **WHEN** a valid PaymentRequestDTO is POSTed to /payments
- **THEN** a 201 response MUST be returned with PaymentResponseDTO containing status=PENDING

#### Scenario: Invalid payment request returns 400
- **WHEN** an invalid PaymentRequestDTO (missing required fields, invalid values) is POSTed to /payments
- **THEN** a 400 response MUST be returned with ErrorResponseDTO containing VALIDATION_ERROR

#### Scenario: Idempotency key prevents duplicate creation
- **WHEN** the same Idempotency-Key header is used for two POST /payments requests
- **THEN** the second request MUST return the same payment as the first

### Requirement: List payments via GET /payments
Clients SHALL be able to list payments with optional customerId, status, limit, offset filters.

#### Scenario: List all payments
- **WHEN** GET /payments is called without filters
- **THEN** a PaymentPageResponseDTO MUST be returned with payments array, total, limit, offset

#### Scenario: Filter by customer ID
- **WHEN** GET /payments?customerId=cust-123 is called
- **THEN** only payments for that customer MUST be returned

#### Scenario: Filter by status
- **WHEN** GET /payments?status=APPROVED is called
- **THEN** only payments with APPROVED status MUST be returned

#### Scenario: Pagination with limit and offset
- **WHEN** GET /payments?limit=10&offset=20 is called
- **THEN** 10 payments starting from offset 20 MUST be returned

### Requirement: Get payment by ID via GET /payments/{id}
Clients SHALL be able to retrieve a specific payment by UUID.

#### Scenario: Payment exists
- **WHEN** GET /payments/{id} is called with a valid existing UUID
- **THEN** a 200 response with PaymentResponseDTO MUST be returned

#### Scenario: Payment not found
- **WHEN** GET /payments/{id} is called with a non-existent UUID
- **THEN** a 404 response with ErrorResponseDTO (NOT_FOUND) MUST be returned

### Requirement: Update payment status via PATCH /payments/{id}/status
Clients SHALL be able to update payment status by sending an UpdatePaymentStatusRequestDTO.

#### Scenario: Valid status update returns 200
- **WHEN** a valid UpdatePaymentStatusRequestDTO is PATCHed to /payments/{id}/status
- **THEN** a 200 response with updated PaymentResponseDTO MUST be returned

#### Scenario: Invalid status returns 400
- **WHEN** an invalid status value is sent
- **THEN** a 400 response with ErrorResponseDTO MUST be returned

#### Scenario: Non-existent payment returns 404
- **WHEN** PATCH is called with a non-existent UUID
- **THEN** a 404 response with ErrorResponseDTO MUST be returned

### Requirement: Get payment by idempotency key via GET /payments/idempotency/{key}
Clients SHALL be able to retrieve a payment using its idempotency key.

#### Scenario: Idempotency key exists
- **WHEN** GET /payments/idempotency/{key} is called with a valid key
- **THEN** a 200 response with PaymentResponseDTO MUST be returned

#### Scenario: Idempotency key not found
- **WHEN** GET /payments/idempotency/{key} is called with a non-existent key
- **THEN** a 404 response MUST be returned

### Requirement: Health check via GET /q/health
The service SHALL expose health endpoints via SmallRye Health.

#### Scenario: Liveness check
- **WHEN** GET /q/health/live is called
- **THEN** a 200 response with status UP MUST be returned

#### Scenario: Readiness check
- **WHEN** GET /q/health/ready is called
- **THEN** a 200 response with status UP and component checks MUST be returned
