ALTER TABLE website_appeal_requests
    MODIFY COLUMN state ENUM(
        'PREPARED',
        'OPEN',
        'INFORMATION_REQUESTED',
        'APPROVAL_PENDING',
        'APPLIED',
        'DENIED',
        'REJECTED'
    ) NOT NULL,
    ADD COLUMN submission_idempotency_key VARCHAR(128) NULL AFTER idempotency_key,
    ADD COLUMN player_account_id VARCHAR(128) NULL AFTER player_account_token,
    ADD COLUMN player_username VARCHAR(16) NULL AFTER player_account_id,
    ADD COLUMN appeal_reason VARCHAR(1000) NULL AFTER player_username,
    ADD COLUMN revision BIGINT UNSIGNED NOT NULL DEFAULT 1 AFTER state,
    ADD COLUMN decision_type ENUM('APPROVE', 'DENY', 'REQUEST_INFORMATION') NULL AFTER revision,
    ADD COLUMN decision_idempotency_key VARCHAR(128) NULL AFTER decision_type,
    ADD COLUMN reviewer_account_id BINARY(16) NULL AFTER decision_idempotency_key,
    ADD COLUMN reviewer_rank VARCHAR(16) NULL AFTER reviewer_account_id,
    ADD COLUMN decision_note VARCHAR(1000) NULL AFTER reviewer_rank,
    ADD COLUMN decided_at TIMESTAMP(6) NULL AFTER decision_note,
    ADD UNIQUE KEY uq_website_appeal_submission_idempotency (submission_idempotency_key),
    ADD UNIQUE KEY uq_website_appeal_decision_idempotency (decision_idempotency_key),
    ADD INDEX idx_website_appeal_queue (state, updated_at, appeal_id);

CREATE TABLE IF NOT EXISTS website_appeal_events (
    event_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    appeal_id BINARY(16) NOT NULL,
    revision BIGINT UNSIGNED NOT NULL,
    event_type ENUM(
        'SUBMITTED',
        'RESUBMITTED',
        'INFORMATION_REQUESTED',
        'APPROVAL_REQUESTED',
        'DENIED'
    ) NOT NULL,
    actor_account_id BINARY(16) NULL,
    actor_rank VARCHAR(16) NULL,
    note VARCHAR(1000) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (event_id),
    UNIQUE KEY uq_website_appeal_event_revision (appeal_id, revision),
    UNIQUE KEY uq_website_appeal_event_idempotency (idempotency_key),
    INDEX idx_website_appeal_events_appeal (appeal_id, event_id),
    CONSTRAINT fk_website_appeal_events_appeal FOREIGN KEY (appeal_id)
        REFERENCES website_appeal_requests(appeal_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS website_appeal_rate_buckets (
    account_token BINARY(32) NOT NULL,
    window_started_at TIMESTAMP(6) NOT NULL,
    submission_count SMALLINT UNSIGNED NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (account_token)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS website_appeal_rate_keys (
    account_token BINARY(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (account_token, idempotency_key),
    INDEX idx_website_appeal_rate_keys_created (created_at),
    CONSTRAINT fk_website_appeal_rate_keys_bucket FOREIGN KEY (account_token)
        REFERENCES website_appeal_rate_buckets(account_token)
        ON DELETE CASCADE
) ENGINE=InnoDB;
