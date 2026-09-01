# 16 - Correlation IDs

## The concept

A correlation ID is a stable, non-secret identifier for one logical business request or workflow. The client may send a safe `X-Correlation-ID`; otherwise Gateway creates one. Services return it, propagate it over HTTP and event envelopes, and place it in logging MDC.

## Why microservices need it

In a monolith, one request usually remains in one process and one log stream. In microservices, creating an order crosses HTTP, local transactions, outbox publishers, Kafka consumers, and scheduled work. Timestamp proximity is not enough to prove that two log lines belong to the same customer action.

## Monolith versus microservices

```text
Monolith: request -> one process/thread context -> logs

Microservices:
request -> Gateway -> Order -> Product
                     |
                     +-> outbox -> Kafka -> Inventory -> Payment -> Notification
```

The correlation ID must be explicitly copied at every boundary. Losing it at one publisher or listener breaks the diagnostic chain.

## Current implementation

- Gateway accepts only a restricted character set and length, preventing log injection and unbounded metadata.
- HTTP services store the value in MDC and echo it in responses.
- Order propagates it to Product.
- Versioned Kafka envelopes persist it, and listeners restore it into MDC while processing.
- API errors expose it so support can match a client failure to internal logs without exposing a stack trace.

Correlation IDs are not authentication. A caller can know or choose one, so authorization must never rely on it. They are also not metric labels: their high cardinality would make time series and log indexes expensive.

## Exercise

Send two order requests using two explicit safe correlation IDs. In Grafana Explore, query Order logs by each value and identify the matching `orderId`. Then send an unsafe value such as whitespace and path characters and confirm Gateway replaces it.

## Questions

1. Why is a correlation ID allowed in an error response while an access token is not?
2. Why must Kafka consumers restore the ID into MDC?
3. Why would using `orderId` as the only correlation mechanism fail during pre-persistence validation?

