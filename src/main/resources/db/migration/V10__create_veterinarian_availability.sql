CREATE TABLE veterinarian_availability_slots (
    id UUID PRIMARY KEY,
    veterinarian_id UUID NOT NULL REFERENCES veterinarian_profiles(user_id),
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    updated_by UUID NOT NULL REFERENCES users(id),
    CONSTRAINT ck_veterinarian_availability_status CHECK (status IN ('PUBLISHED', 'BLOCKED')),
    CONSTRAINT ck_veterinarian_availability_duration CHECK (ends_at = starts_at + INTERVAL '30 minutes'),
    CONSTRAINT ck_veterinarian_availability_grid CHECK (
        DATE_TRUNC('minute', starts_at) = starts_at
        AND MOD(EXTRACT(EPOCH FROM starts_at)::NUMERIC, 1800) = 0
    ),
    CONSTRAINT ck_veterinarian_availability_version CHECK (version >= 0),
    CONSTRAINT ck_veterinarian_availability_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT uk_veterinarian_availability_start UNIQUE (veterinarian_id, starts_at)
);
CREATE INDEX ix_veterinarian_availability_published_start
    ON veterinarian_availability_slots(starts_at, veterinarian_id) WHERE status = 'PUBLISHED';

CREATE TABLE veterinarian_availability_events (
    id UUID PRIMARY KEY,
    slot_id UUID NOT NULL REFERENCES veterinarian_availability_slots(id),
    slot_version BIGINT NOT NULL,
    actor_id UUID NOT NULL REFERENCES users(id),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    previous_starts_at TIMESTAMP WITH TIME ZONE,
    previous_ends_at TIMESTAMP WITH TIME ZONE,
    previous_status VARCHAR(16),
    new_starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    new_ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    new_status VARCHAR(16) NOT NULL,
    reason VARCHAR(500),
    CONSTRAINT ck_veterinarian_availability_event_type CHECK (event_type IN ('CREATED', 'RESCHEDULED', 'BLOCKED', 'PUBLISHED')),
    CONSTRAINT ck_veterinarian_availability_event_status CHECK (
        (previous_status IS NULL OR previous_status IN ('PUBLISHED', 'BLOCKED'))
        AND new_status IN ('PUBLISHED', 'BLOCKED')
    ),
    CONSTRAINT ck_veterinarian_availability_event_shape CHECK (
        (event_type = 'CREATED' AND previous_starts_at IS NULL AND previous_ends_at IS NULL
            AND previous_status IS NULL AND reason IS NULL AND slot_version = 0)
        OR (event_type <> 'CREATED' AND previous_starts_at IS NOT NULL AND previous_ends_at IS NOT NULL
            AND previous_status IS NOT NULL AND reason IS NOT NULL AND BTRIM(reason) <> '' AND slot_version > 0)
    ),
    CONSTRAINT uk_veterinarian_availability_event_version UNIQUE (slot_id, slot_version)
);

CREATE FUNCTION reject_veterinarian_availability_event_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Veterinarian availability events are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_veterinarian_availability_event_immutable
    BEFORE UPDATE OR DELETE ON veterinarian_availability_events
    FOR EACH ROW EXECUTE FUNCTION reject_veterinarian_availability_event_mutation();
