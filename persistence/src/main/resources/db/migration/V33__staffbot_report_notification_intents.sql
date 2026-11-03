-- StaffBot-specific, reference-only journal; does NOT feed Velocity webhooks.
-- An INERT intent cannot be claimed or sent. Future enablement must create
-- newly armed events under an explicitly approved private route; no backfill
-- or automatic promotion of historical INERT intents is permitted.
CREATE TABLE staffbot_report_notification_intents (
    intent_id BINARY(16) NOT NULL,
    idempotency_key VARCHAR(191) NOT NULL,
    report_id BINARY(16) NOT NULL,
    event_type ENUM('REPORT_CREATED', 'REPORT_MERGED') NOT NULL,
    state ENUM('INERT', 'ARMED', 'LEASED', 'DELIVERED', 'REVIEW_REQUIRED')
        NOT NULL DEFAULT 'INERT',
    created_at TIMESTAMP(6) NOT NULL,
    available_at TIMESTAMP(6) NULL,
    lease_owner VARCHAR(128) NULL,
    lease_expires_at TIMESTAMP(6) NULL,
    delivered_at TIMESTAMP(6) NULL,
    attempts INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (intent_id),
    UNIQUE KEY uq_staffbot_report_intent_submission (idempotency_key),
    INDEX idx_staffbot_report_intent_report (report_id, created_at),
    INDEX idx_staffbot_report_intent_due (state, available_at),
    CONSTRAINT fk_staffbot_report_intent_report
        FOREIGN KEY (report_id) REFERENCES reports(report_id),
    CONSTRAINT ck_staffbot_report_intent_lease CHECK (
        (state = 'LEASED' AND lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL)
        OR (state <> 'LEASED' AND lease_owner IS NULL AND lease_expires_at IS NULL)
    ),
    CONSTRAINT ck_staffbot_report_intent_delivery CHECK (
        (state = 'DELIVERED' AND delivered_at IS NOT NULL)
        OR (state <> 'DELIVERED' AND delivered_at IS NULL)
    )
) ENGINE=InnoDB;
