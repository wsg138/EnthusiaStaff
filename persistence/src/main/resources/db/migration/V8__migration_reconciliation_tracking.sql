ALTER TABLE migration_mappings
    ADD COLUMN last_seen_run_id BINARY(16) NULL AFTER run_id,
    ADD COLUMN last_seen_at TIMESTAMP(6) NULL AFTER created_at;

UPDATE migration_mappings
SET last_seen_run_id = run_id,
    last_seen_at = created_at
WHERE last_seen_run_id IS NULL OR last_seen_at IS NULL;

ALTER TABLE migration_mappings
    MODIFY last_seen_run_id BINARY(16) NOT NULL,
    MODIFY last_seen_at TIMESTAMP(6) NOT NULL,
    ADD INDEX idx_migration_mapping_last_seen (last_seen_run_id, last_seen_at),
    ADD CONSTRAINT fk_migration_mapping_last_seen_run
        FOREIGN KEY (last_seen_run_id) REFERENCES migration_runs(run_id);
