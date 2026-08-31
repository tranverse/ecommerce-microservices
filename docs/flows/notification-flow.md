# Notification Delivery Flow

Status: **Terminal Order event consumption and simulated system delivery implemented.**

## Why the flow is asynchronous

Order completion is a business fact; sending a message is a secondary reaction. If notification delivery were a synchronous Order dependency, a slow provider could exhaust Order threads and a provider outage could make a successfully paid order appear to fail. Kafka allows Notification to lag temporarily while Order remains authoritative and available.

## Processing flow

```mermaid
sequenceDiagram
    participant K as Kafka
    participant L as Order lifecycle listener
    participant DB as notification_db
    participant P as NotificationSender

    K->>L: OrderConfirmed or OrderCancelled v1
    L->>L: Validate envelope, key, payload and correlation ID
    L->>DB: tx1: create PENDING notification + inbox
    DB-->>L: durable notificationId
    L->>DB: tx2: lock and mark PROCESSING
    DB-->>L: delivery snapshot
    L->>P: send using notificationId
    alt provider accepts
        L->>DB: tx3: mark SENT
    else provider rejects
        L->>DB: tx3: mark FAILED and safe reason
    end
```

The provider call is outside the transaction. An external system cannot participate in the PostgreSQL commit, and holding a database connection across uncontrolled latency would reduce capacity without creating atomicity.

## Duplicate handling

| Input | Result |
| --- | --- |
| Same event ID delivered again | Inbox locates the existing notification; `SENT` delivery is skipped |
| New event ID, same order and same terminal type | New inbox row records the semantic duplicate; no second notification is created |
| Same order with contradictory terminal type | Reject as a contract/state conflict and route to DLT |
| Redelivery while state is not `SENT` | Delivery may resume with the same notification ID |

Kafka's `orderId` key preserves ordering for one order inside the consumer group. Database uniqueness remains a defensive backstop rather than relying only on broker ordering.

## Failure matrix

| Failure | Durable state | Recovery |
| --- | --- | --- |
| Invalid/unknown event | No notification | Non-retryable DLT record |
| Database unavailable before acceptance | No inbox/notification | Bounded Kafka retry |
| Crash after acceptance | `PENDING` + inbox | Kafka redelivery can resume delivery |
| Provider rejects delivery | `FAILED` + attempt count | Future retry worker scans indexed failure state |
| Crash after provider accepts but before `SENT` | `PROCESSING` | Retry with the same provider idempotency key |
| Duplicate after `SENT` | Existing `SENT` row | No provider call |

Exactly-once external delivery is not promised. A future provider adapter must pass `notificationId` as its idempotency key; otherwise the last crash window can produce duplicate customer messages.
