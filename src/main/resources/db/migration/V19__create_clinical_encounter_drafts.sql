CREATE TABLE clinical_encounters (
    id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL REFERENCES appointments(id),
    pet_id UUID NOT NULL REFERENCES pets(id),
    owner_id UUID NOT NULL REFERENCES owner_profiles(user_id),
    veterinarian_id UUID NOT NULL REFERENCES veterinarian_profiles(user_id),
    created_by UUID NOT NULL REFERENCES veterinarian_profiles(user_id),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,
    reason VARCHAR(4000),
    anamnesis VARCHAR(4000),
    physical_examination VARCHAR(4000),
    diagnosis VARCHAR(4000),
    care_plan VARCHAR(4000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finalized_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_clinical_encounter_appointment UNIQUE (appointment_id),
    CONSTRAINT ck_clinical_encounter_status CHECK (status IN ('DRAFT', 'FINALIZED')),
    CONSTRAINT ck_clinical_encounter_version CHECK (version >= 0),
    CONSTRAINT ck_clinical_encounter_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_clinical_encounter_reason CHECK (reason IS NULL OR BTRIM(reason) <> ''),
    CONSTRAINT ck_clinical_encounter_anamnesis CHECK (anamnesis IS NULL OR BTRIM(anamnesis) <> ''),
    CONSTRAINT ck_clinical_encounter_examination CHECK (physical_examination IS NULL OR BTRIM(physical_examination) <> ''),
    CONSTRAINT ck_clinical_encounter_diagnosis CHECK (diagnosis IS NULL OR BTRIM(diagnosis) <> ''),
    CONSTRAINT ck_clinical_encounter_plan CHECK (care_plan IS NULL OR BTRIM(care_plan) <> ''),
    CONSTRAINT ck_clinical_encounter_finalization CHECK
        ((status = 'DRAFT' AND finalized_at IS NULL)
         OR (status = 'FINALIZED' AND finalized_at IS NOT NULL AND finalized_at >= created_at
             AND reason IS NOT NULL AND anamnesis IS NOT NULL AND physical_examination IS NOT NULL
             AND diagnosis IS NOT NULL AND care_plan IS NOT NULL))
);

CREATE INDEX ix_clinical_encounter_pet_history ON clinical_encounters(pet_id, finalized_at DESC, id)
    WHERE status = 'FINALIZED';
CREATE INDEX ix_clinical_encounter_veterinarian ON clinical_encounters(veterinarian_id, updated_at DESC, id);

CREATE TABLE clinical_encounter_events (
    id UUID PRIMARY KEY,
    encounter_id UUID NOT NULL REFERENCES clinical_encounters(id),
    encounter_version BIGINT NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    actor_id UUID NOT NULL REFERENCES veterinarian_profiles(user_id),
    previous_veterinarian_id UUID REFERENCES veterinarian_profiles(user_id),
    veterinarian_id UUID NOT NULL REFERENCES veterinarian_profiles(user_id),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_clinical_encounter_event_version UNIQUE (encounter_id, encounter_version),
    CONSTRAINT ck_clinical_encounter_event_type CHECK (event_type IN ('CREATED', 'UPDATED')),
    CONSTRAINT ck_clinical_encounter_event_shape CHECK
        ((event_type = 'CREATED' AND encounter_version = 0 AND previous_veterinarian_id IS NULL)
         OR (event_type = 'UPDATED' AND encounter_version > 0 AND previous_veterinarian_id IS NOT NULL)),
    CONSTRAINT ck_clinical_encounter_event_actor CHECK (actor_id = veterinarian_id)
);
CREATE INDEX ix_clinical_encounter_events_history ON clinical_encounter_events(encounter_id, occurred_at, id);

CREATE TABLE clinical_encounter_operations (
    id UUID PRIMARY KEY,
    encounter_id UUID NOT NULL REFERENCES clinical_encounters(id),
    actor_id UUID NOT NULL REFERENCES veterinarian_profiles(user_id),
    operation_type VARCHAR(16) NOT NULL,
    client_request_id UUID NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    resulting_version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_clinical_encounter_operation_request UNIQUE (actor_id, operation_type, client_request_id),
    CONSTRAINT ck_clinical_encounter_operation_type CHECK (operation_type IN ('CREATE', 'UPDATE')),
    CONSTRAINT ck_clinical_encounter_operation_version CHECK (resulting_version >= 0)
);

CREATE FUNCTION reject_clinical_history_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Clinical history is immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_clinical_encounter_events_immutable
    BEFORE UPDATE OR DELETE ON clinical_encounter_events
    FOR EACH ROW EXECUTE FUNCTION reject_clinical_history_mutation();
CREATE TRIGGER tr_clinical_encounter_operations_immutable
    BEFORE UPDATE OR DELETE ON clinical_encounter_operations
    FOR EACH ROW EXECUTE FUNCTION reject_clinical_history_mutation();

CREATE FUNCTION validate_clinical_encounter_snapshot() RETURNS trigger AS $$
DECLARE
    selected_appointment appointments%ROWTYPE;
    assigned_veterinarian UUID;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Clinical encounters cannot be deleted';
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF OLD.status = 'FINALIZED' THEN
            RAISE EXCEPTION 'Finalized clinical encounters are immutable';
        END IF;
        IF NEW.id <> OLD.id OR NEW.appointment_id <> OLD.appointment_id OR NEW.pet_id <> OLD.pet_id
            OR NEW.owner_id <> OLD.owner_id OR NEW.created_by <> OLD.created_by OR NEW.created_at <> OLD.created_at THEN
            RAISE EXCEPTION 'Clinical encounter identity and origin are immutable';
        END IF;
        IF NEW.version <> OLD.version + 1 THEN
            RAISE EXCEPTION 'Clinical encounter updates require the next version';
        END IF;
    ELSIF NEW.version <> 0 OR NEW.status <> 'DRAFT' OR NEW.created_by <> NEW.veterinarian_id THEN
        RAISE EXCEPTION 'Clinical encounters must begin as a new draft authored by their veterinarian';
    END IF;

    SELECT * INTO selected_appointment FROM appointments WHERE id = NEW.appointment_id;
    SELECT veterinarian_id INTO assigned_veterinarian FROM veterinarian_availability_slots
        WHERE id = selected_appointment.current_slot_id;
    IF selected_appointment.id IS NULL OR NEW.pet_id <> selected_appointment.pet_id
        OR NEW.owner_id <> selected_appointment.owner_id OR NEW.veterinarian_id <> assigned_veterinarian THEN
        RAISE EXCEPTION 'Clinical encounter snapshot does not match its appointment';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_clinical_encounter_snapshot
    BEFORE INSERT OR UPDATE OR DELETE ON clinical_encounters
    FOR EACH ROW EXECUTE FUNCTION validate_clinical_encounter_snapshot();
