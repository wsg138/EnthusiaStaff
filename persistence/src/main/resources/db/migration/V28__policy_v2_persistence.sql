CREATE TABLE policy_v2_policy_snapshots (
    snapshot_id BINARY(16) NOT NULL,
    policy_version VARCHAR(128) NOT NULL,
    schema_version SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    snapshot_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (snapshot_id),
    UNIQUE KEY uq_policy_v2_snapshot_version (policy_version),
    INDEX idx_policy_v2_snapshot_hash (content_sha256)
) ENGINE=InnoDB;

CREATE TABLE policy_v2_cases (
    case_id CHAR(16) NOT NULL,
    policy_snapshot_id BINARY(16) NOT NULL,
    original_finding_json JSON NOT NULL,
    effective_finding_json JSON NULL,
    finding_state ENUM('CONFIRMED', 'RECLASSIFIED', 'OVERTURNED') NOT NULL,
    incident_at TIMESTAMP(6) NOT NULL,
    finding_revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    sanction_revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (case_id),
    INDEX idx_policy_v2_cases_snapshot (policy_snapshot_id),
    INDEX idx_policy_v2_cases_history (incident_at, finding_state),
    CONSTRAINT fk_policy_v2_cases_case FOREIGN KEY (case_id) REFERENCES cases(case_id),
    CONSTRAINT fk_policy_v2_cases_snapshot FOREIGN KEY (policy_snapshot_id)
        REFERENCES policy_v2_policy_snapshots(snapshot_id),
    CONSTRAINT ck_policy_v2_effective_finding CHECK (
        (finding_state = 'OVERTURNED' AND effective_finding_json IS NULL)
        OR (finding_state <> 'OVERTURNED' AND effective_finding_json IS NOT NULL)
    )
) ENGINE=InnoDB;

CREATE TABLE policy_v2_resolutions (
    resolution_id BINARY(16) NOT NULL,
    case_id CHAR(16) NOT NULL,
    policy_snapshot_id BINARY(16) NOT NULL,
    resolution_json JSON NOT NULL,
    history_inputs_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (resolution_id),
    UNIQUE KEY uq_policy_v2_resolution_case (case_id),
    INDEX idx_policy_v2_resolution_snapshot (policy_snapshot_id),
    CONSTRAINT fk_policy_v2_resolution_case FOREIGN KEY (case_id)
        REFERENCES policy_v2_cases(case_id),
    CONSTRAINT fk_policy_v2_resolution_snapshot FOREIGN KEY (policy_snapshot_id)
        REFERENCES policy_v2_policy_snapshots(snapshot_id)
) ENGINE=InnoDB;

CREATE TABLE policy_v2_remedies (
    case_id CHAR(16) NOT NULL,
    remedy_id VARCHAR(96) NOT NULL,
    remedy_json JSON NOT NULL,
    status ENUM('REQUIRED', 'SATISFIED', 'WAIVED') NOT NULL DEFAULT 'REQUIRED',
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (case_id, remedy_id),
    INDEX idx_policy_v2_remedies_status (status, updated_at),
    CONSTRAINT fk_policy_v2_remedies_case FOREIGN KEY (case_id)
        REFERENCES policy_v2_cases(case_id)
) ENGINE=InnoDB;

CREATE TABLE policy_v2_finding_revisions (
    revision_id BINARY(16) NOT NULL,
    case_id CHAR(16) NOT NULL,
    finding_revision BIGINT UNSIGNED NOT NULL,
    revision_type ENUM('RECLASSIFICATION', 'OVERTURN') NOT NULL,
    from_offense_id VARCHAR(96) NOT NULL,
    to_offense_id VARCHAR(96) NULL,
    resulting_finding_json JSON NULL,
    reason VARCHAR(1000) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    appeal_reference VARCHAR(128) NULL,
    operation_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (revision_id),
    UNIQUE KEY uq_policy_v2_finding_revision (case_id, finding_revision),
    UNIQUE KEY uq_policy_v2_finding_revision_operation (operation_key),
    INDEX idx_policy_v2_finding_revision_case_time (case_id, occurred_at),
    CONSTRAINT fk_policy_v2_finding_revision_case FOREIGN KEY (case_id)
        REFERENCES policy_v2_cases(case_id),
    CONSTRAINT ck_policy_v2_finding_revision_shape CHECK (
        (revision_type = 'OVERTURN' AND to_offense_id IS NULL AND resulting_finding_json IS NULL)
        OR
        (revision_type = 'RECLASSIFICATION' AND to_offense_id IS NOT NULL AND resulting_finding_json IS NOT NULL)
    )
) ENGINE=InnoDB;

