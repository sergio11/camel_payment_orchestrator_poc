## ADDED Requirements

### Requirement: Structured error responses
All errors SHALL return ErrorResponseDTO with error code, message, optional details array, and timestamp.

#### Scenario: Validation error response
- **WHEN** a ConstraintViolationException occurs (bean validation failure)
- **THEN** a 400 response with error=VALIDATION_ERROR, field-level details, and timestamp MUST be returned

#### Scenario: Not found error response
- **WHEN** a PaymentNotFoundException is thrown
- **THEN** a 404 response with error=NOT_FOUND and message "Payment not found: {id}" MUST be returned

#### Scenario: Internal error response
- **WHEN** an unexpected exception occurs
- **THEN** a 500 response with error=INTERNAL_ERROR and generic message MUST be returned

### Requirement: Marker-based exception classification
Exceptions SHALL be classified by class name markers and message content via MarkerBasedClassifier.

#### Scenario: JSON parse error classified as BAD_REQUEST
- **WHEN** a JsonParseException is thrown
- **THEN** it MUST be classified as BAD_REQUEST and return 400

#### Scenario: Illegal argument classified as BAD_REQUEST
- **WHEN** an IllegalArgumentException is thrown
- **THEN** it MUST be classified as BAD_REQUEST and return 400

#### Scenario: Constraint violation classified as BAD_REQUEST
- **WHEN** a ConstraintViolationException is thrown
- **THEN** it MUST be classified as BAD_REQUEST and return 400 with validation details

#### Scenario: Payment not found classified as NOT_FOUND
- **WHEN** a PaymentNotFoundException is thrown
- **THEN** it MUST be classified as NOT_FOUND and return 404

### Requirement: Error detail structure
ErrorDetailDTO SHALL contain field and message for field-level error reporting.

#### Scenario: Field-level error detail
- **WHEN** a validation error occurs on a specific field
- **THEN** an ErrorDetailDTO with the field path and validation message MUST be included in details array
