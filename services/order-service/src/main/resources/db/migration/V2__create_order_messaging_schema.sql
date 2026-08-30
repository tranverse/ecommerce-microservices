CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    event_key VARCHAR(128) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    CONSTRAINT chk_outbox_event_version CHECK (event_version > 0),
    CONSTRAINT chk_outbox_payload_object CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT chk_outbox_attempts CHECK (attempts >= 0),
    CONSTRAINT chk_outbox_next_attempt CHECK (next_attempt_at >= occurred_at),
    CONSTRAINT chk_outbox_correlation_id CHECK (
        correlation_id ~ '^[A-Za-z0-9._:-]{1,128}$'
    )
);

CREATE INDEX idx_outbox_unpublished
    ON outbox_events (next_attempt_at, occurred_at, id)
    WHERE published_at IS NULL;

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    consumer_name VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_processed_consumer_name CHECK (consumer_name <> ''),
    CONSTRAINT chk_processed_event_type CHECK (event_type <> '')
);

CREATE INDEX idx_processed_events_processed_at
    ON processed_events (processed_at);
