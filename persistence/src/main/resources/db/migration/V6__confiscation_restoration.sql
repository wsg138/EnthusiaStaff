ALTER TABLE confiscated_asset_snapshots
    ADD COLUMN restoration_operation_id BINARY(16) NULL AFTER expires_at,
    ADD COLUMN restoration_state VARCHAR(32) NULL AFTER restoration_operation_id,
    ADD COLUMN restoration_reserved_at TIMESTAMP(6) NULL AFTER restoration_state,
    ADD COLUMN restored_checksum CHAR(64) NULL AFTER restored_at;

CREATE INDEX idx_confiscated_restoration
    ON confiscated_asset_snapshots(case_id, restoration_state, expires_at);
