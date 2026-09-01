# 18 - Metrics and Centralized Logging

## The concepts

Metrics aggregate numeric behavior over time; logs record discrete events with context. Metrics answer **whether** a broad problem exists. Logs help explain **what** happened in a particular execution. Traces connect the timed path between them.

## Why microservices need both

In a monolith, one process and log file may be enough during development. Eight replicas and several infrastructure components make manual terminal inspection incomplete. An operator needs one service-level view and one query surface across instances.

## Implemented pipeline

```text
Spring Actuator -> Prometheus -> Grafana dashboard
Spring JSON stdout -> Docker -> Alloy -> Loki -> Grafana Explore
Tempo traceId <--------------------------> Loki traceId field
```

The dashboard starts with scrape health, HTTP request rate, average latency, 5xx rate, and JVM heap use. These are operational indicators, not business success criteria. A healthy JVM can still reject every payment, so later business counters should describe confirmed/cancelled orders and delivery outcomes without using customer/order IDs as tags.

## Structured logging

JSON gives each event stable fields such as timestamp, level, logger, message, trace ID, span ID, and correlation ID. It avoids brittle regular expressions over a decorative console pattern. Human-readable messages remain important, but machines can now filter fields safely.

Alloy labels only bounded values such as service, container, environment, and level. It deliberately does not label trace or correlation IDs. Loki can parse and filter those JSON fields at query time without creating a separate index stream for every request.

## Operational trade-offs

- High-cardinality labels increase memory, index, and query cost.
- Excessive INFO logging adds storage cost and may bury important signals.
- Retention must follow diagnostic, legal, and privacy requirements.
- Scraping an endpoint is not proof that its business dependencies are healthy.
- Centralized logs increase the impact of accidental secret logging, so redaction is a design requirement rather than a cleanup task.

## Exercise

Run `verify-compose.ps1`. In Grafana, confirm eight Prometheus targets are healthy. Open Loki Explore, filter `service="order-service"`, parse JSON, and find the `Created order` log. Copy its trace ID into Tempo and compare the spans.

## Questions

1. Which four labels are safe for an HTTP request-rate metric, and which identifiers are unsafe?
2. Why can a readiness metric be green while checkout is failing?
3. What should be logged when payment processing fails, and what must be omitted?

