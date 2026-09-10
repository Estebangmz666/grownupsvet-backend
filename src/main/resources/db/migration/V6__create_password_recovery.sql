ALTER TABLE users ADD COLUMN authentication_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN optimistic_lock_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD CONSTRAINT ck_users_authentication_version CHECK (authentication_version >= 0);

CREATE TABLE password_recovery_challenges (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    challenge_id UUID NOT NULL,
    email_at_request VARCHAR(254) NOT NULL,
    authentication_version BIGINT NOT NULL,
    code_digest BYTEA,
    code_expires_at TIMESTAMPTZ NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    reset_token_hash VARCHAR(64) UNIQUE,
    reset_expires_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_password_recovery_attempts CHECK (failed_attempts BETWEEN 0 AND 5),
    CONSTRAINT ck_password_recovery_code_digest CHECK (code_digest IS NULL OR OCTET_LENGTH(code_digest) = 32),
    CONSTRAINT ck_password_recovery_reset_pair CHECK ((reset_token_hash IS NULL) = (reset_expires_at IS NULL))
);
CREATE INDEX ix_password_recovery_updated_at ON password_recovery_challenges(updated_at);

-- Keys are HMACs with distinct purpose prefixes, never raw email or IP addresses.
CREATE TABLE password_recovery_rate_limits (
    bucket_key VARCHAR(64) PRIMARY KEY,
    window_started_at TIMESTAMPTZ NOT NULL,
    used_count INTEGER NOT NULL,
    last_accepted_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_password_recovery_rate_count CHECK (used_count >= 0)
);
CREATE INDEX ix_password_recovery_rate_window ON password_recovery_rate_limits(window_started_at);
