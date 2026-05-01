# Tasks: Phase 2 - API Layer

## Implementation Tasks

- [ ] Create Quarkus Maven project structure
- [ ] Add Quarkus REST, Bean Validation, OpenAPI dependencies
- [ ] Create Payment entity/model class
- [ ] Create PaymentRequest DTO with validation annotations
- [ ] Create PaymentResponse DTO
- [ ] Create PaymentResource REST endpoint
- [ ] Create PaymentService business logic
- [ ] Create PaymentRepository in-memory storage
- [ ] Implement custom validation for amount > 0
- [ ] Implement custom validation for supported currencies
- [ ] Create global exception handler
- [ ] Configure OpenAPI in application.properties
- [ ] Add health check endpoint
- [ ] Write unit tests for PaymentResource

## Validation Tasks

- [ ] Verify POST /payments returns 201 with valid request
- [ ] Verify POST /payments returns 400 with invalid request
- [ ] Verify GET /payments/{id} returns 404 for non-existent
- [ ] Verify GET /health returns UP
- [ ] Verify /q/openapi returns valid OpenAPI spec
- [ ] Run `mvn test` - all tests pass

## Documentation Tasks

- [ ] Document API endpoints in OpenAPI spec
- [ ] Add usage examples to README
- [ ] Document error response formats