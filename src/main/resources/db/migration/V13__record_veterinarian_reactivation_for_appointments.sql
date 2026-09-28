ALTER TABLE appointment_events DROP CONSTRAINT ck_appointment_event_type;
ALTER TABLE appointment_events ADD CONSTRAINT ck_appointment_event_type CHECK (event_type IN
    ('REQUESTED', 'CONFIRMED', 'REJECTED', 'DAILY_CUTOFF', 'VETERINARIAN_DISABLED', 'VETERINARIAN_RESTORED', 'REASSIGNED', 'EMAIL_ACCESSED'));
