# Inventory Service

Inventory Service is authoritative for stock, availability, reservations, releases, and confirmed consumption. It stores Product Service UUIDs only as external references; there is deliberately no cross-database foreign key and no shared Product entity.

## API

Base path: `/api/v1/inventory`

| Method | Path | Purpose | Success |
| --- | --- | --- | --- |
| `PUT` | `/items/{productId}` | Create or replace total stock | `200 OK` |
| `GET` | `/items/{productId}` | Read total, reserved, and available stock | `200 OK` |
| `POST` | `/reservations` | Atomically reserve all requested lines | `201 Created` or `200 OK` for an identical retry |
| `GET` | `/reservations/{orderId}` | Read one order reservation | `200 OK` |
| `POST` | `/reservations/{orderId}/release` | Compensate a pending reservation | `200 OK` |
| `POST` | `/reservations/{orderId}/confirm` | Convert reserved units to consumed units | `200 OK` |

`release` and `confirm` are command endpoints because they represent explicit state transitions. Repeating the same terminal command is safe; trying to change from one terminal state to the other returns `409 INVALID_RESERVATION_STATE`.

## Quantity Model

```text
available_quantity = total_quantity - reserved_quantity
```

- Reserve: `reserved += quantity`; total does not change.
- Release: `reserved -= quantity`; total does not change.
- Confirm: `reserved -= quantity` and `total -= quantity`.

The database enforces `0 <= reserved <= total`. Application methods protect the same invariant and return domain-specific errors before a database constraint becomes the normal control flow.

## Concurrency and Transactions

One reservation can contain up to 100 unique products. `InventoryService.reserve` starts one local transaction and locks every matching `inventory_items` row using `PESSIMISTIC_WRITE`. Product IDs are sorted before the locking query so concurrent multi-product orders acquire locks consistently.

After all rows are locked, the service checks every line before changing any quantity. If one line is missing or insufficient, the transaction rolls back and no partial reservation remains. This prevents overselling and partial holds.

The `order_id` unique constraint is the idempotency key for the synchronous API. An identical retry returns the existing reservation. Reusing the same order ID with different lines returns `409 RESERVATION_CONFLICT`. Kafka delivery has an additional duplicate boundary, so the saga consumer records each input `eventId` in `processed_events`.

## Kafka Saga Participant

Inventory consumes these v1 commands from `inventory.commands.v1`:

- `InventoryReservationRequested`
- `InventoryReleaseRequested`
- `InventoryConfirmationRequested`

The Kafka key, envelope `aggregateId`, and payload `orderId` must match. The parser rejects unknown fields, unsupported versions, invalid quantities, duplicate products, and malformed correlation IDs instead of silently accepting contract drift.

For a successful reservation, stock changes, the inbox row, and an `InventoryReserved` outbox row commit in one `inventory_db` transaction. Known business failures roll back the reservation transaction first, then record the inbox row and an `InventoryReservationFailed` outbox row in a new transaction. Technical failures remain retryable and are never reported as business failures.

The scheduled outbox publisher locks due rows with `FOR UPDATE SKIP LOCKED`, waits for the Kafka broker acknowledgment, and then marks them published. A crash after broker acknowledgment but before the database commit can still duplicate a message; consumers must therefore remain idempotent. Invalid events are not retried, while transient failures use bounded exponential retry before publication to `inventory.commands.v1.DLT`.

## JPA Query Review

- `InventoryItemRepository.findAllByProductIdInForUpdate` locks rows in one query rather than one query per line.
- `InventoryReservationRepository.findByOrderId` uses an entity graph to fetch line items in the use cases that map them, avoiding N+1 queries with Open Session in View disabled.
- The reservation-to-items relationship is internal to Inventory Service, `LAZY`, cascade-owned, and orphan-removing. Product IDs are scalar UUID values, not JPA relationships.

## Configuration

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `INVENTORY_DB_PASSWORD` | Yes | None | Inventory database password |
| `INVENTORY_DB_URL` | No | `jdbc:postgresql://localhost:5432/inventory_db` | JDBC URL |
| `INVENTORY_DB_USERNAME` | No | `inventory_app` | Database owner/user |
| `INVENTORY_DB_POOL_SIZE` | No | `10` | Maximum Hikari connections |
| `INVENTORY_DB_MIN_IDLE` | No | `2` | Minimum idle Hikari connections |
| `KAFKA_BOOTSTRAP_SERVERS` | No | `localhost:9092` | Kafka broker addresses |
| `KAFKA_CONSUMER_MAX_RETRIES` | No | `3` | Retries after the initial delivery attempt |
| `KAFKA_CONSUMER_RETRY_INITIAL_INTERVAL` | No | `PT0.25S` | First transient-failure backoff |
| `KAFKA_CONSUMER_RETRY_MULTIPLIER` | No | `2.0` | Exponential retry multiplier |
| `KAFKA_CONSUMER_RETRY_MAX_INTERVAL` | No | `PT2S` | Maximum retry interval |
| `KAFKA_DLT_PUBLISH_TIMEOUT` | No | `PT5S` | Broker acknowledgement timeout for DLT publication |
| `INVENTORY_KAFKA_CONSUMER_GROUP` | No | `inventory-service-v1` | Stable Inventory consumer group |
| `INVENTORY_COMMANDS_TOPIC` | No | `inventory.commands.v1` | Saga command topic |
| `SAGA_MESSAGING_LISTENER_ENABLED` | No | `true` | Start the Kafka listener |
| `OUTBOX_PUBLISHER_ENABLED` | No | `true` | Start the scheduled publisher |
| `SERVER_PORT` | No | `8084` | HTTP port |

Swagger UI is available at `http://localhost:8084/swagger-ui.html` and OpenAPI at `http://localhost:8084/v3/api-docs`.

## Build and Test

From the repository root:

```powershell
cmd /c mvnw.cmd -pl services/inventory-service test
cmd /c mvnw.cmd -pl services/inventory-service package
docker build -f services/inventory-service/Dockerfile -t ecommerce/inventory-service:local .
```

The 29 tests cover domain invariants, application use cases, MVC validation/errors, the full HTTP lifecycle, Flyway/Hibernate schema compatibility, concurrent oversell prevention, strict event parsing, transactional inbox/outbox behavior, duplicate delivery, business failure rollback, and real Kafka/PostgreSQL success and DLT flows.
