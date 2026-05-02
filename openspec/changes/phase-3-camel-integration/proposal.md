# Proposal: Phase 3 - Camel Integration

## Overview
Implement Apache Camel routes for payment processing, fraud checking, and provider routing with full EIP pattern support.

## Problem Statement
The system needs to process payments beyond simple CRUD:
- Route payments to fraud engine
- Select appropriate payment provider (A or B)
- Handle retries and circuit breaking
- Publish events to Kafka
- Implement error handling with dead letter channel

## Goals
- Implement Content-Based Router for payment routing
- Implement Circuit Breaker with Resilience4j
- Implement Retry with exponential backoff
- Implement Dead Letter Channel for failed payments
- Implement Wire Tap for audit logging
- Implement fraud detection rules engine
- Integrate with Kafka for event publishing

## Non-Goals
- Real provider integration (simulated only)
- Complex fraud rules (simple threshold-based)
- Database persistence (future phase)

## Success Metrics
- Payments flow through Camel routes
- Provider fallback works when primary fails
- Circuit breaker opens after threshold failures
- Events published to Kafka topics
- Failed payments go to dead letter queue

## Timeline
- Start: Week 3
- Target: Week 4 completion

## Dependencies
- Phase 2 (API Layer) must be complete
- Kafka running locally (from Phase 1)