CREATE TABLE revoked_access_tokens (
    issuer VARCHAR(200) NOT NULL,
    jwt_id VARCHAR(100) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id),
    revoked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (issuer, jwt_id),
    CONSTRAINT ck_revoked_access_tokens_issuer_not_blank CHECK (BTRIM(issuer) <> ''),
    CONSTRAINT ck_revoked_access_tokens_jwt_id_not_blank CHECK (BTRIM(jwt_id) <> ''),
    CONSTRAINT ck_revoked_access_tokens_expiration CHECK (expires_at > revoked_at)
);

CREATE INDEX ix_revoked_access_tokens_expires_at ON revoked_access_tokens (expires_at);
CREATE INDEX ix_revoked_access_tokens_user_id ON revoked_access_tokens (user_id);
