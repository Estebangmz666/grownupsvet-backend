ALTER TABLE appointments DROP CONSTRAINT ck_appointment_status;
ALTER TABLE appointments ADD CONSTRAINT ck_appointment_status
    CHECK (status IN ('REQUESTED', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'NO_SHOW'));

ALTER TABLE appointment_events DROP CONSTRAINT ck_appointment_event_type;
ALTER TABLE appointment_events ADD CONSTRAINT ck_appointment_event_type CHECK (event_type IN
    ('REQUESTED', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'NO_SHOW', 'RESCHEDULED', 'DAILY_CUTOFF',
     'VETERINARIAN_DISABLED', 'VETERINARIAN_RESTORED', 'REASSIGNED', 'EMAIL_ACCESSED'));

ALTER TABLE appointment_events DROP CONSTRAINT ck_appointment_event_status;
ALTER TABLE appointment_events ADD CONSTRAINT ck_appointment_event_status CHECK
    ((previous_status IS NULL OR previous_status IN ('REQUESTED', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'NO_SHOW'))
     AND (new_status IS NULL OR new_status IN ('REQUESTED', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'NO_SHOW')));

ALTER TABLE appointment_events DROP CONSTRAINT ck_appointment_event_agreement;
ALTER TABLE appointment_events ADD CONSTRAINT ck_appointment_event_agreement CHECK
    ((event_type <> 'RESCHEDULED' AND agreement_channel IS NULL AND agreement_contacted_at IS NULL AND agreement_note IS NULL)
     OR (agreement_channel IS NOT NULL AND agreement_channel IN ('PHONE', 'WHATSAPP', 'OTHER')
         AND agreement_contacted_at IS NOT NULL AND agreement_note IS NOT NULL AND BTRIM(agreement_note) <> ''));

ALTER TABLE appointment_events ADD CONSTRAINT ck_appointment_administrative_event CHECK
    (event_type NOT IN ('CANCELLED', 'NO_SHOW', 'RESCHEDULED')
     OR (actor_type = 'HUMAN' AND reason IS NOT NULL AND BTRIM(reason) <> ''
         AND previous_status IS NOT NULL AND new_status IS NOT NULL
         AND ((event_type = 'CANCELLED' AND previous_status IN ('REQUESTED', 'CONFIRMED') AND new_status = 'CANCELLED')
              OR (event_type = 'NO_SHOW' AND previous_status = 'CONFIRMED' AND new_status = 'NO_SHOW')
              OR (event_type = 'RESCHEDULED' AND previous_status IN ('REQUESTED', 'CONFIRMED')
                  AND new_status = previous_status AND previous_slot_id IS NOT NULL AND new_slot_id IS NOT NULL
                  AND previous_slot_id <> new_slot_id))));

ALTER TABLE appointment_operation_idempotency DROP CONSTRAINT ck_appointment_operation_type;
ALTER TABLE appointment_operation_idempotency ADD CONSTRAINT ck_appointment_operation_type
    CHECK (operation_type IN ('REASSIGNMENT', 'RESCHEDULING'));

-- Active-slot and pet-interval indexes, snapshot checks and immutable-history triggers remain in force.
