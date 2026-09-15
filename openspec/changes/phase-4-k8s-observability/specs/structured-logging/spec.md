## ADDED Requirements

### Requirement: JSON structured logging
Applications SHALL output logs in JSON format to stdout.

#### Scenario: Logs are valid JSON
- **WHEN** the application writes a log entry
- **THEN** the output MUST be a single-line valid JSON object

#### Scenario: Log entry contains standard fields
- **WHEN** a JSON log entry is produced
- **THEN** it MUST contain timestamp, level, logger, and message fields

### Requirement: TraceId correlation in logs
Log entries SHALL include traceId and spanId for correlation with distributed traces.

#### Scenario: TraceId present in log entry
- **WHEN** a request is being processed within a trace context
- **THEN** the log entry MUST contain traceId matching the active trace

#### Scenario: SpanId present in log entry
- **WHEN** a log entry is produced within a span
- **THEN** the log entry MUST contain spanId matching the active span

### Requirement: Configurable log levels
Log levels SHALL be configurable per category via application.properties.

#### Scenario: Camel category level
- **WHEN** the application starts
- **THEN** org.apache.camel category MUST log at INFO level

#### Scenario: Application category level
- **WHEN** the application starts
- **THEN** com.poc category MUST log at DEBUG level
