CREATE TABLE policy_v2_remedy_enforcement (
    case_id CHAR(16) NOT NULL,
    remedy_id VARCHAR(96) NOT NULL,
    subject_id BINARY(16) NOT NULL,
    remedy_type ENUM(
        'REMOVE_CONTENT',
        'CONFISCATE',
        'ACCESS_RESTRICTION',
        'CORRECT_PROFILE',
        'OTHER'
    ) NOT NULL,
    scope ENUM(
        'NETWORK_ACCESS',
        'REPORT_SUBMISSION',
        'MARKET_ACCESS',
        'REPUTATION_ACCESS',
        'CONTENT',
        'ASSET'
    ) NOT NULL,
    condition_json JSON NOT NULL,
    lifecycle ENUM('REQUIRED', 'ENFORCED', 'SATISFIED', 'WAIVED') NOT NULL DEFAULT 'REQUIRED',
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (case_id, remedy_id),
    INDEX idx_policy_v2_enforcement_subject (subject_id, lifecycle, updated_at),
    CONSTRAINT fk_policy_v2_enforcement_remedy FOREIGN KEY (case_id, remedy_id)
        REFERENCES policy_v2_remedies(case_id, remedy_id)
) ENGINE=InnoDB;
