## ADDED Requirements

### Requirement: Rake ADR validation task
A Rake task SHALL validate the structural integrity of all ADR files.

#### Scenario: All ADRs pass validation
- **WHEN** `rake adr:validate` is executed
- **THEN** all .md files in openspec/adrs/ MUST be checked for required sections (ADR-, Estado, Contexto, Decision, Consecuencias) and pass

### Requirement: Rake spec validation task
A Rake task SHALL validate OpenSpec changes and specs.

#### Scenario: Spec validation runs openspec validate
- **WHEN** `rake spec:validate` is executed
- **THEN** `npx openspec validate --all` MUST be run and all changes MUST pass validation

### Requirement: Rake test execution task
A Rake task SHALL run Maven tests with JaCoCo coverage reporting.

#### Scenario: Test task executes Maven verify
- **WHEN** `rake test:run` is executed
- **THEN** `mvn clean verify` MUST run with JaCoCo coverage gating at 98%

### Requirement: Rake infrastructure management tasks
Rake tasks SHALL manage local infrastructure via Podman Compose.

#### Scenario: Infrastructure start and stop
- **WHEN** `rake infra:start` is executed
- **THEN** Podman Compose MUST bring up all services
- **WHEN** `rake infra:stop` is executed
- **THEN** Podman Compose MUST tear down all services
