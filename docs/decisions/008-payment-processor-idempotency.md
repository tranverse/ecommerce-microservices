# ADR 008: Isolate Provider Calls and Require Payment Idempotency

## Status

Accepted and implemented in the Payment application workflow. Kafka adapters remain pending.

## Context

Charging or refunding through an external processor cannot participate in the local PostgreSQL transaction. The service can crash after the processor accepts a request but before its outcome is committed. Kafka will also redeliver commands.

## Options Considered

1. Hold a database transaction and row lock while calling the processor. This increases lock/connection time and still cannot roll back the external side effect.
2. Mark an attempt failed whenever the processor times out. This can misclassify a completed charge and cause unsafe retries or inconsistent order decisions.
3. Use a distributed two-phase commit with the processor. Typical payment providers do not support it, and it creates tight operational coupling.
4. Persist a stable payment identity, call the processor outside the database transaction with that identity as an idempotency key, then apply the outcome in a second short transaction.

## Decision

Choose option 4. `PaymentApplicationService` separates initialization, processor interaction, and outcome persistence. `PaymentProcessor` implementations must treat `paymentId` as the idempotency key and return the same business outcome/reference for repeated requests.

`order_id` is unique in `payment_db`. Replays must match the original amount and currency. Known declines become terminal `FAILED`; technical failures preserve a retryable state. Outcome application locks the payment row and validates provider references.

## Reasoning

This design makes the unavoidable non-atomic boundary explicit and bounds database resource use. A stable key converts ambiguous retry from “possibly charge again” to “retrieve the same provider operation.” Database constraints and state transitions protect local correctness under concurrency.

## Trade-offs

- Correctness depends on the provider honoring its idempotency contract.
- A `PENDING` payment can require reconciliation if retries are exhausted.
- Two concurrent deliveries may both contact the processor; the stable key prevents duplicate business effects.
- The current simulator proves application semantics, not real provider behavior.
- Provider outcome and Kafka publication will require a transactional outbox.

## Consequences

- Processor calls must never run inside a database transaction.
- Logs include internal IDs and state but no credentials or payment instrument data.
- A differing result/reference for an already-applied payment is a contract violation, not a normal state transition.
- Refund uses the same stable payment identity and remains retryable after technical failure.
- Future Kafka consumers must acknowledge only after the local workflow/outbox transaction succeeds.
