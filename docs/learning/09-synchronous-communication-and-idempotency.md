# Synchronous Communication, Transaction Boundaries, and Idempotency

## The Concept

Synchronous communication means the caller waits for another service's answer before it can finish its current decision. Order uses it to ask Product for current, authoritative sale data before accepting an order.

Idempotency means retrying the same logical operation has the same externally visible result rather than creating duplicates. It is essential because a client can lose the response after the server committed and cannot know whether the operation happened.

## Why Microservices Need This Explicitly

A method call in one process fails clearly: it returns or throws. A network call has an ambiguous third outcome—the remote side may have completed but its response was lost. Services can also be slow or independently unavailable. Every call therefore needs an answer to four questions:

1. Must the caller wait for authoritative data?
2. How long will it wait?
3. What state is committed if the dependency fails?
4. Is a retry safe, and who recognizes it as the same operation?

## How It Looks in a Monolith

Order code could call a Product repository in memory and save everything inside one database transaction. Joins and foreign keys could validate references. There is no serialization, DNS, connection timeout, partial network failure, or independent deployment version.

That convenience also couples schemas and release cycles. Product slowness can still hurt Order, but the failure boundary is less visible.

## How It Changes in Microservices

Order cannot query `product_db` or create a JPA relationship to Product. It sends one batch HTTP request and stores only the returned business snapshot plus the opaque product UUID. Product remains the authority for current catalog state; Order becomes the authority for historical order facts.

```text
Order request
  -> verify JWT subject
  -> identify an idempotent replay
  -> call Product with connect/read timeout
  -> validate the response contract
  -> open a short order_db transaction
  -> re-check idempotency and commit snapshots
```

The HTTP call is outside `@Transactional`. Otherwise a slow Product Service consumes an Order DB connection and extends transaction/lock duration even though no database work is happening.

## Why One Batch Call?

Calling Product once per line item creates an N+1 network problem. A 50-item order would make 50 remote calls, multiply latency, create partial lookup failure, and load Product unnecessarily. The batch endpoint caps IDs at 50 and loads them in one repository operation.

## Idempotency Design

The client supplies an `Idempotency-Key`. Order scopes it to the authenticated customer and hashes a canonical, sorted `(productId, quantity)` representation.

| Situation | Result |
| --- | --- |
| First key/request | Create one order |
| Same key and same canonical request | Return that order |
| Same key and different request | Reject with `409` |
| Same key used by another customer | Separate namespace |
| Two concurrent identical requests | Unique DB constraint chooses one winner |

The application pre-check saves work, but only the database unique constraint is safe across multiple JVM instances. This is a recurring distributed-systems lesson: correctness cannot depend on process-local memory when the service scales horizontally.

## Timeout vs Retry

Timeouts are mandatory; retries are a business decision. Order currently sets a two-second Product connection timeout and three-second read timeout, then returns a sanitized `503` without saving an order.

The Product lookup is a GET and could support a small retry later, but retries can amplify an outage. We defer them until circuit-breaker/metrics work can make the policy visible and testable. Gateway does not retry `POST /orders`; the client uses its idempotency key when retrying after an ambiguous response.

## Trade-offs

- Order availability now depends on Product for new orders, but not for reading/replaying existing ones.
- Snapshots duplicate catalog fields, but preserve historical truth and avoid later cross-service reads.
- `202 Accepted` accurately describes a workflow that has started but has not yet reserved inventory or processed payment.
- Rejecting when Product is unavailable favors correctness over accepting an unverifiable order.

## Questions for You

1. Why would wrapping the Product HTTP call in the Order database transaction be harmful?
2. Why is an application-level “find then insert” check insufficient for concurrent retries?
3. If Product times out after receiving a GET, is retrying safer than retrying a payment POST? Why?
4. When the Kafka saga is added, where should the order row and outbox row share a transaction boundary?
