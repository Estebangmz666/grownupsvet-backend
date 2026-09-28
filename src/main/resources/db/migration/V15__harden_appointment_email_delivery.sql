ALTER TABLE appointment_email_tasks ADD COLUMN lease_token UUID;

-- Recover uncertain deliveries left by an older process with a fresh lease.
UPDATE appointment_email_tasks SET status = 'PENDING', next_attempt_at = CURRENT_TIMESTAMP
WHERE status = 'SENDING';

ALTER TABLE appointment_email_tasks DROP CONSTRAINT ck_appointment_email_task_status;
ALTER TABLE appointment_email_tasks ADD CONSTRAINT ck_appointment_email_task_status
    CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'SUPERSEDED'));
ALTER TABLE appointment_email_tasks ADD CONSTRAINT ck_appointment_email_task_lease
    CHECK ((status = 'SENDING' AND lease_token IS NOT NULL)
        OR (status <> 'SENDING' AND lease_token IS NULL));

CREATE INDEX ix_appointment_email_tasks_sending_appointment
    ON appointment_email_tasks(appointment_id) WHERE status = 'SENDING';
