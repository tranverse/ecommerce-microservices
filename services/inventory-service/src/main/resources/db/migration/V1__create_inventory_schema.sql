CREATE TABLE inventory_items (
    product_id UUID PRIMARY KEY,
    total_quantity INTEGER NOT NULL,
    reserved_quantity INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_inventory_total_non_negative CHECK (total_quantity >= 0),
    CONSTRAINT chk_inventory_reserved_non_negative CHECK (reserved_quantity >= 0),
    CONSTRAINT chk_inventory_reserved_within_total CHECK (reserved_quantity <= total_quantity)
);

CREATE TABLE inventory_reservations (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_inventory_reservations_order_id UNIQUE (order_id),
    CONSTRAINT chk_inventory_reservation_status CHECK (status IN ('RESERVED', 'RELEASED', 'CONFIRMED'))
);

CREATE TABLE inventory_reservation_items (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    CONSTRAINT fk_inventory_reservation_items_reservation
        FOREIGN KEY (reservation_id) REFERENCES inventory_reservations (id) ON DELETE CASCADE,
    CONSTRAINT uk_inventory_reservation_product UNIQUE (reservation_id, product_id),
    CONSTRAINT chk_inventory_reservation_item_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_inventory_reservation_items_product_id
    ON inventory_reservation_items (product_id);
