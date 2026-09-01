# Observability Request Flow

The following sequence shows how one order request creates signals without changing the business contract.

```mermaid
sequenceDiagram
    actor Client
    participant G as API Gateway
    participant O as Order Service
    participant P as Product Service
    participant T as Tempo
    participant L as Loki
    participant M as Prometheus
    participant UI as Grafana

    Client->>G: POST /api/v1/orders + X-Correlation-ID
    G->>O: JWT + correlation ID + traceparent
    O->>P: GET product batch + correlation ID + traceparent
    P-->>O: Trusted product snapshots
    O-->>G: 202 Accepted
    G-->>Client: Response + X-Correlation-ID

    par Trace export
        G-->>T: Gateway spans via OTLP
        O-->>T: Order and client spans via OTLP
        P-->>T: Product server span via OTLP
    and Centralized logs
        O-->>L: JSON log with traceId, spanId, correlationId
    and Metrics scrape
        M->>G: GET /actuator/prometheus
        M->>O: GET /actuator/prometheus
        M->>P: GET /actuator/prometheus
    end

    UI->>M: Dashboard queries
    UI->>T: Trace lookup
    UI->>L: Logs filtered by traceId or correlationId
```

One verified local run produced a single trace spanning Gateway, Order, and Product. The Order creation log contained the same trace ID plus the stable business correlation ID. Later outbox and Kafka work can be found through that correlation ID even when it belongs to a different trace segment.

