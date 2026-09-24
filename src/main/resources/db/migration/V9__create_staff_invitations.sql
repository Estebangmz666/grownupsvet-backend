CREATE TABLE staff_invitations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invited_by UUID NOT NULL REFERENCES users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT ck_staff_invitation_status CHECK (status IN ('PENDING', 'CONSUMED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_staff_invitation_expiration CHECK (expires_at > created_at),
    CONSTRAINT ck_staff_invitation_consumption CHECK ((status = 'CONSUMED') = (consumed_at IS NOT NULL)),
    CONSTRAINT ck_staff_invitation_hash CHECK (token_hash ~ '^[0-9a-f]{64}$')
);
CREATE UNIQUE INDEX uk_staff_invitation_pending_user ON staff_invitations(user_id) WHERE status = 'PENDING';
CREATE INDEX ix_staff_invitation_user_created ON staff_invitations(user_id, created_at DESC);

-- A transaction creates both rows. Only the delivery worker can decrypt the temporary token.
CREATE TABLE staff_invitation_email_tasks (
    invitation_id UUID PRIMARY KEY REFERENCES staff_invitations(id) ON DELETE CASCADE,
    encrypted_token BYTEA,
    status VARCHAR(16) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_failure_code VARCHAR(40),
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_staff_invitation_email_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_staff_invitation_email_attempts CHECK (attempts BETWEEN 0 AND 10),
    CONSTRAINT ck_staff_invitation_email_secret CHECK ((status = 'PENDING') = (encrypted_token IS NOT NULL)),
    CONSTRAINT ck_staff_invitation_email_completion CHECK ((status = 'PENDING') = (completed_at IS NULL)),
    CONSTRAINT ck_staff_invitation_email_failure CHECK (last_failure_code IS NULL OR last_failure_code IN ('SMTP_DELIVERY_FAILED', 'INVITATION_DELIVERY_FAILED'))
);
CREATE INDEX ix_staff_invitation_email_pending ON staff_invitation_email_tasks(next_attempt_at) WHERE status = 'PENDING';
