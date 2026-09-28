CREATE TABLE appointments (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    pet_id UUID NOT NULL REFERENCES pets(id),
    current_slot_id UUID NOT NULL REFERENCES veterinarian_availability_slots(id),
    status VARCHAR(16) NOT NULL,
    assignment_status VARCHAR(24) NOT NULL DEFAULT 'ASSIGNED',
    reason VARCHAR(1000) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmation_cutoff_at TIMESTAMP WITH TIME ZONE NOT NULL,
    client_request_id UUID NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_appointment_owner_request UNIQUE (owner_id, client_request_id),
    CONSTRAINT ck_appointment_status CHECK (status IN ('REQUESTED', 'CONFIRMED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_appointment_assignment_status CHECK (assignment_status IN ('ASSIGNED', 'NEEDS_REASSIGNMENT')),
    CONSTRAINT ck_appointment_duration CHECK (ends_at = starts_at + INTERVAL '30 minutes'),
    CONSTRAINT ck_appointment_reason CHECK (BTRIM(reason) <> ''),
    CONSTRAINT ck_appointment_version CHECK (version >= 0),
    CONSTRAINT ck_appointment_timestamps CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX uk_appointment_active_slot
    ON appointments(current_slot_id) WHERE status IN ('REQUESTED', 'CONFIRMED');
CREATE UNIQUE INDEX uk_appointment_active_pet_interval
    ON appointments(pet_id, starts_at) WHERE status IN ('REQUESTED', 'CONFIRMED');
CREATE INDEX ix_appointment_owner_start ON appointments(owner_id, starts_at, id);
CREATE INDEX ix_appointment_slot_start ON appointments(current_slot_id, starts_at, id);
CREATE INDEX ix_appointment_cutoff ON appointments(confirmation_cutoff_at, status, id);

CREATE TABLE appointment_assignments (
    id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL REFERENCES appointments(id),
    slot_id UUID NOT NULL REFERENCES veterinarian_availability_slots(id),
    veterinarian_id UUID NOT NULL REFERENCES veterinarian_profiles(user_id),
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_id UUID REFERENCES users(id),
    actor_type VARCHAR(8) NOT NULL,
    reason VARCHAR(1000),
    CONSTRAINT ck_appointment_assignment_actor CHECK
        ((actor_type = 'SYSTEM' AND actor_id IS NULL) OR (actor_type = 'HUMAN' AND actor_id IS NOT NULL)),
    CONSTRAINT ck_appointment_assignment_duration CHECK (ends_at = starts_at + INTERVAL '30 minutes')
);
CREATE INDEX ix_appointment_assignments_slot ON appointment_assignments(slot_id);
CREATE INDEX ix_appointment_assignments_appointment ON appointment_assignments(appointment_id, assigned_at, id);

CREATE TABLE appointment_events (
    id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL REFERENCES appointments(id),
    appointment_version BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    previous_status VARCHAR(16),
    new_status VARCHAR(16),
    previous_assignment_status VARCHAR(24),
    new_assignment_status VARCHAR(24),
    previous_slot_id UUID REFERENCES veterinarian_availability_slots(id),
    new_slot_id UUID REFERENCES veterinarian_availability_slots(id),
    actor_id UUID REFERENCES users(id),
    actor_type VARCHAR(8) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reason VARCHAR(1000),
    agreement_channel VARCHAR(24),
    agreement_contacted_at TIMESTAMP WITH TIME ZONE,
    agreement_note VARCHAR(500),
    CONSTRAINT uk_appointment_event_version UNIQUE (appointment_id, appointment_version),
    CONSTRAINT ck_appointment_event_actor CHECK
        ((actor_type = 'SYSTEM' AND actor_id IS NULL) OR (actor_type = 'HUMAN' AND actor_id IS NOT NULL)),
    CONSTRAINT ck_appointment_event_type CHECK (event_type IN
        ('REQUESTED', 'CONFIRMED', 'REJECTED', 'DAILY_CUTOFF', 'VETERINARIAN_DISABLED', 'REASSIGNED', 'EMAIL_ACCESSED')),
    CONSTRAINT ck_appointment_event_status CHECK
        ((previous_status IS NULL OR previous_status IN ('REQUESTED', 'CONFIRMED', 'REJECTED', 'CANCELLED'))
         AND (new_status IS NULL OR new_status IN ('REQUESTED', 'CONFIRMED', 'REJECTED', 'CANCELLED'))),
    CONSTRAINT ck_appointment_event_agreement CHECK
        ((agreement_channel IS NULL AND agreement_contacted_at IS NULL AND agreement_note IS NULL)
         OR (agreement_channel IN ('PHONE', 'WHATSAPP', 'OTHER') AND agreement_contacted_at IS NOT NULL
             AND agreement_note IS NOT NULL AND BTRIM(agreement_note) <> ''))
);
CREATE INDEX ix_appointment_events_history ON appointment_events(appointment_id, occurred_at, id);

CREATE TABLE appointment_operation_idempotency (
    id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL REFERENCES appointments(id),
    operation_type VARCHAR(24) NOT NULL,
    client_request_id UUID NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    resulting_version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_appointment_operation_request UNIQUE (appointment_id, operation_type, client_request_id),
    CONSTRAINT ck_appointment_operation_type CHECK (operation_type IN ('REASSIGNMENT')),
    CONSTRAINT ck_appointment_operation_version CHECK (resulting_version > 0)
);

CREATE TABLE appointment_email_access_audits (
    id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL REFERENCES appointments(id),
    veterinarian_id UUID NOT NULL REFERENCES users(id),
    accessed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reason VARCHAR(500) NOT NULL,
    CONSTRAINT ck_appointment_email_access_reason CHECK (BTRIM(reason) <> '')
);
CREATE INDEX ix_appointment_email_access_history ON appointment_email_access_audits(appointment_id, accessed_at);

CREATE TABLE appointment_email_tasks (
    id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL REFERENCES appointments(id),
    event_key UUID NOT NULL,
    recipient_id UUID REFERENCES users(id),
    recipient_email VARCHAR(254),
    task_type VARCHAR(32) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error_code VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_appointment_email_event_recipient UNIQUE (event_key, recipient_id, task_type),
    CONSTRAINT ck_appointment_email_task_type CHECK (task_type IN ('ADMIN_REASSIGNMENT', 'OWNER_REASSIGNED')),
    CONSTRAINT ck_appointment_email_task_status CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_appointment_email_task_attempts CHECK (attempt_count >= 0)
);
CREATE INDEX ix_appointment_email_tasks_pending ON appointment_email_tasks(next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'SENDING');

CREATE FUNCTION reject_appointment_history_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Appointment history is immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_appointment_events_immutable
    BEFORE UPDATE OR DELETE ON appointment_events
    FOR EACH ROW EXECUTE FUNCTION reject_appointment_history_mutation();
CREATE TRIGGER tr_appointment_assignments_immutable
    BEFORE UPDATE OR DELETE ON appointment_assignments
    FOR EACH ROW EXECUTE FUNCTION reject_appointment_history_mutation();
CREATE TRIGGER tr_appointment_email_audits_immutable
    BEFORE UPDATE OR DELETE ON appointment_email_access_audits
    FOR EACH ROW EXECUTE FUNCTION reject_appointment_history_mutation();

CREATE FUNCTION validate_appointment_slot_snapshot() RETURNS trigger AS $$
DECLARE
    selected_slot veterinarian_availability_slots%ROWTYPE;
BEGIN
    SELECT * INTO selected_slot FROM veterinarian_availability_slots WHERE id = NEW.current_slot_id;
    IF selected_slot.id IS NULL OR selected_slot.starts_at <> NEW.starts_at OR selected_slot.ends_at <> NEW.ends_at THEN
        RAISE EXCEPTION 'Appointment slot snapshot does not match availability';
    END IF;
    IF NEW.status IN ('REQUESTED', 'CONFIRMED') AND selected_slot.status <> 'PUBLISHED' THEN
        RAISE EXCEPTION 'An active appointment requires a published slot';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER tr_appointment_slot_snapshot
    BEFORE INSERT OR UPDATE OF current_slot_id, starts_at, ends_at, status ON appointments
    FOR EACH ROW EXECUTE FUNCTION validate_appointment_slot_snapshot();

CREATE FUNCTION protect_appointment_referenced_slots() RETURNS trigger AS $$
BEGIN
    IF NEW.starts_at <> OLD.starts_at OR NEW.ends_at <> OLD.ends_at THEN
        IF EXISTS (SELECT 1 FROM appointment_assignments WHERE slot_id = OLD.id) THEN
            RAISE EXCEPTION 'An appointment referenced this availability slot';
        END IF;
    END IF;
    IF NEW.status = 'BLOCKED' AND OLD.status <> 'BLOCKED' AND EXISTS
        (SELECT 1 FROM appointments WHERE current_slot_id = OLD.id AND status IN ('REQUESTED', 'CONFIRMED')) THEN
        RAISE EXCEPTION 'An active appointment occupies this availability slot';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER tr_protect_appointment_referenced_slots
    BEFORE UPDATE ON veterinarian_availability_slots
    FOR EACH ROW EXECUTE FUNCTION protect_appointment_referenced_slots();
