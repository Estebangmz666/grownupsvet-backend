ALTER TABLE clinical_encounter_events ALTER COLUMN event_type TYPE VARCHAR(32);
ALTER TABLE clinical_encounter_events DROP CONSTRAINT ck_clinical_encounter_event_type;
ALTER TABLE clinical_encounter_events ADD CONSTRAINT ck_clinical_encounter_event_type
    CHECK (event_type IN ('CREATED', 'UPDATED', 'TREATMENT_UPDATED'));
ALTER TABLE clinical_encounter_events DROP CONSTRAINT ck_clinical_encounter_event_shape;
ALTER TABLE clinical_encounter_events ADD CONSTRAINT ck_clinical_encounter_event_shape CHECK
    ((event_type = 'CREATED' AND encounter_version = 0 AND previous_veterinarian_id IS NULL)
     OR (event_type IN ('UPDATED', 'TREATMENT_UPDATED') AND encounter_version > 0 AND previous_veterinarian_id IS NOT NULL));
ALTER TABLE clinical_encounter_operations DROP CONSTRAINT ck_clinical_encounter_operation_type;
ALTER TABLE clinical_encounter_operations ADD CONSTRAINT ck_clinical_encounter_operation_type
    CHECK (operation_type IN ('CREATE', 'UPDATE', 'TREATMENT'));

CREATE TABLE clinical_prescriptions (
    encounter_id UUID NOT NULL REFERENCES clinical_encounters(id),
    id UUID NOT NULL,
    position INTEGER NOT NULL,
    medication_name VARCHAR(200) NOT NULL,
    dose NUMERIC(18,6) NOT NULL,
    dose_unit VARCHAR(50) NOT NULL,
    administration_route VARCHAR(100) NOT NULL,
    interval_hours INTEGER NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    instructions VARCHAR(2000),
    PRIMARY KEY (encounter_id, id),
    CONSTRAINT uk_clinical_prescription_position UNIQUE (encounter_id, position),
    CONSTRAINT ck_clinical_prescription_position CHECK (position BETWEEN 0 AND 49),
    CONSTRAINT ck_clinical_prescription_name CHECK (BTRIM(medication_name) <> ''),
    CONSTRAINT ck_clinical_prescription_dose CHECK (dose > 0),
    CONSTRAINT ck_clinical_prescription_unit CHECK (BTRIM(dose_unit) <> ''),
    CONSTRAINT ck_clinical_prescription_route CHECK (BTRIM(administration_route) <> ''),
    CONSTRAINT ck_clinical_prescription_interval CHECK (interval_hours BETWEEN 1 AND 168),
    CONSTRAINT ck_clinical_prescription_window CHECK
        (ends_at > starts_at AND ends_at <= starts_at + INTERVAL '8784 hours'),
    CONSTRAINT ck_clinical_prescription_instructions CHECK (instructions IS NULL OR BTRIM(instructions) <> '')
);

CREATE TABLE clinical_controls (
    encounter_id UUID NOT NULL REFERENCES clinical_encounters(id),
    id UUID NOT NULL,
    position INTEGER NOT NULL,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    PRIMARY KEY (encounter_id, id),
    CONSTRAINT uk_clinical_control_position UNIQUE (encounter_id, position),
    CONSTRAINT ck_clinical_control_position CHECK (position BETWEEN 0 AND 49),
    CONSTRAINT ck_clinical_control_reason CHECK (BTRIM(reason) <> '')
);

CREATE FUNCTION protect_clinical_treatment_mutation() RETURNS trigger AS $$
DECLARE
    selected_encounter UUID;
    encounter_status VARCHAR(16);
BEGIN
    IF TG_OP = 'DELETE' THEN
        selected_encounter := OLD.encounter_id;
    ELSE
        selected_encounter := NEW.encounter_id;
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.encounter_id <> OLD.encounter_id THEN
        RAISE EXCEPTION 'Clinical treatment cannot move between encounters';
    END IF;
    -- Serialize direct child writes against finalization as well as service writes.
    SELECT status INTO encounter_status FROM clinical_encounters WHERE id = selected_encounter FOR UPDATE;
    IF encounter_status IS NULL OR encounter_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Clinical treatment requires a mutable draft';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_clinical_prescription_mutation
    BEFORE INSERT OR UPDATE OR DELETE ON clinical_prescriptions
    FOR EACH ROW EXECUTE FUNCTION protect_clinical_treatment_mutation();
CREATE TRIGGER tr_clinical_control_mutation
    BEFORE INSERT OR UPDATE OR DELETE ON clinical_controls
    FOR EACH ROW EXECUTE FUNCTION protect_clinical_treatment_mutation();
