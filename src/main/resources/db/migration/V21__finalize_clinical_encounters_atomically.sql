ALTER TABLE appointments DROP CONSTRAINT ck_appointment_status;
ALTER TABLE appointments ADD CONSTRAINT ck_appointment_status
    CHECK (status IN ('REQUESTED', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'NO_SHOW', 'COMPLETED'));
ALTER TABLE appointment_events DROP CONSTRAINT ck_appointment_event_type;
ALTER TABLE appointment_events ADD CONSTRAINT ck_appointment_event_type CHECK (event_type IN
    ('REQUESTED','CONFIRMED','REJECTED','CANCELLED','NO_SHOW','RESCHEDULED','DAILY_CUTOFF',
     'VETERINARIAN_DISABLED','VETERINARIAN_RESTORED','REASSIGNED','EMAIL_ACCESSED','COMPLETED'));
ALTER TABLE appointment_events DROP CONSTRAINT ck_appointment_event_status;
ALTER TABLE appointment_events ADD CONSTRAINT ck_appointment_event_status CHECK
    ((previous_status IS NULL OR previous_status IN ('REQUESTED','CONFIRMED','REJECTED','CANCELLED','NO_SHOW','COMPLETED'))
     AND (new_status IS NULL OR new_status IN ('REQUESTED','CONFIRMED','REJECTED','CANCELLED','NO_SHOW','COMPLETED')));
ALTER TABLE appointment_events ADD CONSTRAINT ck_appointment_clinical_completion CHECK
    (event_type <> 'COMPLETED' OR (actor_type='HUMAN' AND actor_id IS NOT NULL
        AND previous_status IS NOT NULL AND previous_status='CONFIRMED'
        AND new_status IS NOT NULL AND new_status='COMPLETED'
        AND previous_assignment_status IS NOT NULL AND previous_assignment_status='ASSIGNED'
        AND new_assignment_status IS NOT NULL AND new_assignment_status='ASSIGNED'));

ALTER TABLE clinical_encounter_events DROP CONSTRAINT ck_clinical_encounter_event_type;
ALTER TABLE clinical_encounter_events ADD CONSTRAINT ck_clinical_encounter_event_type
    CHECK (event_type IN ('CREATED','UPDATED','TREATMENT_UPDATED','FINALIZED'));
ALTER TABLE clinical_encounter_events DROP CONSTRAINT ck_clinical_encounter_event_shape;
ALTER TABLE clinical_encounter_events ADD CONSTRAINT ck_clinical_encounter_event_shape CHECK
    ((event_type='CREATED' AND encounter_version=0 AND previous_veterinarian_id IS NULL)
     OR (event_type IN ('UPDATED','TREATMENT_UPDATED','FINALIZED') AND encounter_version>0 AND previous_veterinarian_id IS NOT NULL));
ALTER TABLE clinical_encounter_operations DROP CONSTRAINT ck_clinical_encounter_operation_type;
ALTER TABLE clinical_encounter_operations ADD CONSTRAINT ck_clinical_encounter_operation_type
    CHECK (operation_type IN ('CREATE','UPDATE','TREATMENT','FINALIZE'));

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM appointments a LEFT JOIN clinical_encounters e ON e.appointment_id=a.id
        WHERE (a.status='COMPLETED') IS DISTINCT FROM COALESCE(e.status='FINALIZED',FALSE)) THEN
        RAISE EXCEPTION 'Existing clinical finalization and appointment completion are inconsistent';
    END IF;
END;
$$;

-- Deferred checks permit either write order within a transaction but never a partial commit.
CREATE FUNCTION validate_clinical_completion_consistency() RETURNS trigger AS $$
DECLARE
    target_appointment UUID;
    appointment_completed BOOLEAN;
    encounter_finalized BOOLEAN;
BEGIN
    IF TG_TABLE_NAME='appointments' THEN
        target_appointment := NEW.id;
    ELSE
        target_appointment := NEW.appointment_id;
    END IF;
    SELECT status='COMPLETED' INTO appointment_completed FROM appointments WHERE id=target_appointment;
    SELECT EXISTS(SELECT 1 FROM clinical_encounters WHERE appointment_id=target_appointment AND status='FINALIZED')
        INTO encounter_finalized;
    IF appointment_completed IS DISTINCT FROM encounter_finalized THEN
        RAISE EXCEPTION 'Clinical finalization and appointment completion must commit together';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
CREATE CONSTRAINT TRIGGER tr_clinical_completion_encounter
    AFTER INSERT OR UPDATE ON clinical_encounters DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_clinical_completion_consistency();
CREATE CONSTRAINT TRIGGER tr_clinical_completion_appointment
    AFTER INSERT OR UPDATE ON appointments DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_clinical_completion_consistency();
