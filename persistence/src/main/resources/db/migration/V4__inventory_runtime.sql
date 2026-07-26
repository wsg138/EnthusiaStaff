CREATE TABLE IF NOT EXISTS inventory_observations (
    profile_id BINARY(16) NOT NULL,
    revision BIGINT UNSIGNED NOT NULL,
    checksum CHAR(64) NOT NULL,
    snapshot_blob LONGBLOB NOT NULL,
    observed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (profile_id),
    CONSTRAINT fk_inventory_observation_profile
        FOREIGN KEY (profile_id) REFERENCES inventory_profiles(profile_id)
) ENGINE=InnoDB;

ALTER TABLE inventory_pending_patches
    ADD COLUMN expected_checksum CHAR(64) NULL AFTER expected_revision,
    ADD COLUMN replacement_checksum CHAR(64) NULL AFTER expected_checksum,
    ADD COLUMN replacement_blob LONGBLOB NULL AFTER replacement_checksum,
    ADD COLUMN actor_id BINARY(16) NULL AFTER replacement_blob,
    ADD COLUMN case_id CHAR(16) NULL AFTER actor_id,
    ADD COLUMN owning_server_id VARCHAR(64) NULL AFTER case_id,
    ADD COLUMN fencing_token BIGINT UNSIGNED NULL AFTER owning_server_id,
    ADD COLUMN conflict_code VARCHAR(64) NULL AFTER applied_at,
    ADD COLUMN conflict_detail VARCHAR(512) NULL AFTER conflict_code,
    ADD CONSTRAINT fk_inventory_patch_case
        FOREIGN KEY (case_id) REFERENCES cases(case_id);

CREATE INDEX idx_inventory_patch_player_state
    ON inventory_pending_patches(state, owning_server_id, created_at);
