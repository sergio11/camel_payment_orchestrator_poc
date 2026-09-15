## ADDED Requirements

### Requirement: Maven multi-module project structure
The project SHALL use a parent POM with three modules: shared, backend, and payment-processor.

#### Scenario: Project builds successfully
- **WHEN** `mvn clean compile` is executed at the project root
- **THEN** all modules compile without errors

#### Scenario: Shared module is reusable
- **WHEN** backend or payment-processor declare a dependency on shared
- **THEN** shared DTOs, events, validators, and utilities MUST be available

### Requirement: Quarkus BOM dependency management
The parent POM SHALL declare io.quarkus.platform:quarkus-bom for centralized version management.

#### Scenario: Dependency versions resolve from BOM
- **WHEN** Maven resolves dependencies
- **THEN** Quarkus extension versions MUST be managed by the BOM, not hardcoded in module POMs

### Requirement: Java 17 LTS runtime
The project SHALL target Java 17 LTS as the minimum runtime version.

#### Scenario: Modern Java features available
- **WHEN** code is compiled with Java 17
- **THEN** records, sealed interfaces, pattern matching, and text blocks MUST be available
