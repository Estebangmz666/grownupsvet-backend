CREATE TABLE clinical_reminder_schedules (
    id UUID PRIMARY KEY,
    encounter_id UUID NOT NULL REFERENCES clinical_encounters(id),
    source_type VARCHAR(16) NOT NULL,
    prescription_id UUID,
    control_id UUID,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    interval_hours INTEGER,
    next_due_at TIMESTAMP WITH TIME ZONE,
    next_occurrence_index BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    missed_count BIGINT NOT NULL DEFAULT 0,
    last_missed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (encounter_id, prescription_id) REFERENCES clinical_prescriptions(encounter_id, id),
    FOREIGN KEY (encounter_id, control_id) REFERENCES clinical_controls(encounter_id, id),
    CONSTRAINT uk_clinical_reminder_prescription UNIQUE (encounter_id, prescription_id),
    CONSTRAINT uk_clinical_reminder_control UNIQUE (encounter_id, control_id),
    CONSTRAINT ck_clinical_reminder_source CHECK
        ((source_type='PRESCRIPTION' AND prescription_id IS NOT NULL AND control_id IS NULL AND interval_hours IS NOT NULL AND interval_hours BETWEEN 1 AND 168)
         OR (source_type='CONTROL' AND control_id IS NOT NULL AND prescription_id IS NULL AND interval_hours IS NULL)),
    CONSTRAINT ck_clinical_reminder_window CHECK (ends_at > starts_at),
    CONSTRAINT ck_clinical_reminder_state CHECK
        ((status='ACTIVE' AND next_due_at IS NOT NULL AND next_due_at >= starts_at AND next_due_at < ends_at)
         OR (status IN ('FINISHED','SUPERSEDED') AND next_due_at IS NULL)),
    CONSTRAINT ck_clinical_reminder_cursor CHECK (next_occurrence_index >= 0 AND missed_count >= 0)
);
CREATE INDEX ix_clinical_reminder_schedule_due ON clinical_reminder_schedules(next_due_at,id) WHERE status='ACTIVE';

CREATE TABLE clinical_reminder_email_tasks (
    id UUID PRIMARY KEY,
    schedule_id UUID NOT NULL REFERENCES clinical_reminder_schedules(id),
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recipient_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lease_token UUID,
    lease_until TIMESTAMP WITH TIME ZONE,
    last_error_code VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_clinical_reminder_occurrence UNIQUE (schedule_id,scheduled_at),
    CONSTRAINT ck_clinical_reminder_task_window CHECK (expires_at > scheduled_at),
    CONSTRAINT ck_clinical_reminder_task_state CHECK (status IN ('PENDING','SENDING','SENT','FAILED','SUPERSEDED','EXPIRED')),
    CONSTRAINT ck_clinical_reminder_task_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_clinical_reminder_task_lease CHECK
        ((status='SENDING' AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
         OR (status<>'SENDING' AND lease_token IS NULL AND lease_until IS NULL))
);
CREATE INDEX ix_clinical_reminder_task_due ON clinical_reminder_email_tasks(next_attempt_at,id)
    WHERE status IN ('PENDING','SENDING');

CREATE FUNCTION validate_clinical_reminder_origin() RETURNS trigger AS $$
DECLARE
    encounter_status VARCHAR(16);
    appointment_status VARCHAR(16);
    source_starts_at TIMESTAMP WITH TIME ZONE;
    source_ends_at TIMESTAMP WITH TIME ZONE;
    source_interval_hours INTEGER;
BEGIN
    IF TG_OP='UPDATE' AND (NEW.id<>OLD.id OR NEW.encounter_id<>OLD.encounter_id OR NEW.source_type<>OLD.source_type
        OR NEW.prescription_id IS DISTINCT FROM OLD.prescription_id OR NEW.control_id IS DISTINCT FROM OLD.control_id
        OR NEW.starts_at<>OLD.starts_at OR NEW.ends_at<>OLD.ends_at OR NEW.interval_hours IS DISTINCT FROM OLD.interval_hours
        OR NEW.created_at<>OLD.created_at) THEN
        RAISE EXCEPTION 'Clinical reminder origin and timing are immutable';
    END IF;
    IF TG_OP='INSERT' THEN
        SELECT e.status,a.status INTO encounter_status,appointment_status
            FROM clinical_encounters e JOIN appointments a ON a.id=e.appointment_id WHERE e.id=NEW.encounter_id;
        IF encounter_status IS DISTINCT FROM 'FINALIZED' OR appointment_status IS DISTINCT FROM 'COMPLETED' THEN
            RAISE EXCEPTION 'Clinical reminders require a finalized encounter and completed appointment';
        END IF;
        IF NEW.source_type='PRESCRIPTION' THEN
            SELECT starts_at,ends_at,interval_hours INTO source_starts_at,source_ends_at,source_interval_hours
                FROM clinical_prescriptions WHERE encounter_id=NEW.encounter_id AND id=NEW.prescription_id;
            IF NEW.starts_at IS DISTINCT FROM source_starts_at OR NEW.ends_at IS DISTINCT FROM source_ends_at
                OR NEW.interval_hours IS DISTINCT FROM source_interval_hours THEN
                RAISE EXCEPTION 'Clinical reminder timing must match the prescription';
            END IF;
        ELSIF NEW.source_type='CONTROL' THEN
            SELECT scheduled_at INTO source_ends_at FROM clinical_controls WHERE encounter_id=NEW.encounter_id AND id=NEW.control_id;
            source_starts_at := source_ends_at - INTERVAL '24 hours';
            IF NEW.created_at > source_starts_at AND NEW.created_at < source_ends_at THEN
                source_starts_at := NEW.created_at;
            END IF;
            IF NEW.ends_at IS DISTINCT FROM source_ends_at OR NEW.starts_at IS DISTINCT FROM source_starts_at THEN
                RAISE EXCEPTION 'Clinical reminder timing must match the control';
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER tr_clinical_reminder_origin BEFORE INSERT OR UPDATE ON clinical_reminder_schedules
    FOR EACH ROW EXECUTE FUNCTION validate_clinical_reminder_origin();
