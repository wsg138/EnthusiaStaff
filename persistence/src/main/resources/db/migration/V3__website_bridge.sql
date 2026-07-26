CREATE TABLE IF NOT EXISTS punishment_codes (
    sanction_id BINARY(16) NOT NULL,
    case_id CHAR(16) NOT NULL,
    key_version INT UNSIGNED NOT NULL,
    generation INT UNSIGNED NOT NULL,
    code_hash BINARY(32) NOT NULL,
    status ENUM('ACTIVE', 'REVOKED') NOT NULL DEFAULT 'ACTIVE',
    claimed_account_token BINARY(32) NULL,
    claimed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    rotated_at TIMESTAMP(6) NULL,
    rotated_by BINARY(16) NULL,
    revoked_at TIMESTAMP(6) NULL,
    revoked_by BINARY(16) NULL,
    PRIMARY KEY (sanction_id),
    UNIQUE KEY uq_punishment_codes_hash (key_version, code_hash),
    INDEX idx_punishment_codes_case (case_id, status),
    INDEX idx_punishment_codes_claim (claimed_account_token, status),
    CONSTRAINT fk_punishment_codes_sanction FOREIGN KEY (sanction_id) REFERENCES sanctions(sanction_id),
    CONSTRAINT fk_punishment_codes_case FOREIGN KEY (case_id) REFERENCES cases(case_id),
    CONSTRAINT ck_punishment_codes_generation CHECK (generation > 0),
    CONSTRAINT ck_punishment_codes_claimed CHECK (
        (claimed_account_token IS NULL AND claimed_at IS NULL)
        OR (claimed_account_token IS NOT NULL AND claimed_at IS NOT NULL)
    )
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS website_api_nonces (
    nonce_hash BINARY(32) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (nonce_hash),
    INDEX idx_website_api_nonces_expiration (expires_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS website_appeal_requests (
    appeal_id BINARY(16) NOT NULL,
    punishment_id BINARY(16) NOT NULL,
    case_id CHAR(16) NOT NULL,
    player_account_token BINARY(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    state ENUM('PREPARED', 'APPLIED', 'REJECTED') NOT NULL,
    outcome_code VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (appeal_id),
    UNIQUE KEY uq_website_appeal_idempotency (idempotency_key),
    UNIQUE KEY uq_website_appeal_punishment (punishment_id),
    INDEX idx_website_appeal_punishment (punishment_id, state),
    CONSTRAINT fk_website_appeal_punishment FOREIGN KEY (punishment_id)
        REFERENCES sanctions(sanction_id),
    CONSTRAINT fk_website_appeal_case FOREIGN KEY (case_id) REFERENCES cases(case_id)
) ENGINE=InnoDB;

CREATE OR REPLACE VIEW public_cases AS
SELECT
    c.case_id,
    c.target_id,
    c.public_reason,
    c.sanction_family,
    c.issued_at,
    c.state,
    c.revision
FROM cases c
WHERE c.visibility = 'PUBLIC'
  AND c.state <> 'FULLY_OVERTURNED';

CREATE OR REPLACE VIEW public_player_name_history AS
SELECT player_id, username, lowercase_username, first_seen_at, last_seen_at
FROM player_names;
