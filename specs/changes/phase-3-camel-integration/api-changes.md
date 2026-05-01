# API Changes: Phase 3 - Camel Integration

## New Endpoints
None - existing endpoints now trigger Camel processing.

## Modified Endpoints
- POST /payments: Now triggers Camel route + publishes to Kafka

## Deprecated Endpoints
None.

## Schema Changes
None.

## Internal Changes
- Payment processing now asynchronous via Camel
- Status updates via event-driven architecture
- Provider field populated after processing