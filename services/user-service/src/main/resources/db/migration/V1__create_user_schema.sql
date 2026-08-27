CREATE TABLE user_profiles (
    id UUID PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    phone VARCHAR(32),
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_user_profiles_status CHECK (status IN ('ACTIVE', 'DEACTIVATED'))
);

CREATE TABLE user_addresses (
    id UUID PRIMARY KEY,
    user_profile_id UUID NOT NULL,
    label VARCHAR(50) NOT NULL,
    recipient_name VARCHAR(120) NOT NULL,
    line1 VARCHAR(200) NOT NULL,
    line2 VARCHAR(200),
    city VARCHAR(100) NOT NULL,
    state_province VARCHAR(100),
    postal_code VARCHAR(20) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    phone VARCHAR(32),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    default_marker BOOLEAN GENERATED ALWAYS AS (
        CASE WHEN is_default THEN TRUE ELSE NULL END
    ) STORED,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_user_addresses_profile
        FOREIGN KEY (user_profile_id) REFERENCES user_profiles(id) ON DELETE CASCADE,
    CONSTRAINT ck_user_addresses_country_code
        CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT uk_user_addresses_one_default
        UNIQUE (user_profile_id, default_marker) DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX idx_user_addresses_profile ON user_addresses(user_profile_id);
