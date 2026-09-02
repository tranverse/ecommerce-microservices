# 19. Kafka Retries, Dead Letters, and Replay

## Concept

A Kafka consumer failure needs classification, not a blanket retry rule. A **transient failure** may disappear, such as a short database outage. A **poison message** is deterministic, such as malformed JSON, an unsupported event version, or a state conflict. A dead-letter topic (DLT) stores a record that normal processing could not safely complete.

## Why Microservices Need It

Kafka retains the message independently from the service process. If one record always fails and the consumer keeps seeking back to it, later records in that partition never run. If the consumer skips it without durable evidence, the distributed workflow can remain inconsistent with no repair path.

In a monolith, a failed method usually rolls back one database transaction and returns an error to the caller. The caller can retry the same request immediately. In microservices, the producer may already be gone, several services may have committed local state, and Kafka can redeliver after a crash. Recovery must therefore combine bounded retries, durable failure evidence, idempotency, and operator replay.

## Current Policy

```text
valid record -> handler -> success -> commit offset
                       -> transient error -> 3 bounded retries -> DLT
invalid/conflicting record ---------------------------> DLT immediately
DLT publish failure ----------------------------------> source remains retryable
```

Inventory, Order, Payment, and Notification use the same policy but own their configuration inside each deployable service. The default has one initial attempt plus three retries with 250 ms exponential backoff. Increasing the delay is not free: blocking retry pauses that partition and must stay well below Kafka's poll interval.

## Why Not Retry Every Error?

- Invalid JSON will remain invalid.
- Unsupported versions require a producer/consumer compatibility decision.
- A contradictory saga result needs investigation, not another state mutation.
- Infinite retry hides the incident while consumer lag grows.
- Retrying external calls is safe only when the provider accepts a stable idempotency key.

## What the DLT Contains

The original key and value are retained along with source topic, partition, offset, consumer group, and exception metadata. Stack traces are deliberately omitted from Kafka headers because they can be large and can expose internal details. Application logs and traces carry the diagnostic context.

The DLT has the same partition count as the source because recovery preserves the original partition. The DLT is not a queue that should be replayed blindly and it is not Saga compensation; it is durable technical evidence.

## Safe Replay Checklist

1. Inspect the exact DLT topic, partition, and offset.
2. Identify whether the cause was contract, data, code, configuration, or dependency availability.
3. Deploy or verify the fix first.
4. Confirm the target consumer is idempotent for the record's key/event ID.
5. Run the replay script without `-Execute`; review the exact topic location and key without exposing payload or failure headers.
6. Replay exactly one record and observe consumer recovery metrics, logs, inbox, and business state.
7. Keep the original DLT record for audit; record the operator action externally in a real production environment.

Example dry run:

```powershell
.\scripts\replay-dlt-record.ps1 `
  -OriginalTopic payment.commands.v1 `
  -Partition 0 `
  -Offset 42
```

Add `-Execute` only after investigation. The script strips DLT failure headers by republishing only key/value, so the normal listener sees an ordinary source record and applies all validation again.

## Review Questions

1. Why is insufficient stock a business outcome instead of a technical retry?
2. What happens to one partition during blocking backoff?
3. Why must DLT publication be acknowledged before the source offset is treated as recovered?
4. Why can replay still duplicate an external payment or notification call?
5. When would retry topics be a better choice than the current blocking retry?