CREATE TABLE policy_v2_sanction_revisions (
    revision_id BINARY(16) NOT NULL,
    case_id CHAR(16) NOT NULL,
    sanction_revision BIGINT UNSIGNED NOT NULL,
    change_kind ENUM('INITIAL', 'LENIENCY', 'OTHER') NOT NULL,
    sanctions_json JSON NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    appeal_reference VARCHAR(128) NULL,
    operation_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (revision_id),
    UNIQUE KEY uq_policy_v2_sanction_revision (case_id, sanction_revision),
    UNIQUE KEY uq_policy_v2_sanction_revision_operation (operation_key),
    INDEX idx_policy_v2_sanction_revision_case_time (case_id, occurred_at),
    CONSTRAINT fk_policy_v2_sanction_revision_case FOREIGN KEY (case_id)
        REFERENCES policy_v2_cases(case_id)
) ENGINE=InnoDB;

CREATE TABLE policy_v2_appeal_events (
    event_id BINARY(16) NOT NULL,
    case_id CHAR(16) NOT NULL,
    appeal_reference VARCHAR(128) NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    actor_id BINARY(16) NULL,
    note VARCHAR(1000) NOT NULL,
    operation_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (event_id),
    UNIQUE KEY uq_policy_v2_appeal_operation (operation_key),
    INDEX idx_policy_v2_appeal_case (case_id, occurred_at),
    INDEX idx_policy_v2_appeal_reference (appeal_reference, occurred_at),
    CONSTRAINT fk_policy_v2_appeal_case FOREIGN KEY (case_id)
        REFERENCES policy_v2_cases(case_id)
) ENGINE=InnoDB;

CREATE TABLE policy_v2_public_projections (
    case_id CHAR(16) NOT NULL,
    projection_json JSON NOT NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (case_id),
    CONSTRAINT fk_policy_v2_public_projection_case FOREIGN KEY (case_id)
        REFERENCES policy_v2_cases(case_id)
) ENGINE=InnoDB;

CREATE TABLE policy_v2_shadow_evaluations (
    evaluation_id BINARY(16) NOT NULL,
    subject_id BINARY(16) NOT NULL,
    case_id CHAR(16) NULL,
    policy_snapshot_id BINARY(16) NOT NULL,
    finding_json JSON NOT NULL,
    resolution_json JSON NOT NULL,
    history_inputs_json JSON NOT NULL,
    operation_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evaluated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (evaluation_id),
    UNIQUE KEY uq_policy_v2_shadow_operation (operation_key),
    INDEX idx_policy_v2_shadow_subject_time (subject_id, evaluated_at),
    INDEX idx_policy_v2_shadow_case_time (case_id, evaluated_at),
    CONSTRAINT fk_policy_v2_shadow_snapshot FOREIGN KEY (policy_snapshot_id)
        REFERENCES policy_v2_policy_snapshots(snapshot_id)
) ENGINE=InnoDB;

CREATE TABLE policy_v2_audit_events (
    audit_id BINARY(16) NOT NULL,
    case_id CHAR(16) NULL,
    event_type VARCHAR(64) NOT NULL,
    actor_id BINARY(16) NULL,
    event_json JSON NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (audit_id),
    INDEX idx_policy_v2_audit_case_time (case_id, occurred_at),
    INDEX idx_policy_v2_audit_type_time (event_type, occurred_at),
    CONSTRAINT fk_policy_v2_audit_case FOREIGN KEY (case_id)
        REFERENCES policy_v2_cases(case_id)
) ENGINE=InnoDB;

CREATE TABLE policy_v2_operations (
    operation_key VARCHAR(128) NOT NULL,
    operation_kind VARCHAR(48) NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    case_id CHAR(16) NULL,
    result_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (operation_key),
    INDEX idx_policy_v2_operations_case (case_id, created_at),
    CONSTRAINT fk_policy_v2_operations_case FOREIGN KEY (case_id)
        REFERENCES policy_v2_cases(case_id)
) ENGINE=InnoDB;
