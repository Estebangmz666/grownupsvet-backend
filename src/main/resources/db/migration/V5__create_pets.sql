CREATE TABLE pets (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES owner_profiles(user_id),
    name VARCHAR(100) NOT NULL,
    species VARCHAR(8) NOT NULL,
    breed VARCHAR(100),
    sex VARCHAR(8),
    date_of_birth DATE,
    date_of_birth_estimated BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_pets_name_not_blank CHECK (BTRIM(name) <> ''),
    CONSTRAINT ck_pets_species CHECK (species IN ('DOG', 'CAT')),
    CONSTRAINT ck_pets_breed_not_blank CHECK (breed IS NULL OR BTRIM(breed) <> ''),
    CONSTRAINT ck_pets_sex CHECK (sex IS NULL OR sex IN ('MALE', 'FEMALE', 'UNKNOWN')),
    CONSTRAINT ck_pets_date_of_birth CHECK (date_of_birth IS NULL OR date_of_birth >= DATE '0001-01-01'),
    CONSTRAINT ck_pets_estimated_birth_requires_date CHECK (NOT date_of_birth_estimated OR date_of_birth IS NOT NULL),
    CONSTRAINT ck_pets_timestamp_order CHECK (updated_at >= created_at)
);

CREATE INDEX ix_pets_owner_created_at ON pets(owner_id, created_at DESC, id);
CREATE INDEX ix_pets_owner_active_created_at ON pets(owner_id, active, created_at DESC, id);

-- Every pet belongs to exactly one owner profile. There is no cascading deletion
-- or ownership transfer. active=false archives the pet without deleting history;
-- future reservation creation must require an active pet belonging to its owner.
-- A birth date may be exact, estimated, or absent. Future dates are rejected using
-- the application Clock, not a SQL CHECK whose result would depend on the date.
