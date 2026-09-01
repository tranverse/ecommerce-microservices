# 17 - Distributed Tracing

## The concept

A trace models one distributed operation as spans. Each span records a timed unit of work and its parent relationship. W3C trace context propagates identifiers over HTTP; Spring observations also instrument Kafka producers/consumers and scheduled tasks.

## Why microservices need it

A monolith profiler or stack trace can often reveal a slow call chain in one process. A microservice request may spend time in Gateway security, Order, a Product HTTP call, serialization, and network queues. No single service owns the complete call stack.

## Implemented synchronous flow

```text
api-gateway: HTTP POST
  -> order-service: POST /api/v1/orders
       -> product-service: GET /api/v1/products/batch
```

Spring creates and propagates the spans; applications export them through OTLP to Tempo. One local smoke run produced fourteen spans across these three services, including security and HTTP client/server work.

## Tracing versus correlation

A trace is excellent for a bounded causally connected operation. The saga may pause in an outbox, be retried, and resume much later. Preserving a parent span across that durable boundary can create misleading durations and coupling. This project therefore uses:

- trace IDs for a synchronous or transport segment;
- correlation IDs for the complete business workflow;
- persisted event IDs for delivery identity and idempotency.

These identifiers answer different questions and should not be collapsed into one field.

## Sampling trade-offs

Keeping every trace provides maximum detail but has CPU, network, and storage cost. Compose samples every trace for learning. The standalone default disables export, and production must choose a policy from traffic volume and diagnostic goals. Errors and high-latency operations are often more valuable than ordinary successes, which is why production systems may use tail sampling in an OpenTelemetry Collector.

## Exercise

Run the Compose smoke test, open Grafana Explore with Tempo, and search for POST spans from `api-gateway`. Open the trace and identify the Order server span and Product batch client/server spans. Then stop Product and compare the failed trace with the circuit-breaker metrics.

## Questions

1. Why is a trace ID unsuitable as a Prometheus label?
2. What information does the HTTP client span add that an Order log alone cannot?
3. Why might tail sampling preserve failures better than simple head sampling?

