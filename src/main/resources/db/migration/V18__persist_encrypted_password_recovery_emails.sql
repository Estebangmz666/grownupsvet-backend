CREATE TABLE password_recovery_email_tasks (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    challenge_id UUID NOT NULL,
    authentication_version BIGINT NOT NULL,
    task_type VARCHAR(24) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    encrypted_payload BYTEA,
    payload_nonce BYTEA,
    expires_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    lease_token UUID,
    lease_expires_at TIMESTAMPTZ,
    last_error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    CONSTRAINT uk_password_recovery_email_intent UNIQUE (user_id, challenge_id, task_type),
    CONSTRAINT ck_password_recovery_email_type CHECK (task_type IN ('RECOVERY_CODE', 'RESET_NOTIFICATION')),
    CONSTRAINT ck_password_recovery_email_status CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'SUPERSEDED')),
    CONSTRAINT ck_password_recovery_email_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_password_recovery_email_version CHECK (authentication_version >= 0),
    CONSTRAINT ck_password_recovery_email_expiry CHECK
        ((task_type = 'RECOVERY_CODE' AND expires_at IS NOT NULL) OR (task_type = 'RESET_NOTIFICATION' AND expires_at IS NULL)),
    CONSTRAINT ck_password_recovery_email_payload CHECK
        ((status IN ('PENDING', 'SENDING') AND encrypted_payload IS NOT NULL AND OCTET_LENGTH(encrypted_payload) >= 16
          AND payload_nonce IS NOT NULL AND OCTET_LENGTH(payload_nonce) = 12)
         OR (status IN ('SENT', 'FAILED', 'SUPERSEDED') AND encrypted_payload IS NULL AND payload_nonce IS NULL)),
    CONSTRAINT ck_password_recovery_email_lease CHECK
        ((status = 'SENDING' AND lease_token IS NOT NULL AND lease_expires_at IS NOT NULL)
         OR (status <> 'SENDING' AND lease_token IS NULL AND lease_expires_at IS NULL))
);
CREATE INDEX ix_password_recovery_email_pending
    ON password_recovery_email_tasks(next_attempt_at, created_at, id) WHERE status IN ('PENDING', 'SENDING');
CREATE INDEX ix_password_recovery_email_user ON password_recovery_email_tasks(user_id, status);
