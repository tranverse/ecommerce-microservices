# ADR 007: Validate Product Snapshots Synchronously and Accept Orders Idempotently

## Status

Accepted and implemented for initial order acceptance.

## Context

An order needs authoritative product identity, sale status, price, and currency at acceptance time. Product Service owns current catalog data; Order Service owns the historical customer agreement. Clients may retry creation after timeouts, and multiple Order instances must not create duplicates.

## Options Considered

1. Trust product names/prices supplied by the client. This is insecure and allows stale or manipulated commercial data.
2. Query Product Service once per order item. This creates an N+1 network pattern and poor partial-failure behavior.
3. Read Product tables directly or share JPA entities. This violates database ownership and couples schemas/releases.
4. Consume catalog events and accept from an Order-local read model. This improves runtime availability but introduces eventual-staleness/versioning complexity before the project has that need.
5. Perform one synchronous Product batch lookup, snapshot trusted fields, and enforce customer-scoped idempotency in `order_db`.

## Decision

Choose option 5 for the initial workflow. Order canonicalizes requested product IDs/quantities, checks a customer-scoped idempotency record, calls Product's bounded batch GET outside a database transaction, validates active products and a single ISO currency, then commits an order plus immutable item snapshots in one local transaction.

The database enforces uniqueness on `(customer_id, idempotency_key)`. A SHA-256 request hash distinguishes a valid replay from accidental key reuse with different input.

## Reasoning

The order cannot be accepted without a trusted price, so synchronous communication is justified. One batch call bounds network fan-out. Snapshots preserve historical truth if the catalog changes. Database uniqueness remains correct under concurrent requests and horizontal scaling, unlike an in-memory lock or pre-check alone.

## Trade-offs

- New-order availability depends on Product availability.
- Snapshot fields duplicate Product data intentionally.
- Product API contract changes require compatible evolution.
- A concurrent race may perform Product lookup more than once, although only one order commits.
- `202 Accepted` communicates that the later inventory/payment workflow is incomplete.

## Consequences

- Product failure returns `503` and persists no new order.
- Existing idempotent replays do not depend on Product availability.
- Order reads never need Product to reconstruct historical lines.
- Internal REST calls bypass Gateway and propagate correlation IDs.
- Automatic retry is not enabled until resilience policy and metrics are implemented.
- The future transactional outbox must be inserted in the same local transaction as the initial order state.
