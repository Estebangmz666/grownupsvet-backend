CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_normalized CHECK (email = LOWER(BTRIM(email)) AND email <> ''),
    CONSTRAINT ck_users_password_hash_not_blank CHECK (BTRIM(password_hash) <> ''),
    CONSTRAINT ck_users_role CHECK (role IN ('OWNER', 'VETERINARIAN', 'ADMINISTRATOR'))
);
