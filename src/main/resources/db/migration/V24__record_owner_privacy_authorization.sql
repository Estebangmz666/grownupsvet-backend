ALTER TABLE owner_profiles
    ADD COLUMN privacy_notice_version varchar(10),
    ADD COLUMN privacy_authorized_at timestamptz,
    ADD CONSTRAINT ck_owner_profiles_privacy_authorization_pair
        CHECK ((privacy_notice_version IS NULL) = (privacy_authorized_at IS NULL));
