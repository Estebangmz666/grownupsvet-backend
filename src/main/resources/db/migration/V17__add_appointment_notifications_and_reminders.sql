ALTER TABLE appointment_email_tasks ADD COLUMN scheduled_at TIMESTAMP WITH TIME ZONE;
UPDATE appointment_email_tasks SET scheduled_at = created_at;
ALTER TABLE appointment_email_tasks ALTER COLUMN scheduled_at SET NOT NULL;
ALTER TABLE appointment_email_tasks ALTER COLUMN scheduled_at SET DEFAULT clock_timestamp();

ALTER TABLE appointment_email_tasks DROP CONSTRAINT ck_appointment_email_task_type;
ALTER TABLE appointment_email_tasks ADD CONSTRAINT ck_appointment_email_task_type CHECK (task_type IN
    ('ADMIN_REASSIGNMENT', 'OWNER_REASSIGNED', 'OWNER_CONFIRMED', 'OWNER_REJECTED',
     'OWNER_CANCELLED', 'OWNER_RESCHEDULED', 'OWNER_REMINDER'));
CREATE INDEX ix_appointment_email_recipient_history
    ON appointment_email_tasks(appointment_id, recipient_id, created_at, id);

-- A future reminder is an ordinary durable task. Its scheduled_at never becomes a lease or retry time.
-- The existing event/recipient/type uniqueness and lease constraint remain authoritative.
-- The default permits legacy immediate-notice writers during an application revision change.
