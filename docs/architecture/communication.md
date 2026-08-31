# Service Communication

Status: **Accepted; synchronous Product lookup, the terminal Order saga, and asynchronous Notification delivery are implemented.**

## Decision policy

Use synchronous HTTP when the caller cannot continue without an immediate, authoritative answer. Use Kafka when work may continue independently, multiple consumers react, temporary delay is acceptable, or a distributed workflow needs durable progress.

| Interaction | Style | Reason | Unavailability behavior |
| --- | --- | --- | --- |
| Client to service through Gateway | Synchronous HTTP | Interactive request/response | Return a bounded error; never hang indefinitely |
| Order to Product snapshot lookup | Synchronous HTTP | Order needs trusted current product status and price before acceptance | Timeout and reject order creation without persisting a misleading accepted order |
| Order to Inventory reservation | Kafka command/event | Reservation is a durable saga step and eventual completion is acceptable | Order remains pending; retries and operator visibility apply |
| Order to Payment | Kafka command/event | Payment is a long-running, failure-prone saga step | Order remains in payment state; duplicate commands are idempotent |
| Order result to Notification | Kafka event | Notification must not block business completion | Consumer retries safely and records delivery failure |

Payment exposes no public business route. Its Kafka consumer invokes the application workflow, while its provider adapter remains an outbound dependency. Provider calls use `paymentId` as an idempotency key and run outside database transactions. The terminal payment update, processed input, and outcome outbox share one local transaction.

Order consumes Payment outcomes with the same at-least-once rules used for Inventory outcomes. A success changes the order and writes inventory-confirmation plus order-confirmed outbox rows in one `order_db` transaction. A decline writes the cancellation, inventory-release command, order-cancelled event, and inbox row together. No service writes another service's database.

Notification consumes only `OrderConfirmed` and `OrderCancelled` because Order owns the final customer-visible outcome. Its inbox and `PENDING` notification share one `notification_db` transaction. The provider call runs outside that transaction; success or failure is finalized in a later short transaction. A stable notification ID is the external idempotency key for ambiguous retries.

## Synchronous standards

- Use Spring `RestClient` for imperative MVC services; it matches the blocking stack and avoids adding reactive complexity.
- Configure connection and response timeouts.
- Propagate correlation ID and authenticated subject context when appropriate.
- Map remote failures into explicit infrastructure errors without leaking downstream payloads.
- Retry only transient, idempotent operations with a small bounded policy.
- Use a circuit breaker where repeated downstream failure would exhaust resources.

## Asynchronous standards

- Events use a metadata envelope containing `eventId`, `eventType`, `eventVersion`, `occurredAt`, `correlationId`, and `aggregateId`.
- Commands name an intended action; events state a fact that already occurred.
- Aggregate ID is the Kafka message key when per-aggregate ordering matters.
- Producers use a transactional outbox when database state and publication must not diverge.
- Consumers assume at-least-once delivery and enforce idempotency before external side effects. Provider adapters also need a stable idempotency key because an inbox cannot atomically commit with an external provider.
- Dead-letter handling is operational visibility, not a substitute for correct error handling.

## Failure model

Network calls can time out after the remote side completed, so callers cannot equate timeout with “nothing happened.” Kafka can redeliver messages. Processes can crash between database and network operations. The design therefore favors idempotent commands, persisted workflow state, unique event IDs, explicit state transitions, and observable retry/failure status.

## Avoided patterns

- Routing internal calls through Gateway
- Kafka for simple catalog reads
- Unbounded retries
- Retrying non-idempotent writes blindly
- Long chains of synchronous calls
- Shared database queries disguised as integration
- Events containing internal JPA entities
