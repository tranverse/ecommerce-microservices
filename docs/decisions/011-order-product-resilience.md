# ADR 011: Protect the Order-to-Product Call at the Owning Boundary

## Status

Accepted and implemented.

## Context

Order must synchronously obtain Product's authoritative price and sale status before accepting a new order. A network call can be slow, time out, return a transient server error, or remain unavailable after both services passed startup health checks. Unbounded waiting exhausts request threads, while indiscriminate retries amplify an outage and can duplicate unsafe operations.

The Product batch lookup is an idempotent `GET`. The public order creation endpoint is a `POST`, so retrying at Gateway would repeat the entire workflow and would hide the dependency-specific policy from Order.

## Options considered

1. Use timeouts only: simple, but every request continues to hit a failing Product instance.
2. Retry every failure: may recover transient errors, but retries deterministic 4xx responses and increases load during an outage.
3. Retry `POST /orders` at Gateway: the idempotency key limits duplicate orders, but Gateway cannot classify Product failures and would repeat authentication, validation, and persistence checks.
4. Apply a small retry and a circuit breaker around Order's Product adapter: policy remains next to the dependency boundary and can distinguish failure classes.
5. Fall back to cached or client-supplied prices: improves acceptance availability but can create orders using stale or manipulated commercial data.

## Decision

Choose option 4 and reject option 5. Order protects the direct Product batch `GET` with:

- a 500-millisecond connection timeout and 1.5-second read timeout;
- at most two attempts, separated by 100 milliseconds;
- retry only for connection/read failures and HTTP 5xx responses;
- a count-based circuit breaker with a ten-call window, five-call minimum, 50% failure threshold, ten-second open period, and two half-open probes;
- HTTP 4xx responses excluded from retry and circuit-breaker failure statistics;
- a sanitized `503 PRODUCT_CATALOG_UNAVAILABLE` response after exhaustion or fail-fast rejection.

The composition is `CircuitBreaker(Retry(Product GET))`. One exhausted logical lookup counts as one circuit-breaker failure even though it used two HTTP attempts. Resilience4j instances are created through its registries so retry and circuit-breaker metrics are available to Micrometer.

The Product call remains outside Order's database transaction. Only after a trusted response is validated does a short transaction persist the order, snapshots, and first outbox command.

## Consequences

- A single transient 5xx or network failure can recover without asking the client to resubmit.
- Repeated dependency failures eventually open the circuit, so later requests fail quickly and reduce pressure on Product.
- New-order availability still depends on authoritative Product data; there is deliberately no price fallback.
- The maximum lookup work is bounded by two timed attempts plus one short retry delay. Even if connect and read budgets are both consumed, the 4.1-second inner budget remains below Gateway's five-second response timeout.
- Circuit-breaker state is local to each Order instance. Replicas learn dependency health independently; no distributed breaker state is required.
- Existing idempotent replays return the stored order before calling Product and remain available while Product is down.
- A 4xx response indicates a deterministic request/contract problem. It is sanitized at the public boundary but does not trigger repeated calls or open the infrastructure circuit.
- Operators must tune thresholds from observed latency/error metrics rather than increasing retries by guesswork.
