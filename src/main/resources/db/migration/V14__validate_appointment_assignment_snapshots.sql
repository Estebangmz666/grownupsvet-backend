CREATE FUNCTION validate_appointment_assignment_snapshot() RETURNS trigger AS $$
DECLARE
    selected_slot veterinarian_availability_slots%ROWTYPE;
BEGIN
    SELECT * INTO selected_slot FROM veterinarian_availability_slots WHERE id = NEW.slot_id;
    IF selected_slot.id IS NULL OR selected_slot.veterinarian_id <> NEW.veterinarian_id
        OR selected_slot.starts_at <> NEW.starts_at OR selected_slot.ends_at <> NEW.ends_at THEN
        RAISE EXCEPTION 'Appointment assignment snapshot does not match availability';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_validate_appointment_assignment_snapshot
    BEFORE INSERT ON appointment_assignments
    FOR EACH ROW EXECUTE FUNCTION validate_appointment_assignment_snapshot();
