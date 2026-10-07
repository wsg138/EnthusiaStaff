CREATE TABLE policy_v2_full_overturns (
    operation_id BINARY(16) NOT NULL,
    case_id CHAR(16) NOT NULL,
    appeal_reference VARCHAR(128) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    plan_json JSON NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    stage ENUM(
        'STARTED',
        'FINDING_OVERTURNED',
        'SANCTIONS_TERMINATED',
        'REMEDIES_CLEANED',
        'COMPLETED'
    ) NOT NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uq_policy_v2_full_overturn_case (case_id),
    INDEX idx_policy_v2_full_overturn_stage (stage, updated_at),
    CONSTRAINT fk_policy_v2_full_overturn_case FOREIGN KEY (case_id)
        REFERENCES policy_v2_cases(case_id)
) ENGINE=InnoDB;
