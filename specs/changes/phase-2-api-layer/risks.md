# Risk Assessment: Phase 2 - API Layer

## Identified Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| OpenAPI spec validation fails | Low | Low | Use Spectral in Rake to validate |
| Quarkus version conflicts | Medium | Low | Use BOM for dependency management |
| In-memory storage loses data on restart | High | Low | Document limitation, defer DB to future |
| Validation not working properly | Medium | Low | Write comprehensive unit tests |

## Contingency Plans
- If validation issues: Add more explicit constraint annotations
- If storage limitation: Add simple file-based backup
- If spec changes needed: Use spec-delta.md to track changes