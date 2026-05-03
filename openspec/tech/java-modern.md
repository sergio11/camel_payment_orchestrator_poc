# Java Modern Standards

## Runtime Requirements

| Component | Version | Notes |
|-----------|---------|-------|
| Java | 17 LTS | Minimum |
| Target | 17 | Source compatibility |
| JVM | 17+ | Enable modern features |

## Mandatory Modern Features

### 1. Records (Java 16+)

**Use**: DTOs, immutable entities, data carriers

**Why**: Reduces boilerplate ~40%, immutable by design

```java
// Instead of verbose class with getters/setters
public class PaymentResponse {
    private UUID id;
    private BigDecimal amount;
    // ... 50+ lines of boilerplate
}

// Use Records
public record PaymentResponse(
    UUID id,
    BigDecimal amount,
    String currency,
    String status,
    LocalDateTime createdAt
) {}
```

### 2. Sealed Classes (Java 17+)

**Use**: Controlled hierarchies (errors, status, validation)

**Why**: Type-safe, compiler-enforced exhaustive handling

```java
// Traditional: No compile-time guarantee
public interface PaymentResult {}

// Sealed: Compiler enforces all implementations
public sealed interface PaymentResult 
    permits PaymentApproved, PaymentRejected, PaymentFailed {
    UUID paymentId();
}

public final class PaymentApproved implements PaymentResult {
    private final UUID paymentId;
    private final String authorizationCode;
    // Only these 3 classes allowed
}

public final class PaymentRejected implements PaymentResult {}

public final class PaymentFailed implements PaymentResult {}
```

### 3. Pattern Matching (Java 16+)

**Use**: instanceof + cast simplification

```java
// Old style
if (obj instanceof String) {
    String s = (String) obj;
    System.out.println(s.length());
}

// New style: instanceof pattern variable
if (obj instanceof String s) {
    System.out.println(s.length());  // s is already typed
}

// With records
if (result instanceof PaymentApproved approved) {
    System.out.println(approved.authorizationCode());
}
```

### 4. Switch Expressions (Java 14+)

**Use**: Modern control flow, exhaustive matching

```java
// Old style
if (status.equals("PENDING")) {
    return "Awaiting";
} else if (status.equals("APPROVED")) {
    return "Success";
} else {
    return "Unknown";
}

// New style
return switch (status) {
    case PENDING -> "Awaiting processing";
    case APPROVED -> "Payment successful";
    case REJECTED -> "Rejected by provider";
    case FAILED -> "Processing failed";
};

// Exhaustiveness enforced by compiler for sealed types
```

### 5. Text Blocks (Java 15+)

**Use**: Multi-line strings (JSON, SQL, logs)

```java
// Old style
String json = "{\n" +
    "  \"id\": \"" + id + "\",\n" +
    "  \"amount\": " + amount + "\n" +
    "}";

// New style
String json = """
    {
        "id": "%s",
        "amount": %s,
        "currency": "%s"
    }
    """.formatted(id, amount, currency);
```

### 6. Optional API Improvements (Java 9+)

**Use**: Streamlined null handling

```java
// Old style
if (payment != null && payment.getCustomer() != null) {
    return payment.getCustomer().getName();
}

// New style: orElseThrow, isPresent shorthand
return Optional.ofNullable(payment)
    .map(Payment::getCustomer)
    .flatMap(Customer::getName)
    .orElseThrow();
```

## Migration Path

| Phase | Action | Files |
|-------|-------|-------|
| Phase 2 | Convert DTOs to Records | PaymentRequest, PaymentResponse, PaymentStatusResponse, ErrorResponse |
| Phase 2 | Sealed classes for PaymentResult | PaymentResult hierarchy |
| Phase 2 | Pattern matching in mappers | PaymentMapper, exception mappers |
| Phase 3 | Switch expressions in routes | Camel route logic |
| All phases | Replace if-else with switch | General refactoring |

## Code Style Examples

### Entity → Record Transformation

```java
// BEFORE: Traditional entity (119 lines)
public class Payment {
    private UUID id;
    private BigDecimal amount;
    // getters, setters, toString, equals, hashCode...
}

// AFTER: Record (30 lines)
public record Payment(
    UUID id,
    BigDecimal amount,
    String currency,
    String customerId,
    String paymentMethod,
    PaymentStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

### DTO with Validation → Record + Validation Annotations

```java
// BEFORE: Class with validation
public class PaymentRequest {
    @NotNull
    private BigDecimal amount;
    // getters, setters...
}

// AFTER: Record with validation annotations on components
public record PaymentRequest(
    @NotNull @DecimalMin("0.01") @DecimalMax("999999.99")
    BigDecimal amount,
    
    @NotNull @SupportedCurrency
    String currency,
    
    @NotBlank @Size(max = 50)
    String customerId,
    
    @NotNull @Pattern(regexp = "CREDIT_CARD|DEBIT_CARD|...")
    String paymentMethod,
    
    @Size(max = 2)
    String country,
    
    Map<String, Object> metadata
) {}
```

### Error Handling → Sealed Interface

```java
// BEFORE: Open interface
public interface PaymentResult {}

// AFTER: Sealed interface
public sealed interface PaymentResult 
    permits PaymentApproved, PaymentRejected, PaymentFailed {
    UUID paymentId();
}

// Exhaustive switch - compiler catches missing cases
String toMessage(PaymentResult result) {
    return switch (result) {
        case PaymentApproved approved -> "Approved: " + approved.authCode();
        case PaymentRejected rejected -> "Rejected: " + rejected.reason();
        case PaymentFailed failed -> "Failed: " + failed.error();
    };
}
```

## Benefits Summary

| Benefit | Description |
|---------|-------------|
| **Maintainability** | Less boilerplate = fewer places for bugs |
| **Type Safety** | Sealed classes enforce exhaustive handling at compile time |
| **Immutability** | Records are immutable by design |
| **Performance** | No runtime overhead vs manual code |
| **Modernity** | Attracts Java developers, easier hiring |

## References

- [JEP 395](https://openjdk.org/jeps/395): Records
- [JEP 409](https://openjdk.org/jeps/409): Sealed Classes
- [JEP 394](https://openjdk.org/jeps/394): Pattern Matching for instanceof
- [JEP 361](https://openjdk.org/jeps/361): Switch Expressions
- [JEP 368](https://openjdk.org/jeps/368): Text Blocks (Preview)
- [JEP 323](https://openjdk.org/jeps/323): Local-Variable Syntax for Lambda Parameters (var)