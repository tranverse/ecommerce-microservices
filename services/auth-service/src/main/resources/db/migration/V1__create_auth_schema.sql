CREATE TABLE auth_accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_auth_accounts_email UNIQUE (email),
    CONSTRAINT chk_auth_accounts_normalized_email CHECK (email = LOWER(email)),
    CONSTRAINT chk_auth_accounts_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE auth_account_roles (
    account_id UUID NOT NULL,
    role VARCHAR(30) NOT NULL,
    CONSTRAINT pk_auth_account_roles PRIMARY KEY (account_id, role),
    CONSTRAINT fk_auth_account_roles_account
        FOREIGN KEY (account_id) REFERENCES auth_accounts (id) ON DELETE CASCADE,
    CONSTRAINT chk_auth_account_role CHECK (role IN ('CUSTOMER', 'ADMIN'))
);

CREATE TABLE auth_refresh_tokens (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uk_auth_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_refresh_tokens_account
        FOREIGN KEY (account_id) REFERENCES auth_accounts (id) ON DELETE CASCADE,
    CONSTRAINT chk_auth_refresh_token_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_auth_refresh_tokens_account_id ON auth_refresh_tokens (account_id);
CREATE INDEX idx_auth_refresh_tokens_expires_at ON auth_refresh_tokens (expires_at);
