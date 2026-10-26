-- HMAC keys contain neither raw email addresses nor transport peer addresses.
CREATE TABLE login_attempt_rate_limits (
    bucket_key VARCHAR(64) PRIMARY KEY,
    window_started_at TIMESTAMPTZ NOT NULL,
    used_count INTEGER NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_login_rate_limit_key CHECK (bucket_key ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_login_rate_limit_count CHECK (used_count >= 0),
    CONSTRAINT ck_login_rate_limit_window CHECK (expires_at > window_started_at)
);
CREATE INDEX ix_login_rate_limit_expiration ON login_attempt_rate_limits(expires_at);
