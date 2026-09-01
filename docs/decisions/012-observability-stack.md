# ADR 012: Use OpenTelemetry with Prometheus and the Grafana Observability Stack

## Status

Accepted and implemented for the local platform.

## Context

Eight independently running services and an asynchronous saga cannot be diagnosed reliably from individual terminal windows. The platform needs service health and latency trends, request-hop reconstruction, and searchable logs. The design must remain vendor-neutral at the application boundary and runnable through the existing Docker Compose environment.

## Options considered

1. Application logs only: lowest setup cost, but weak for latency trends and cross-service request reconstruction.
2. Spring Boot Admin: useful application view, but not a complete metrics, trace, and centralized-log pipeline.
3. A proprietary hosted agent and backend: operationally capable, but requires an account, credentials, network access, and vendor-specific setup for every learner.
4. Micrometer/OpenTelemetry plus Prometheus, Tempo, Loki, Alloy, and Grafana: more local components, but open application protocols, one UI, and a realistic correlation workflow.
5. Elasticsearch-based logging: powerful full-text search, but heavier than needed for the local learning environment.

## Decision

Choose option 4.

- Every application exposes Micrometer's Prometheus registry and exports sampled spans using OpenTelemetry OTLP/HTTP.
- Prometheus scrapes the eight service endpoints over the Compose network.
- Applications write structured Logstash JSON to stdout; Alloy discovers only explicitly labelled application containers and sends those logs to Loki.
- Grafana provisions Prometheus, Tempo, and Loki data sources and one starter service dashboard.
- Trace IDs and correlation IDs remain queryable log fields, not Loki labels.
- Export is disabled by default outside Compose. Compose enables it and supplies the Tempo endpoint.

## Consequences

- Application instrumentation is portable to compatible managed backends.
- A request can be followed from aggregate metrics to a trace and then to exact logs.
- The local stack consumes more memory and disk than applications alone.
- Single-node local storage provides no production durability or high availability.
- Prometheus's unauthenticated scrape endpoints are acceptable only on the internal/loopback local network; production must restrict access.
- The outbox-based saga uses correlation IDs for end-to-end business continuity instead of forcing one misleading long-lived trace tree.
- Operators must actively manage sampling, retention, redaction, and label cardinality in production.

