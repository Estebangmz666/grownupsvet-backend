ALTER TABLE veterinarian_availability_events
    ADD CONSTRAINT ck_veterinarian_availability_event_intervals CHECK (
        new_ends_at = new_starts_at + INTERVAL '30 minutes'
        AND (previous_starts_at IS NULL OR previous_ends_at = previous_starts_at + INTERVAL '30 minutes')
    ),
    ADD CONSTRAINT ck_veterinarian_availability_event_transition CHECK (
        (event_type = 'CREATED' AND new_status = 'PUBLISHED')
        OR (event_type = 'RESCHEDULED' AND previous_status = new_status)
        OR (event_type = 'BLOCKED' AND previous_status = 'PUBLISHED' AND new_status = 'BLOCKED')
        OR (event_type = 'PUBLISHED' AND previous_status = 'BLOCKED' AND new_status = 'PUBLISHED')
    );
