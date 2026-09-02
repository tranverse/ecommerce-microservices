# ADR 013: Use Bounded Blocking Retry with Dead-Letter Topics

## Status

Accepted and implemented for the current Kafka consumers.

## Context

Inventory, Order, Payment, and Notification consume at-least-once Kafka records. A temporary database or provider outage can succeed on another attempt, while a malformed envelope or contradictory state cannot improve by waiting. Unbounded retry can block one partition forever; acknowledging a failed record before its DLT publication is confirmed can lose operational evidence.

## Options considered

1. Retry every failure forever: simple but allows poison messages to stop a partition permanently.
2. Drop failed records after logging: preserves throughput but loses durable evidence and makes repair unsafe.
3. Bounded blocking retry followed by a DLT: preserves per-partition ordering, has a small topology, and fits short transient failures.
4. Non-blocking retry topics: releases the consumer thread during long delays, but adds topics, consumers, ordering trade-offs, and operational complexity.
5. Broker or platform-specific dead-letter handling: reduces application code but makes exception classification and portability weaker.

## Decision

Choose option 3 for the current short retry window.

- A record receives one initial attempt plus three configurable exponential retries: 250 ms, 500 ms, and 1 second by default, capped at 2 seconds per interval.
- `InvalidEventException` and deterministic state conflicts skip retry and go directly to `<original-topic>.DLT`.
- Transient runtime failures are retried. Payment processor unavailability is the reference failure-path test.
- The DLT uses the original partition, so every DLT has at least as many partitions as its source topic.
- DLT publication must receive broker acknowledgement within the configured timeout. A failed recovery does not silently discard the source record.
- Spring Kafka's original topic, partition, offset, consumer group, and exception headers are retained. Exception stack-trace headers are excluded to control record size and information exposure.
- Failure, recovery, and recovery-failure counters use bounded topic labels. Logs contain metadata and exception type, never the message payload.
- Replay is an explicit operator action for one topic/partition/offset. It republishes a clean key/value record and leaves the DLT record intact for audit.

## Consequences

- A poison record no longer blocks later records indefinitely.
- Short retry delays block that source partition deliberately, preserving ordering for one order aggregate.
- Consumers and provider ports must remain idempotent because retry and replay can repeat side effects.
- The default worst-case backoff is short enough for the current `max.poll.interval.ms`; increasing it substantially requires reevaluating consumer liveness.
- Long recovery windows should move to non-blocking retry topics rather than increasing blocking sleeps.
- DLT depth and recovery-failure metrics require alerts and operator ownership; a DLT is not automatic business compensation.
