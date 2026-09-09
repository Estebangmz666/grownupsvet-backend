CREATE TABLE owner_profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    full_name VARCHAR(150) NOT NULL,
    date_of_birth DATE NOT NULL,
    phone_number VARCHAR(16) NOT NULL,
    CONSTRAINT ck_owner_profiles_full_name_not_blank CHECK (BTRIM(full_name) <> ''),
    CONSTRAINT ck_owner_profiles_phone_number_international CHECK (phone_number ~ '^\+[1-9][0-9]{1,14}$')
);

-- Age is validated against an application Clock. It is not a timeless SQL CHECK.
-- A profile is created atomically with public OWNER registration. Existing staff
-- and historical development accounts are not forced to invent owner-only data.
