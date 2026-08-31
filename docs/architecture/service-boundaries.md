# Service Boundaries

Status: **Accepted.**

Service boundaries are based on business capabilities, data ownership, consistency rules, and failure/scaling characteristics. They are not table-per-service boundaries.

## Ownership matrix

| Service | Owns | Does not own | Primary consumers |
| --- | --- | --- | --- |
| API Gateway | Edge routing, correlation-ID entry, coarse token validation | Business data or workflows | External clients |
| Auth | Credentials, password hashes, token lifecycle, identity subject and roles | Customer profile and addresses | Gateway and authenticated services |
| User | Profile, customer information, addresses | Passwords and token signing | Customer-facing APIs |
| Product | Catalog, description, sale status, current price, search metadata | Stock quantity and reservations | Clients and Order Service |
| Inventory | Stock, availability, reservations, releases, concurrency rules | Product description or order lifecycle | Admin APIs and order saga |
| Order | Orders, immutable item snapshots, order state, saga progress | Product catalog, stock, payment records | Customers and administrators |
| Payment | Simulated payment attempts, results, idempotency and refunds | Order state and inventory | Order saga |
| Notification | Notification requests, delivery attempts and status | Order or payment business decisions | Asynchronous events |

## Boundary rationale

### Auth and User

Authentication data has stricter security and lifecycle requirements than customer profile data. Auth issues stable subject identifiers; User uses that subject identifier but never receives passwords. This separation costs an additional integration boundary, so registration initially creates credentials and profile completion remains a separate authenticated use case. We will add an event only when another service genuinely needs to react to registration.

### Product and Inventory

Catalog reads and stock reservations have different workloads and invariants. Product owns the current selling price. Inventory owns the promise that a unit can be reserved without overselling. An order stores a price snapshot because historical agreements must not change when Product changes later.

### Order, Inventory, and Payment

Each service owns one local transaction boundary. Order coordinates business progress but cannot mutate Inventory or Payment tables. Inventory and Payment return outcomes through versioned events; Order applies valid state transitions and requests compensation when needed.

Payment's local charge/refund workflow and Kafka saga participant are implemented. It stores `orderId` only as an opaque identifier, owns provider references, consumes `PaymentRequested`, emits terminal outcomes, and has no client-facing route. Order consumes those outcomes and emits Inventory commands; it never changes a payment or reservation row directly.

### Notification

Notification delivery is slow and failure-prone relative to order processing. It consumes events asynchronously so email simulation or a future provider outage cannot roll back a confirmed order.

## Dependency rules

- External traffic enters through Gateway.
- Internal service calls bypass Gateway.
- Service code may depend on its own DTOs and persistence model only.
- IDs may cross boundaries; entity objects may not.
- A service cannot import another service's domain package.
- Technical duplication is preferred over a shared business-model library when sharing would couple releases.
- Circular synchronous dependencies are prohibited.

## Boundary review questions

Before adding any dependency between services, record:

1. Which service owns the requested data?
2. Does the caller require an immediate response?
3. Is eventual consistency acceptable?
4. What happens when the target is unavailable or slow?
5. Is the operation idempotent and safe to retry?
6. Does the dependency create a synchronous chain or cycle?
