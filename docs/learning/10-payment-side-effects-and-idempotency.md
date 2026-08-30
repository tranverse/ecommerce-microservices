# Payment Side Effects and Idempotency

## The Concept

A payment charge is an external side effect: after the provider accepts it, rolling back a local PostgreSQL transaction cannot undo it. The caller may also time out without knowing whether the charge happened. Idempotency means repeating the same logical command has the same business result and does not repeat the side effect.

## Why Microservices Need It

Kafka provides at-least-once delivery, processes can crash, and networks can lose responses. A `PaymentRequested` command may therefore be handled more than once. Exactly-once delivery across Kafka, Payment DB, and an external provider is not a realistic guarantee; business-level idempotency is the practical guarantee.

Payment Service uses its durable `paymentId` as the provider idempotency key. The database separately enforces one payment per `orderId`.

## In a Monolith

Order and payment rows could share one database transaction, which simplifies local state changes. The external provider remains outside the database transaction:

```text
BEGIN -> update order -> call provider -> update payment -> COMMIT
```

If the process crashes after the provider call, the database rolls back but the money may already have moved. A monolith still needs a durable payment identity, idempotent provider requests, reconciliation, and compensation.

## In Microservices

Order owns `order_db`; Payment owns `payment_db`. Neither service can join the other's transaction. Kafka will deliver commands and outcomes, and an orchestrated saga will decide the next step:

```text
Order outbox -> PaymentRequested -> Payment local workflow
Payment outbox -> PaymentCompleted/PaymentFailed -> Order transition
```

Every boundary can fail independently. The design must answer whether a failure is a business outcome or a technical uncertainty.

## Business Failure vs Technical Failure

- `DECLINED` is a known business result, so Payment records terminal `FAILED`.
- timeout/unavailability is uncertainty, so Payment keeps `PENDING` and permits retry.
- failed refund transport keeps `COMPLETED`, because the charge remains completed and refund can be retried.

Marking a timeout as `FAILED` would be dangerous: the provider may have charged successfully just before the response was lost.

## Transaction Boundary

The implementation uses:

1. a short transaction to create or find `PENDING`;
2. the provider call outside a database transaction;
3. a short transaction with a row lock to apply the outcome.

The lock protects concurrent state transitions, not the network call. The processor contract protects repeated side effects.

## Questions to Reason About

1. If a provider does not support idempotency keys, what reconciliation data would you need before retrying?
2. Why is `orderId` uniqueness insufficient as the provider idempotency key when two services assign identifiers independently?
3. Should a refund failure cancel an already confirmed order, or create a separate operational state?
4. Where will `PaymentCompleted` be persisted so a crash cannot lose the event after committing `COMPLETED`?

The answer to question 4 motivates the transactional outbox in the next milestone.
