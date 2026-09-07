# Design: Phase 2 - API Layer

## Architecture

### Technology Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| Java | 17 LTS | Runtime |
| Maven | 3.9+ | Build tool |
| Quarkus | 3.17.x | Framework |
| quarkus-rest | 3.17.x | REST endpoints |
| quarkus-hibernate-validator | 3.17.x | Bean validation |
| quarkus-smallrye-openapi | 3.17.x | OpenAPI docs |
| quarkus-smallrye-health | 3.17.x | Health checks |
| quarkus-rest-jackson | 3.17.x | JSON serialization |

### REST Endpoints

| Method | Path | Description | Status Codes |
|--------|------|-------------|--------------|
| POST | /payments | Create payment | 201, 400, 500 |
| GET | /payments | List payments (filters: customerId, status, limit, offset) | 200 |
| GET | /payments/{id} | Get payment by ID | 200, 404 |
| GET | /payments/{id}/status | Get simplified status | 200, 404 |
| GET | /health | Health check | 200 |

### Project Structure

```
api-gateway/
├── pom.xml
└── src/main/
    ├── java/com/poc/gateway/
    │   ├── PaymentResource.java          # @Path("/payments"), @ApplicationScoped
    │   ├── PaymentService.java           # Business logic, @ApplicationScoped
    │   ├── PaymentRepository.java         # In-memory storage, @ApplicationScoped
    │   ├── entity/
    │   │   └── Payment.java               # Domain entity
    │   ├── dto/
    │   │   ├── PaymentRequest.java        # @Valid input DTO
    │   │   ├── PaymentResponse.java       # Output DTO
    │   │   ├── PaymentStatusResponse.java # Status only DTO
    │   │   └── ErrorResponse.java          # Error response DTO
    │   ├── mapper/
    │   │   └── PaymentMapper.java         # Entity <-> DTO conversion
    │   ├── validator/
    │   │   ├── SupportedCurrency.java     # @SupportedCurrency annotation
    │   │   └── SupportedCurrencyValidator  # Currency validator implementation
    │   └── exception/
    │       ├── GlobalExceptionMapper.java  # @ServerExceptionMapper
    │       ├── PaymentNotFoundException.java # 404 exception
    │       └── PaymentNotFoundExceptionMapper.java
    └── resources/
        └── application.properties
```

### application.properties

```properties
# HTTP Server
quarkus.http.port=8080

# OpenAPI / Swagger
quarkus.smallrye-openapi.path=/openapi
quarkus.swagger-ui.always-include=true
quarkus.swagger-ui.path=/swagger-ui

# Health Check
quarkus.smallrye-health.root-path=/health

# Application
quarkus.application.name=payment-gateway
quarkus.log.console.format=%d{yyyy-MM-dd HH:mm:ss} %-5p traceId=%X{traceId} %s%e%n

# CORS (development)
quarkus.http.cors=true
quarkus.http.cors.origins=*
```

### Package Details

#### Payment (Entity)
```java
@ApplicationScoped
public class Payment {
    private UUID id;                    // Auto-generated UUID
    private BigDecimal amount;          // 0.01 to 999999.99, 2 decimal places
    private String currency;            // USD, EUR, GBP, MXN, JPY
    private String customerId;          // Max 50 chars
    private String paymentMethod;       // CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, WALLET, CRYPTO
    private String country;             // ISO 3166-1 alpha-2 (optional)
    private PaymentStatus status;       // PENDING, PROCESSING, APPROVED, REJECTED, FAILED
    private String provider;            // Selected provider (nullable)
    private String failureReason;       // Failure reason (nullable)
    private Map<String, Object> metadata; // Additional data
    private LocalDateTime createdAt;    // Auto-set on create
    private LocalDateTime updatedAt;    // Auto-set on update
}

public enum PaymentStatus {
    PENDING, PROCESSING, APPROVED, REJECTED, FAILED
}
```

#### PaymentRequest (DTO - Input)
```java
public class PaymentRequest {
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be >= 0.01")
    @DecimalMax(value = "999999.99", message = "Amount must be <= 999999.99")
    @Digits(integer = 6, fraction = 2, message = "Amount max 2 decimal places")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    @SupportedCurrency
    private String currency;

    @NotBlank(message = "Customer ID is required")
    @Size(max = 50, message = "Customer ID max 50 characters")
    private String customerId;

    @NotNull(message = "Payment method is required")
    @Pattern(regexp = "CREDIT_CARD|DEBIT_CARD|BANK_TRANSFER|WALLET|CRYPTO")
    private String paymentMethod;

    @Size(max = 2, message = "Country code max 2 characters")
    private String country;

    private Map<String, Object> metadata;
}
```

#### PaymentResponse (DTO - Output)
```java
public class PaymentResponse {
    private String id;                  // UUID format
    private BigDecimal amount;
    private String currency;
    private String customerId;
    private String paymentMethod;
    private String country;
    private String status;              // PENDING, PROCESSING, APPROVED, REJECTED, FAILED
    private String provider;
    private String failureReason;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Jackson annotations for proper serialization
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private LocalDateTime createdAt;
}
```

#### ErrorResponse (DTO - Error Output)
```java
public class ErrorResponse {
    private String error;               // ERROR_CODE
    private String message;            // Human readable
    private List<ErrorDetail> details;  // Field-specific errors
    private LocalDateTime timestamp;   // ISO-8601

    public static ErrorResponse from(String error, String message) {
        return new ErrorResponse(error, message, null, LocalDateTime.now());
    }
}

public class ErrorDetail {
    private String field;
    private String message;
}
```

