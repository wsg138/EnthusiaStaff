-- A case-message row can be a merged reporter submission or an internal staff action.
-- Historical entries cannot be classified reliably from actor UUID alone
-- (a reporter may also be staff); leave existing rows UNKNOWN for review.
ALTER TABLE report_messages
    ADD COLUMN origin ENUM('UNKNOWN', 'REPORTER_FOLLOWUP', 'STAFF_ACTION')
    NOT NULL DEFAULT 'UNKNOWN';
