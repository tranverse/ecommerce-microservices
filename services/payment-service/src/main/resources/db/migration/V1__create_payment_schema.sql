CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(30),
    provider_reference VARCHAR(128),
    refund_reference VARCHAR(128),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_payments_order_id UNIQUE (order_id),
    CONSTRAINT uk_payments_provider_reference UNIQUE (provider_reference),
    CONSTRAINT uk_payments_refund_reference UNIQUE (refund_reference),
    CONSTRAINT chk_payments_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_payments_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_payments_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED')),
    CONSTRAINT chk_payments_failure_reason CHECK (failure_reason IS NULL OR failure_reason IN ('DECLINED')),
    CONSTRAINT chk_payments_state_data CHECK (
        (status = 'PENDING' AND failure_reason IS NULL AND provider_reference IS NULL AND refund_reference IS NULL)
        OR (status = 'COMPLETED' AND failure_reason IS NULL AND provider_reference IS NOT NULL AND refund_reference IS NULL)
        OR (status = 'FAILED' AND failure_reason IS NOT NULL AND provider_reference IS NULL AND refund_reference IS NULL)
        OR (status = 'REFUNDED' AND failure_reason IS NULL AND provider_reference IS NOT NULL AND refund_reference IS NOT NULL)
    )
);

CREATE INDEX idx_payments_status_created_at ON payments (status, created_at);
