# Kafka Consumer Failure Flow

```mermaid
flowchart TD
    A[Kafka delivers source record] --> B[Strict envelope and key validation]
    B -->|invalid contract| F[Publish once to source.DLT]
    B -->|valid| C[Run idempotent application handler]
    C -->|success| D[Commit local state/inbox and source offset]
    C -->|deterministic conflict| F
    C -->|transient failure| E{Retry budget left?}
    E -->|yes| G[Exponential backoff on this partition]
    G --> C
    E -->|no| F
    F --> H{Broker acknowledges DLT publish?}
    H -->|yes| I[Commit source offset and emit recovery metric]
    H -->|no| J[Keep source record retryable and emit recovery-failure metric]
    I --> K[Operator investigates and fixes root cause]
    K --> L[Inspect exact DLT partition/offset]
    L --> M{Explicit replay approved?}
    M -->|no| N[Retain DLT audit record]
    M -->|yes| O[Republish clean key/value to original topic]
    O --> A
```

The retry is local to the failing source partition; other partitions continue. A source key is the aggregate/order ID, so short blocking retry also preserves per-aggregate ordering. Replay does not delete or mutate the DLT record and does not bypass normal validation, inbox, or state-machine rules.

Use `scripts/replay-dlt-record.ps1` only after the bad contract, data, configuration, or dependency has been corrected. The dry run prints only the topic location and key; it suppresses the message value and failure headers because older records may contain verbose stack traces.