#### PaymentRepository (In-Memory Storage)
```java
@ApplicationScoped
public class PaymentRepository {
    private final ConcurrentHashMap<UUID, Payment> store = new ConcurrentHashMap<>();

    public Payment save(Payment payment) {
        payment.setId(UUID.randomUUID());
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());
        payment.setStatus(PaymentStatus.PENDING);
        store.put(payment.getId(), payment);
        return payment;
    }

    public Optional<Payment> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    public List<Payment> findAll(String customerId, PaymentStatus status, int limit, int offset) {
        return store.values().stream()
            .filter(p -> customerId == null || p.getCustomerId().equals(customerId))
            .filter(p -> status == null || p.getStatus() == status)
            .skip(offset)
            .limit(limit)
            .collect(Collectors.toList());
    }

    public long count(String customerId, PaymentStatus status) {
        return store.values().stream()
            .filter(p -> customerId == null || p.getCustomerId().equals(customerId))
            .filter(p -> status == null || p.getStatus() == status)
            .count();
    }

    public Payment update(Payment payment) {
        payment.setUpdatedAt(LocalDateTime.now());
        store.put(payment.getId(), payment);
        return payment;
    }
}
```

#### PaymentService (Business Logic)
```java
@ApplicationScoped
public class PaymentService {
    @Inject
    PaymentRepository repository;

    public PaymentResponse createPayment(PaymentRequest request) {
        Payment payment = PaymentMapper.toEntity(request);
        Payment saved = repository.save(payment);
        return PaymentMapper.toResponse(saved);
    }

    public PaymentResponse getPayment(String id) {
        UUID uuid = UUID.fromString(id);
        Payment payment = repository.findById(uuid)
            .orElseThrow(() -> new PaymentNotFoundException(id));
        return PaymentMapper.toResponse(payment);
    }

    public List<PaymentResponse> listPayments(String customerId, String status, int limit, int offset) {
        PaymentStatus paymentStatus = status != null ? PaymentStatus.valueOf(status) : null;
        return repository.findAll(customerId, paymentStatus, limit, offset)
            .stream()
            .map(PaymentMapper::toResponse)
            .collect(Collectors.toList());
    }
}
```

#### PaymentResource (REST Endpoint)
```java
@Path("/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class PaymentResource {

    @Inject
    PaymentService paymentService;

    @POST
    public Response createPayment(@Valid PaymentRequest request) {
        PaymentResponse response = paymentService.createPayment(request);
        return Response.status(Response.Status.CREATED)
            .entity(response)
            .build();
    }

    @GET
    public Response listPayments(
            @QueryParam("customerId") String customerId,
            @QueryParam("status") String status,
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset) {
        
        List<PaymentResponse> payments = paymentService.listPayments(customerId, status, limit, offset);
        return Response.ok(payments).build();
    }

    @GET
    @Path("/{id}")
    public Response getPayment(@PathParam("id") String id) {
        PaymentResponse response = paymentService.getPayment(id);
        return Response.ok(response).build();
    }

    @GET
    @Path("/{id}/status")
    public Response getPaymentStatus(@PathParam("id") String id) {
        PaymentResponse response = paymentService.getPayment(id);
        PaymentStatusResponse statusResponse = new PaymentStatusResponse(
            response.getId(),
            response.getStatus(),
            response.getUpdatedAt()
        );
        return Response.ok(statusResponse).build();
    }
}
```

#### Custom Validator: SupportedCurrency

```java
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
@Constraint(validatedBy = SupportedCurrencyValidator.class)
@Documented
public @interface SupportedCurrency {
    String message() default "Unsupported currency";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class SupportedCurrencyValidator implements ConstraintValidator<SupportedCurrency, String> {
    // Plain POJO, no CDI scope: Quarkus instantiates ConstraintValidators via ArcConstraintValidatorFactory.
    private static final Set<String> ALLOWED = Set.of("USD", "EUR", "GBP", "MXN", "JPY");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || ALLOWED.contains(value);
    }
}
```

#### Exception Handlers

```java
@ApplicationScoped
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {
    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof ConstraintViolationException) {
            return handleValidationException((ConstraintViolationException) exception);
        }
        if (exception instanceof PaymentNotFoundException) {
            return handleNotFound((PaymentNotFoundException) exception);
        }
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(ErrorResponse.from("INTERNAL_ERROR", "An unexpected error occurred"))
            .build();
    }

    private Response handleValidationException(ConstraintViolationException e) {
        List<ErrorDetail> details = e.getConstraintViolations().stream()
            .map(v -> new ErrorDetail(v.getPropertyPath().toString(), v.getMessage()))
            .collect(Collectors.toList());
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ErrorResponse.from("VALIDATION_ERROR", "Invalid request", details))
            .build();
    }

    private Response handleNotFound(PaymentNotFoundException e) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity(ErrorResponse.from("NOT_FOUND", "Payment not found: " + e.getPaymentId()))
            .build();
    }
}

public class PaymentNotFoundException extends RuntimeException {
    private final String paymentId;
    public PaymentNotFoundException(String paymentId) {
        super("Payment not found: " + paymentId);
        this.paymentId = paymentId;
    }
}
```

### Health Check Configuration

Quarkus SmallRye Health automatically provides:
- `/health/live` - Liveness probe
- `/health/ready` - Readiness probe
- `/health` - Combined health

No additional code required - enabled via dependency.

### OpenAPI Documentation

Enabled via `quarkus-smallrye-openapi` dependency:
- `/openapi` - OpenAPI 3.0 YAML
- `/swagger-ui` - Swagger UI (when `quarkus.swagger-ui.always-include=true`)

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