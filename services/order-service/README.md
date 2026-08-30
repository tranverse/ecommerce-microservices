# Order Service

Order Service owns customer orders, immutable product snapshots, totals, idempotent order acceptance, and the order lifecycle state machine. It does not own catalog data, inventory quantities, payment attempts, credentials, or customer profiles.

Authenticated order creation, customer-owned reads, and the first durable saga command are implemented. New orders remain `PENDING`; Inventory processes the command, while Order outcome handling and Payment progression are the next workflow milestone.

## API

Base path: `/api/v1/orders`. Every endpoint requires a valid Auth Service JWT with `CUSTOMER` or `ADMIN`; the customer ID is always derived from JWT `sub`.

| Method | Path | Purpose | Success |
| --- | --- | --- | --- |
| `POST` | `/api/v1/orders` | Validate products and accept an idempotent order | `202 Accepted` |
| `GET` | `/api/v1/orders/{orderId}` | Read one order owned by the authenticated subject | `200 OK` |
| `GET` | `/api/v1/orders?page=0&size=20` | List owned order summaries | `200 OK` |

Creation requires an `Idempotency-Key` header containing 8–128 safe characters. A request can contain 1–50 unique product IDs, each with quantity 1–999.

```http
POST /api/v1/orders
Authorization: Bearer ACCESS_TOKEN
Idempotency-Key: checkout-7f9e7dc1
X-Correlation-ID: checkout-web-001
Content-Type: application/json

{
  "items": [
    {"productId": "b087fb9e-2c20-41f7-84bb-41cd9df81012", "quantity": 2}
  ]
}
```

The response stores Product Service's trusted SKU, name, unit price, and currency. Later catalog edits cannot rewrite the commercial facts recorded on an existing order.

## Creation Flow

1. Gateway validates a coarse role and relays the bearer token.
2. Order validates the JWT again and derives `customerId` from `sub`.
3. It canonicalizes the item list and hashes the request.
4. An existing matching `(customerId, Idempotency-Key)` is returned immediately; a different hash returns `409`.
5. Order makes one sorted batch call to Product Service with explicit connection/read timeouts and the correlation ID.
6. Missing, inactive, malformed, or mixed-currency products reject the request before persistence.
7. A short local transaction writes the order, item snapshots, and one `InventoryReservationRequested` outbox row to `order_db`.

The network call deliberately happens outside the database transaction. Holding a transaction and connection while waiting for another service would increase lock time and amplify downstream slowness.

See [Order Creation Flow](../../docs/flows/order-creation-flow.md) for the sequence and failure paths.

## Idempotency and Concurrency

The database unique constraint on `(customer_id, idempotency_key)` is the final race-condition guard. The application also stores a SHA-256 hash of the canonical request:

- same customer, key, and request: return the original order without calling Product again;
- same customer and key, different request: `409 IDEMPOTENCY_KEY_CONFLICT`;
- different customer and same key: independent operation.

An application pre-check improves the common path, but it cannot replace the unique constraint because concurrent requests can both pass a read before either commits.

## Transactional Outbox

The order and first inventory command share one local PostgreSQL transaction. A scheduled publisher selects due rows with `FOR UPDATE SKIP LOCKED`, publishes the persisted JSON envelope to `inventory.commands.v1`, and marks the row only after Kafka acknowledges it. Failed sends retain the row with bounded exponential backoff.

A broker acknowledgment followed by a process crash before the database update can still cause duplicate publication. This is intentional at-least-once behavior; Inventory's processed-event inbox makes the command idempotent. Kafka producer idempotence alone cannot atomically commit Kafka and `order_db`.

## Failure Behavior

- Product unavailable or timed out: `503 PRODUCT_CATALOG_UNAVAILABLE`; no order is persisted.
- Missing/inactive product or mixed currency: `422 INVALID_ORDER_ITEM`.
- Order owned by another subject: `404 ORDER_NOT_FOUND`, avoiding resource enumeration.
- Malformed/invalid input: stable sanitized `400` response.
- No automatic HTTP retry is used. The current read is technically safe to retry, but a retry/circuit-breaker policy will be introduced with measured resilience behavior rather than hidden defaults.

## Database

Flyway owns the `customer_orders` and `order_items` schema. Foreign keys exist only inside `order_db`; `product_id` and `customer_id` are opaque cross-service identifiers, not cross-database relationships. Hibernate uses `ddl-auto=validate` and Open Session in View is disabled.

Detail/idempotency queries use an entity graph to fetch items in one query. Paginated summaries do not access the item collection, avoiding an N+1 query path.

## Configuration

| Variable | Required | Default |
| --- | --- | --- |
| `ORDER_DB_PASSWORD` | Yes | None |
| `ORDER_DB_URL` | No | `jdbc:postgresql://localhost:5432/order_db` |
| `ORDER_DB_USERNAME` | No | `order_app` |
| `ORDER_DB_POOL_SIZE` | No | `10` |
| `ORDER_DB_MIN_IDLE` | No | `2` |
| `ORDER_AUTH_ISSUER` | No | `http://localhost:8081` |
| `ORDER_AUTH_JWKS_URI` | No | `http://localhost:8081/.well-known/jwks.json` |
| `ORDER_AUTH_CONNECT_TIMEOUT` | No | `PT2S` |
| `ORDER_AUTH_READ_TIMEOUT` | No | `PT2S` |
| `ORDER_PRODUCT_SERVICE_URL` | No | `http://localhost:8083` |
| `ORDER_PRODUCT_CONNECT_TIMEOUT` | No | `PT2S` |
| `ORDER_PRODUCT_READ_TIMEOUT` | No | `PT3S` |
| `KAFKA_BOOTSTRAP_SERVERS` | No | `localhost:9092` |
| `OUTBOX_PUBLISHER_ENABLED` | No | `true` |
| `SERVER_PORT` | No | `8085` |

Production disables Swagger/OpenAPI through the `prod` profile. No password or private key is stored in source control.

## Build and Test

From the repository root:

```powershell
.\mvnw.cmd -pl services/order-service test
docker build -f services/order-service/Dockerfile -t ecommerce/order-service:local .
```

The 25 tests cover aggregate transitions/invariants, repository constraints and fetch behavior on PostgreSQL 17.6, request canonicalization, Product contract/failure mapping, idempotency races, MVC security/validation, ownership, Flyway, the full HTTP/persistence flow, outbox retry behavior, and real Kafka publication.

The multi-stage image contains a Java 21 JRE runtime, runs as the non-root `spring` user, has a readiness health check, and uses container memory-aware JVM settings.
