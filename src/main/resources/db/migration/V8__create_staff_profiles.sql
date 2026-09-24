CREATE TABLE administrator_profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    full_name VARCHAR(150) NOT NULL CHECK (BTRIM(full_name) <> ''),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    updated_by UUID NOT NULL REFERENCES users(id),
    CONSTRAINT ck_administrator_timestamps CHECK (updated_at >= created_at)
);

CREATE TABLE veterinarian_profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    full_name VARCHAR(150) NOT NULL CHECK (BTRIM(full_name) <> ''),
    professional_phone_number VARCHAR(16) NOT NULL CHECK (professional_phone_number ~ '^\+[1-9][0-9]{1,14}$'),
    professional_registration_number VARCHAR(50) NOT NULL,
    biography VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    updated_by UUID NOT NULL REFERENCES users(id),
    CONSTRAINT uk_veterinarian_registration UNIQUE (professional_registration_number),
    CONSTRAINT ck_veterinarian_registration CHECK (
        professional_registration_number = UPPER(BTRIM(professional_registration_number))
        AND professional_registration_number ~ '^[A-Z0-9][A-Z0-9 ./-]{0,49}$'),
    CONSTRAINT ck_veterinarian_biography CHECK (biography IS NULL OR BTRIM(biography) <> ''),
    CONSTRAINT ck_veterinarian_timestamps CHECK (updated_at >= created_at)
);

CREATE TABLE veterinarian_qualifications (
    id UUID PRIMARY KEY,
    veterinarian_id UUID NOT NULL REFERENCES veterinarian_profiles(user_id),
    type VARCHAR(20) NOT NULL CHECK (type IN ('UNDERGRADUATE', 'SPECIALIZATION', 'MASTERS', 'DOCTORATE')),
    title VARCHAR(200) NOT NULL CHECK (BTRIM(title) <> ''),
    institution VARCHAR(200) NOT NULL CHECK (BTRIM(institution) <> ''),
    graduation_year INTEGER CHECK (graduation_year BETWEEN 1900 AND 9999),
    base_degree BOOLEAN NOT NULL DEFAULT FALSE,
    diploma_published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_qualification_base_type CHECK (NOT base_degree OR type = 'UNDERGRADUATE'),
    CONSTRAINT ck_qualification_timestamps CHECK (updated_at >= created_at)
);
CREATE UNIQUE INDEX uk_veterinarian_base_degree ON veterinarian_qualifications(veterinarian_id) WHERE base_degree;
CREATE INDEX ix_veterinarian_qualifications ON veterinarian_qualifications(veterinarian_id, base_degree DESC, created_at, id);
CREATE INDEX ix_administrator_profiles_created ON administrator_profiles(created_at DESC, user_id);
CREATE INDEX ix_veterinarian_profiles_created ON veterinarian_profiles(created_at DESC, user_id);

CREATE TABLE veterinarian_diplomas (
    qualification_id UUID PRIMARY KEY REFERENCES veterinarian_qualifications(id),
    content BYTEA NOT NULL,
    size_bytes INTEGER NOT NULL CHECK (size_bytes BETWEEN 1 AND 5242880),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_diploma_content_size CHECK (OCTET_LENGTH(content) = size_bytes)
);
