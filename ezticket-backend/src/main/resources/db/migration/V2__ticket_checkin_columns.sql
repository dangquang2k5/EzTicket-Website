-- ==========================================================
-- FLYWAY MIGRATION SCRIPT: V2__ticket_checkin_columns.sql
-- SCRUM-29 [GĐ2] — C-05: columns for gate check-in.
-- V1 is NOT modified; only additive columns here.
-- ==========================================================

ALTER TABLE tickets ADD COLUMN IF NOT EXISTS check_in_gate VARCHAR(50);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS checked_in_by BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_tickets_users_checkin'
    ) THEN
        ALTER TABLE tickets
            ADD CONSTRAINT fk_tickets_users_checkin
            FOREIGN KEY (checked_in_by) REFERENCES users (id) ON DELETE SET NULL;
    END IF;
END $$;
