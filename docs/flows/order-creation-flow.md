# Order Creation Flow

## Implemented Synchronous Acceptance

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    participant G as API Gateway
    participant J as Auth JWKS
    participant O as Order Service
    participant P as Product Service
    participant D as order_db

    C->>G: POST /api/v1/orders + JWT + Idempotency-Key
    G->>J: Fetch/cache public key when needed
    G->>G: Verify token, role, correlation ID
    G->>O: Relay JWT, key, body, correlation ID
    O->>J: Fetch/cache public key when needed
    O->>O: Verify JWT; customerId = sub
    O->>D: Find customer + idempotency key
    alt matching request already exists
        D-->>O: Existing order + items
        O-->>G: 202 same order
    else key reused with different request
        D-->>O: Existing order with different hash
        O-->>G: 409 idempotency conflict
    else new request
        O->>P: GET product batch (bounded timeout)
        P-->>O: Trusted active product data
        O->>D: Local transaction: insert order + snapshots
        D-->>O: Committed PENDING order
        O-->>G: 202 + Location
    end
    G-->>C: Normalized response + correlation ID
```

The Product call is synchronous because Order cannot truthfully accept an order without an authoritative current price and sale status. It is an internal direct call; routing it through Gateway would add an unnecessary dependency and mix public-edge concerns into service communication.

## Transaction Boundary

```text
No DB transaction: validate identity -> idempotency pre-check -> Product HTTP call
Local DB transaction: re-check key -> insert customer_orders -> insert order_items -> commit
```

The second key check plus database uniqueness handles the race where two identical requests arrive together. PostgreSQL is the final arbiter; an in-memory lock would fail once multiple Order instances run.

## Failure Matrix

| Failure | Response/state | Why |
| --- | --- | --- |
| Invalid/forbidden JWT | `401`/`403`, no Product call | Reject at trust boundary |
| Same key, same request | `202`, original order | Safe client retry |
| Same key, different request | `409`, no Product call | Prevent key ambiguity |
| Missing/inactive product | `422`, no order | Business input cannot be accepted |
| Product timeout/unavailable | `503`, no order | No trusted snapshot exists |
| Concurrent duplicate insert | Read committed winner | DB unique constraint closes race |
| Another customer reads order | `404` | Enforce ownership without leaking existence |

## Future Asynchronous Continuation

The accepted `PENDING` order will later write an outbox command in the same local transaction. Kafka will carry inventory/payment saga work. That phase is intentionally absent today: returning `202` represents accepted workflow state, not a false claim that reservation/payment already completed.
