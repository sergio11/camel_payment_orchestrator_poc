# Risk Assessment: Phase 2 - API Layer

## Identified Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Maven build fails due to dependency conflicts | Medium | Low | Use `io.quarkus.platform:quarkus-bom:3.17.0` |
| Jackson serialization fails for BigDecimal | Medium | Low | Configure `@JsonSerialize(using = ToStringSerializer.class)` |
| UUID validation fails on path parameter | Low | Medium | Add `@Pattern` validation for UUID format in resource |
| ConcurrentHashMap thread-safety issues | Medium | Low | Use `computeIfAbsent` for thread-safe operations |
| Custom validator not registered | Medium | Low | Ensure validator class has `@ApplicationScoped` annotation |
| OpenAPI spec mismatch with implementation | Low | Medium | Run `rake spec:validate` before implementation |
| In-memory storage loses data on restart | High | Low | Document limitation, defer DB to future phase |

## Contingency Plans

| Scenario | Action |
|----------|--------|
| Validation issues | Add explicit `@Constraint(validatedBy = ...)` with full class name |
| Storage limitation | Add simple file-based JSON backup (future enhancement) |
| Spec changes needed | Update spec-delta.md in change directory |
| Build issues | Run `mvn dependency:tree` to check version conflicts |

## Testing Requirements

- Minimum 5 unit tests covering:
  1. Happy path - create payment returns 201
  2. Validation error - missing required field returns 400
  3. Validation error - invalid currency returns 400
  4. Not found - non-existent UUID returns 404
  5. List - filter by customer returns correct results