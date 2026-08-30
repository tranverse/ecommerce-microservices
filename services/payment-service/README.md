# Payment Service

Payment Service owns simulated charge/refund attempts, provider references, payment state, and payment idempotency. It does not own the order lifecycle, inventory, customer credentials, or catalog prices.

The current milestone implements the domain, PostgreSQL persistence, processor port, deterministic local simulator, and retry-safe application workflow. The Kafka command/event adapter is intentionally deferred to the saga milestone. There is no public business controller and Payment is not routed through API Gateway.

## Why This Is a Separate Service

Payment has different invariants and operational risks from Order. A provider can accept a charge and then time out before the caller receives the result. Charges and refunds therefore require durable state, stable idempotency keys, provider-specific integration, and an audit trail. Keeping these rules in Payment prevents Order from acquiring provider logic and sensitive operational responsibilities.

In a monolith the same classes could run in one process and one database transaction could update order and payment rows. The external provider call would still not join that transaction. In microservices, Order and Payment additionally have separate databases, so Kafka and a saga will coordinate their outcomes without a distributed database transaction.

## State Machine

```text
PENDING --approved--> COMPLETED --refund approved--> REFUNDED
   |
   +------declined--> FAILED
```

- `PENDING` means the charge has no durable terminal business outcome yet.
- `COMPLETED` records the stable provider charge reference.
- `FAILED` currently represents a business decline, not a temporary infrastructure failure.
- `REFUNDED` preserves both the original charge reference and the refund reference.

Technical provider failures leave the payment retryable: a failed charge call keeps `PENDING`, while a failed refund call keeps `COMPLETED`.

## Processing Boundary

```text
short DB transaction: create/find PENDING payment
        -> provider call outside DB transaction
short DB transaction: lock row and apply outcome
```

The processor receives `paymentId` as its idempotency key. If the process crashes after the provider approves but before Payment DB records the outcome, redelivery uses the same key. A compliant provider returns the same outcome/reference instead of charging twice.

One `orderId` can own only one payment. A replay with the same amount/currency returns the existing outcome; reuse with different commercial data is rejected as a conflict. Database uniqueness is the final concurrency guard.

See [Payment Processing Flow](../../docs/flows/payment-processing-flow.md) and [ADR 008](../../docs/decisions/008-payment-processor-idempotency.md).

## Processor Port

`PaymentProcessor` is an outbound port. `SimulatedPaymentProcessor` is the current adapter and generates stable references from `paymentId`:

- `sim-charge-{paymentId}`
- `sim-refund-{paymentId}`

Environment switches can simulate a business decline or infrastructure outage. A real provider adapter can replace the simulator without changing the aggregate or application service, but it must honor the same idempotency contract.

## Database

Flyway owns `payment_db`. The `payments` table enforces:

- one payment per opaque cross-service `order_id`;
- positive `NUMERIC(19,2)` amounts and ISO currency format;
- unique provider/refund references;
- allowed states and state-specific nullable data;
- optimistic versioning plus pessimistic locking while an outcome is applied.

There is deliberately no foreign key to `order_db`. Hibernate uses `ddl-auto=validate` and Open Session in View is disabled.

## Configuration

| Variable | Required | Default |
| --- | --- | --- |
| `PAYMENT_DB_PASSWORD` | Yes | None |
| `PAYMENT_DB_URL` | No | `jdbc:postgresql://localhost:5432/payment_db` |
| `PAYMENT_DB_USERNAME` | No | `payment_app` |
| `PAYMENT_DB_POOL_SIZE` | No | `10` |
| `PAYMENT_DB_MIN_IDLE` | No | `2` |
| `PAYMENT_SIMULATOR_DECLINE_PAYMENTS` | No | `false` |
| `PAYMENT_SIMULATOR_UNAVAILABLE` | No | `false` |
| `SERVER_PORT` | No | `8086` |

Never enable both simulator failure switches as application policy in production. They exist for local failure exercises until a real provider sandbox is introduced.

## Build and Test

From the repository root:

```powershell
.\mvnw.cmd -pl services/payment-service test
docker build -f services/payment-service/Dockerfile -t ecommerce/payment-service:local .
```

The 19 tests cover aggregate transitions, monetary validation, Flyway/database constraints, idempotent approval/decline/refund behavior, processor outage recovery, provider-contract checks, and the deterministic simulator on PostgreSQL 17.6.

The multi-stage image contains a Java 21 JRE runtime, runs as the non-root `spring` user, exposes only actuator endpoints in this milestone, and checks readiness on port `8086`.
