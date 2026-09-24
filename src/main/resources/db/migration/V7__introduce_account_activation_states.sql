ALTER TABLE users ADD COLUMN status VARCHAR(32);
-- Older deactivations did not bump the session version. Preserve their revocation
-- even if a credentialed staff account is later explicitly enabled again.
UPDATE users SET status = CASE WHEN active THEN 'ACTIVE' ELSE 'DISABLED' END,
    authentication_version = authentication_version + CASE WHEN active THEN 0 ELSE 1 END;
ALTER TABLE users ALTER COLUMN status SET NOT NULL;
ALTER TABLE users ALTER COLUMN status SET DEFAULT 'ACTIVE';
ALTER TABLE users DROP COLUMN active;
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE users DROP CONSTRAINT ck_users_role;
ALTER TABLE users ADD CONSTRAINT ck_users_role
    CHECK (role IN ('OWNER', 'VETERINARIAN', 'ADMINISTRATOR', 'SUPER_ADMIN'));
ALTER TABLE users ADD CONSTRAINT ck_users_status
    CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'DISABLED'));
ALTER TABLE users ADD CONSTRAINT ck_users_active_credentials
    CHECK (status <> 'ACTIVE' OR password_hash IS NOT NULL);
ALTER TABLE users ADD CONSTRAINT ck_users_pending_credentials
    CHECK (status <> 'PENDING_ACTIVATION' OR
           (role IN ('ADMINISTRATOR', 'VETERINARIAN') AND password_hash IS NULL));
ALTER TABLE users ADD CONSTRAINT ck_users_owner_credentials
    CHECK (role <> 'OWNER' OR password_hash IS NOT NULL);
ALTER TABLE users ADD CONSTRAINT ck_users_super_administrator_active
    CHECK (role <> 'SUPER_ADMIN' OR status = 'ACTIVE');
CREATE UNIQUE INDEX uk_users_single_super_administrator ON users(role)
    WHERE role = 'SUPER_ADMIN';
