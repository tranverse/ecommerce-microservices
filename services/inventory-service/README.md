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

The `order_id` unique constraint is the idempotency key for this synchronous API. An identical retry returns the existing reservation. Reusing the same order ID with different lines returns `409 RESERVATION_CONFLICT`. Kafka consumer idempotency will later add processed-event records because broker delivery introduces a different duplicate boundary.

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
| `SERVER_PORT` | No | `8084` | HTTP port |

Swagger UI is available at `http://localhost:8084/swagger-ui.html` and OpenAPI at `http://localhost:8084/v3/api-docs`.

## Build and Test

From the repository root:

```powershell
cmd /c mvnw.cmd -pl services/inventory-service test
cmd /c mvnw.cmd -pl services/inventory-service package
docker build -f services/inventory-service/Dockerfile -t ecommerce/inventory-service:local .
```

The 17 tests cover domain invariants, application use cases, MVC validation/errors, the full HTTP lifecycle on PostgreSQL 17.6, Flyway/Hibernate schema compatibility, idempotent retry, and concurrent oversell prevention.
