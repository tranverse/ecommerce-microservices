CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    source_event_id UUID NOT NULL,
    order_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    notification_type VARCHAR(40) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    content VARCHAR(500) NOT NULL,
    cancellation_reason VARCHAR(40),
    correlation_id VARCHAR(128) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    failure_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_notifications_source_channel UNIQUE (source_event_id, channel),
    CONSTRAINT uk_notifications_order_channel UNIQUE (order_id, channel),
    CONSTRAINT chk_notifications_type CHECK (
        notification_type IN ('ORDER_CONFIRMED', 'ORDER_CANCELLED')
    ),
    CONSTRAINT chk_notifications_channel CHECK (channel IN ('SYSTEM')),
    CONSTRAINT chk_notifications_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED')
    ),
    CONSTRAINT chk_notifications_content CHECK (content <> ''),
    CONSTRAINT chk_notifications_attempts CHECK (attempts >= 0),
    CONSTRAINT chk_notifications_correlation_id CHECK (
        correlation_id ~ '^[A-Za-z0-9._:-]{1,128}$'
    ),
    CONSTRAINT chk_notifications_cancellation_reason CHECK (
        (notification_type = 'ORDER_CANCELLED' AND cancellation_reason IS NOT NULL)
        OR (notification_type = 'ORDER_CONFIRMED' AND cancellation_reason IS NULL)
    ),
    CONSTRAINT chk_notifications_cancellation_reason_value CHECK (
        cancellation_reason IS NULL OR cancellation_reason IN (
            'INVENTORY_UNAVAILABLE',
            'INSUFFICIENT_INVENTORY',
            'PAYMENT_FAILED',
            'SYSTEM_ERROR'
        )
    ),
    CONSTRAINT chk_notifications_delivery_state CHECK (
        (status = 'SENT' AND sent_at IS NOT NULL AND failure_reason IS NULL)
        OR (status = 'FAILED' AND sent_at IS NULL AND failure_reason IS NOT NULL)
        OR (status IN ('PENDING', 'PROCESSING') AND sent_at IS NULL AND failure_reason IS NULL)
    )
);

CREATE INDEX idx_notifications_status_updated
    ON notifications (status, updated_at, id);

CREATE INDEX idx_notifications_customer_created
    ON notifications (customer_id, created_at DESC);

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
