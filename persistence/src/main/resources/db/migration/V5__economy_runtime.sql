ALTER TABLE economy_operations
    MODIFY COLUMN requested_amount BIGINT UNSIGNED NULL,
    MODIFY COLUMN authoritative_total BIGINT UNSIGNED NULL,
    MODIFY COLUMN plan_json JSON NULL,
    MODIFY COLUMN before_snapshot JSON NULL,
    ADD COLUMN actor_id BINARY(16) NULL AFTER target_id,
    ADD COLUMN amount_mode VARCHAR(16) NOT NULL DEFAULT 'CUSTOM' AFTER actor_id,
    ADD COLUMN owning_server_id VARCHAR(64) NULL AFTER amount_mode,
    ADD COLUMN lease_owner VARCHAR(128) NULL AFTER state,
    ADD COLUMN lease_until TIMESTAMP(6) NULL AFTER lease_owner,
    ADD COLUMN fencing_token BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER lease_until,
    ADD COLUMN terminal_outcome VARCHAR(32) NULL AFTER fencing_token,
    ADD COLUMN before_checksum CHAR(64) NULL AFTER before_snapshot,
    ADD COLUMN replacement_checksum CHAR(64) NULL AFTER before_checksum,
    ADD COLUMN result_total BIGINT UNSIGNED NULL AFTER replacement_checksum,
    ADD COLUMN result_checksum CHAR(64) NULL AFTER result_total,
    ADD COLUMN result_snapshot JSON NULL AFTER result_checksum,
    ADD COLUMN failure_code VARCHAR(64) NULL AFTER result_snapshot,
    ADD COLUMN failure_detail VARCHAR(1024) NULL AFTER failure_code,
    ADD COLUMN committed_at TIMESTAMP(6) NULL AFTER failure_detail,
    ADD COLUMN released_at TIMESTAMP(6) NULL AFTER committed_at;

CREATE INDEX idx_economy_target_state
    ON economy_operations(target_id, state, created_at);

CREATE INDEX idx_economy_server_recovery
    ON economy_operations(owning_server_id, state, updated_at);

CREATE TABLE IF NOT EXISTS economy_operation_events (
    sequence_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id BINARY(16) NOT NULL,
    operation_id BINARY(16) NOT NULL,
    state VARCHAR(48) NOT NULL,
    fencing_token BIGINT UNSIGNED NOT NULL,
    event_json JSON NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (sequence_id),
    UNIQUE KEY uq_economy_event_id (event_id),
    INDEX idx_economy_event_operation (operation_id, sequence_id),
    CONSTRAINT fk_economy_event_operation
        FOREIGN KEY (operation_id) REFERENCES economy_operations(operation_id)
) ENGINE=InnoDB;
