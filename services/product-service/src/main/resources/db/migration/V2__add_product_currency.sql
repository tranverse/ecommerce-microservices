ALTER TABLE products
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'USD';

ALTER TABLE products
    ADD CONSTRAINT chk_products_currency_format CHECK (currency ~ '^[A-Z]{3}$');

ALTER TABLE products
    ALTER COLUMN currency DROP DEFAULT;
