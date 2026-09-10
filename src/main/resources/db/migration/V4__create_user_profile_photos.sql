CREATE TABLE user_profile_photos (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    content BYTEA NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    size_bytes INTEGER NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_user_profile_photos_content_not_empty CHECK (OCTET_LENGTH(content) > 0),
    CONSTRAINT ck_user_profile_photos_content_type CHECK (content_type IN ('image/jpeg', 'image/png')),
    CONSTRAINT ck_user_profile_photos_size_bytes CHECK (size_bytes = OCTET_LENGTH(content) AND size_bytes <= 2097152),
    CONSTRAINT ck_user_profile_photos_width CHECK (width > 0 AND width <= 512),
    CONSTRAINT ck_user_profile_photos_height CHECK (height > 0 AND height <= 512)
);
