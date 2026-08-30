CREATE TABLE customer_orders (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    failure_reason VARCHAR(40),
    currency VARCHAR(3) NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_customer_orders_idempotency UNIQUE (customer_id, idempotency_key),
    CONSTRAINT chk_customer_orders_request_hash CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_customer_orders_status CHECK (
        status IN ('PENDING', 'INVENTORY_RESERVED', 'PAYMENT_PENDING', 'CONFIRMED', 'CANCELLED')
    ),
    CONSTRAINT chk_customer_orders_failure_reason CHECK (
        failure_reason IS NULL OR failure_reason IN (
            'CUSTOMER_CANCELLED',
            'INVENTORY_UNAVAILABLE',
            'INSUFFICIENT_INVENTORY',
            'PAYMENT_FAILED',
            'PAYMENT_TIMEOUT',
            'SYSTEM_ERROR'
        )
    ),
    CONSTRAINT chk_customer_orders_failure_state CHECK (
        (status = 'CANCELLED' AND failure_reason IS NOT NULL)
        OR (status <> 'CANCELLED' AND failure_reason IS NULL)
    ),
    CONSTRAINT chk_customer_orders_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_customer_orders_total CHECK (total_amount > 0)
);

CREATE INDEX idx_customer_orders_customer_created
    ON customer_orders (customer_id, created_at DESC);
CREATE INDEX idx_customer_orders_status_created
    ON customer_orders (status, created_at);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    product_id UUID NOT NULL,
    product_sku VARCHAR(64) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    unit_price NUMERIC(19, 2) NOT NULL,
    quantity INTEGER NOT NULL,
    line_total NUMERIC(19, 2) NOT NULL,
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES customer_orders (id) ON DELETE CASCADE,
    CONSTRAINT uk_order_items_line UNIQUE (order_id, line_number),
    CONSTRAINT uk_order_items_product UNIQUE (order_id, product_id),
    CONSTRAINT chk_order_items_line_number CHECK (line_number > 0),
    CONSTRAINT chk_order_items_unit_price CHECK (unit_price > 0),
    CONSTRAINT chk_order_items_quantity CHECK (quantity BETWEEN 1 AND 999),
    CONSTRAINT chk_order_items_line_total CHECK (line_total = unit_price * quantity)
);
