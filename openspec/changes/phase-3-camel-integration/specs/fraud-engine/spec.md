## ADDED Requirements

### Requirement: Fraud scoring with configurable rules
Payments SHALL be scored based on multiple fraud rules with configurable thresholds.

#### Scenario: High amount rule triggers score
- **WHEN** payment amount exceeds the high-amount-threshold (15000)
- **THEN** risk score MUST increase by 50 points

#### Scenario: High risk country rule triggers score
- **WHEN** payment country is in the high-risk countries list
- **THEN** risk score MUST increase by 30 points

#### Scenario: Rapid retry rule triggers score
- **WHEN** payment attempt count exceeds the rapid-retry-threshold (3)
- **THEN** risk score MUST increase by 25 points

#### Scenario: New payment method rule triggers score
- **WHEN** payment method is new and age in days is less than the new-method-days-threshold (30)
- **THEN** risk score MUST increase by 20 points

#### Scenario: Unusual hour rule triggers score
- **WHEN** payment timestamp is between 2am and 5am in the customer timezone
- **THEN** risk score MUST increase by 15 points

### Requirement: Risk score capping
The total risk score SHALL NOT exceed the maximum configured value (100).

#### Scenario: Score does not exceed maximum
- **WHEN** the sum of all rule scores exceeds 100
- **THEN** the final risk score MUST be capped at 100

### Requirement: Risk score determines fraud action
The risk score SHALL map to APPROVE, REVIEW, or REJECT actions.

#### Scenario: High risk score rejects payment
- **WHEN** risk score >= 80 (risk-score-threshold-high)
- **THEN** fraud action MUST be REJECT

#### Scenario: Medium risk score reviews payment
- **WHEN** risk score >= 50 (risk-score-threshold-medium) and < 80
- **THEN** fraud action MUST be REVIEW

#### Scenario: Low risk score approves payment
- **WHEN** risk score < 50
- **THEN** fraud action MUST be APPROVE

### Requirement: FraudResult published to fraud.events.detected
Fraud evaluation results SHALL be published as FraudResult to the fraud.events.detected topic.

#### Scenario: Approve result published
- **WHEN** fraud action is APPROVE
- **THEN** a FraudResult.Approve MUST be published to fraud.events.detected

#### Scenario: Review result published
- **WHEN** fraud action is REVIEW
- **THEN** a FraudResult.Review (with reason) MUST be published to fraud.events.detected

#### Scenario: Reject result published
- **WHEN** fraud action is REJECT
- **THEN** a FraudResult.Reject (with reason) MUST be published to fraud.events.detected

### Requirement: Routing after fraud evaluation
Payments SHALL be routed based on fraud action to provider selection, review queue, or rejection.

#### Scenario: Approved payment goes to provider selection
- **WHEN** fraud action is APPROVE
- **THEN** payment MUST be routed to direct:provider-selection

#### Scenario: Rejected payment publishes failed event
- **WHEN** fraud action is REJECT
- **THEN** FraudResult MUST be published to fraud.events.detected and PaymentMessage to payments.events.failed

#### Scenario: Review payment publishes review event
- **WHEN** fraud action is REVIEW
- **THEN** FraudResult MUST be published to fraud.events.detected and PaymentMessage to payments.events.review
