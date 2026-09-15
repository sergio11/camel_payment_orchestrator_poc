## ADDED Requirements

### Requirement: Amount validation
Payment amount SHALL be between 0.01 and 999999.99 with max 2 decimal places.

#### Scenario: Amount below minimum
- **WHEN** amount is less than 0.01
- **THEN** a validation error MUST be returned with message "Amount must be >= 0.01"

#### Scenario: Amount above maximum
- **WHEN** amount exceeds 999999.99
- **THEN** a validation error MUST be returned with message "Amount must be <= 999999.99"

#### Scenario: Amount is null
- **WHEN** amount is null
- **THEN** a validation error MUST be returned with message "Amount is required"

### Requirement: Currency validation
Payment currency SHALL be a supported ISO 4217 code via custom @SupportedCurrency validator.

#### Scenario: Supported currency accepted
- **WHEN** currency is one of USD, EUR, GBP, MXN, JPY
- **THEN** validation MUST pass

#### Scenario: Unsupported currency rejected
- **WHEN** currency is not in the supported list
- **THEN** a validation error MUST be returned with message "Unsupported currency"

#### Scenario: Currency null
- **WHEN** currency is null
- **THEN** a validation error MUST be returned with message "Currency is required"

### Requirement: Customer ID validation
Customer ID SHALL be required and maximum 50 characters.

#### Scenario: Missing customer ID
- **WHEN** customerId is blank
- **THEN** a validation error MUST be returned with message "Customer ID is required"

#### Scenario: Customer ID exceeds max length
- **WHEN** customerId exceeds 50 characters
- **THEN** a validation error MUST be returned with message "Customer ID max 50 characters"

### Requirement: Payment method validation
Payment method SHALL be required but accept any string value.

#### Scenario: Missing payment method
- **WHEN** paymentMethod is null
- **THEN** a validation error MUST be returned with message "Payment method is required"

### Requirement: Country code validation
Country MUST match ISO 3166-1 alpha-2 pattern (two uppercase letters) if provided.

#### Scenario: Invalid country code format
- **WHEN** country does not match pattern ^[A-Z]{2}$
- **THEN** a validation error MUST be returned with message "Country must be ISO 3166-1 alpha-2"

#### Scenario: Country is optional
- **WHEN** country is null
- **THEN** validation MUST pass (country is not required)
