-- Tenant signup verification + rate limiting tables
CREATE TABLE IF NOT EXISTS signup_verification_tokens (
    token VARCHAR(128) PRIMARY KEY,
    tenant_slug VARCHAR(64) NOT NULL,
    admin_email VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_signup_token_tenant
    ON signup_verification_tokens (tenant_slug);

CREATE INDEX IF NOT EXISTS idx_signup_token_expires
    ON signup_verification_tokens (expires_at);

CREATE TABLE IF NOT EXISTS signup_rate_limit (
    client_key VARCHAR(128) PRIMARY KEY,
    last_attempt TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_signup_throttle_last_attempt
    ON signup_rate_limit (last_attempt);
