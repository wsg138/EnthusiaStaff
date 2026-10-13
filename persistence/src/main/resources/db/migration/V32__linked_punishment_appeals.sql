-- Preserve every appeal attempt and its review history; submissions serialize on the sanction row.
ALTER TABLE website_appeal_requests
    DROP INDEX uq_website_appeal_punishment,
    MODIFY COLUMN player_username VARCHAR(32) NULL,
    ADD INDEX idx_website_appeal_attempts (punishment_id, created_at, appeal_id);
