# Design: Phase 2 - API Layer

## Architecture

### REST Endpoints
```
POST   /payments         - Create payment
GET    /payments         - List payments (with filters)
GET    /payments/{id}   - Get payment by ID
GET    /payments/{id}/status - Get simplified status
GET    /health           - Health check
```

### Request/Response Models

#### PaymentRequest
```json
{
  "amount": 99.99,
  "currency": "USD",
  "customerId": "cust-123",
  "paymentMethod": "CREDIT_CARD",
  "country": "US",
  "metadata": {}
}
```

#### PaymentResponse
```json
{
  "id": "uuid",
  "amount": 99.99,
  "currency": "USD",
  "customerId": "cust-123",
  "paymentMethod": "CREDIT_CARD",
  "status": "PENDING",
  "createdAt": "timestamp",
  "updatedAt": "timestamp"
}
```

### Technology
- **REST Framework**: Quarkus REST (formerly RESTEasy Reactive)
- **Validation**: Hibernate Validator + Custom validators
- **OpenAPI**: SmallRye OpenAPI extension
- **Storage**: In-memory Map (for POC)

### Error Handling Strategy
| Status | Condition |
|--------|-----------|
| 201 | Payment created successfully |
| 400 | Validation error (invalid request body) |
| 404 | Payment not found |
| 422 | Fraud rejected (future) |
| 500 | Internal server error |

## Components

1. **PaymentResource**: REST endpoint for payment operations
2. **PaymentService**: Business logic for payment CRUD
3. **PaymentRepository**: In-memory storage
4. **PaymentMapper**: DTO/Entity conversion
5. **ValidationExceptionMapper**: Global exception handling
6. **ErrorResponse**: Standard error response model

## Data Flow
1. Client POSTs payment to /payments
2. PaymentResource receives request, validates
3. PaymentService creates payment with PENDING status
4. Payment persisted to in-memory store
5. PaymentResponse returned with 201

## Security Considerations
- No authentication (dev mode)
- Input sanitization via validation
- No sensitive data logging