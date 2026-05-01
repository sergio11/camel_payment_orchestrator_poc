# API Changes: Phase 2 - API Layer

## New Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | /payments | Create payment |
| GET | /payments | List payments with filters |
| GET | /payments/{id} | Get payment by ID |
| GET | /payments/{id}/status | Get simplified status |
| GET | /health | Health check |

## Modified Endpoints
None.

## Deprecated Endpoints
None.

## Schema Changes

### PaymentRequest
- `amount` (required, decimal > 0)
- `currency` (required, enum: USD, EUR, GBP, MXN, JPY)
- `customerId` (required, string max 50)
- `paymentMethod` (required, enum: CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, WALLET, CRYPTO)
- `country` (optional, ISO 3166-1 alpha-2)
- `metadata` (optional, object)

### PaymentResponse
- `id` (uuid)
- `amount`, `currency`, `customerId`, `paymentMethod`
- `status` (enum: PENDING, PROCESSING, APPROVED, REJECTED, FAILED)
- `createdAt`, `updatedAt` (date-time)
- `provider`, `failureReason`, `metadata` (nullable)