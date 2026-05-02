# Tasks: Phase 2 - API Layer

## Implementation Tasks

### Step 1: Project Setup
- [ ] 2.1 Create parent pom.xml with Quarkus BOM
- [ ] 2.2 Create api-gateway/pom.xml with dependencies
- [ ] 2.3 Create application.properties with config

### Step 2: Domain Layer
- [ ] 2.4 Create Payment entity (com.poc.gateway.entity.Payment)
- [ ] 2.5 Create PaymentStatus enum
- [ ] 2.6 Create PaymentRepository (ConcurrentHashMap storage)

### Step 3: DTO Layer
- [ ] 2.7 Create PaymentRequest DTO with validation annotations (@NotNull, @DecimalMin, @Size, @Pattern)
- [ ] 2.8 Create PaymentResponse DTO with Jackson annotations
- [ ] 2.9 Create PaymentStatusResponse DTO
- [ ] 2.10 Create ErrorResponse and ErrorDetail DTOs

### Step 4: Custom Validators
- [ ] 2.11 Create @SupportedCurrency annotation
- [ ] 2.12 Create SupportedCurrencyValidator implementation
- [ ] 2.13 Apply @SupportedCurrency to PaymentRequest.currency

### Step 5: Business Logic
- [ ] 2.14 Create PaymentMapper (Entity <-> DTO conversion)
- [ ] 2.15 Create PaymentService with createPayment, getPayment, listPayments methods

### Step 6: REST Layer
- [ ] 2.16 Create PaymentNotFoundException
- [ ] 2.17 Create PaymentNotFoundExceptionMapper
- [ ] 2.18 Create GlobalExceptionMapper (ConstraintViolation -> 400)
- [ ] 2.19 Create PaymentResource with all endpoints (@Path("/payments"))

### Step 7: Validation
- [ ] 2.20 Verify POST /payments returns 201 with valid request
- [ ] 2.21 Verify POST /payments returns 400 with invalid request (missing required fields)
- [ ] 2.22 Verify POST /payments returns 400 with invalid currency (not in allowed list)
- [ ] 2.23 Verify POST /payments returns 400 with invalid amount (negative, > 999999.99)
- [ ] 2.24 Verify GET /payments/{id} returns 404 for non-existent UUID
- [ ] 2.25 Verify GET /payments/{id}/status returns 404 for non-existent
- [ ] 2.26 Verify GET /payments returns list with filters
- [ ] 2.27 Verify GET /health returns UP (liveness)
- [ ] 2.28 Verify GET /health returns UP (readiness)
- [ ] 2.29 Verify /q/openapi returns valid OpenAPI spec YAML
- [ ] 2.30 Verify /swagger-ui is accessible

### Step 8: Testing
- [ ] 2.31 Write unit tests for PaymentResource (happy path - create payment)
- [ ] 2.32 Write unit tests for PaymentResource (validation error - invalid amount)
- [ ] 2.33 Write unit tests for PaymentResource (404 - payment not found)
- [ ] 2.34 Run `mvn test` - all tests pass

## Documentation Tasks
- [ ] 2.35 Document API usage examples in README
- [ ] 2.36 Document error response formats in README