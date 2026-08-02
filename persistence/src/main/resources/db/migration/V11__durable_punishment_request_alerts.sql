ALTER TABLE staff_alerts
    ADD COLUMN intent_key VARCHAR(160) NULL AFTER alert_id,
    ADD COLUMN request_id BINARY(16) NULL AFTER intent_key,
    ADD COLUMN request_revision BIGINT UNSIGNED NULL AFTER request_id,
    ADD COLUMN lifecycle_event VARCHAR(64) NULL AFTER request_revision,
    ADD COLUMN audience ENUM('DIRECT_RECIPIENT', 'ELIGIBLE_REVIEWERS', 'OPERATIONAL_ADMINISTRATORS') NULL AFTER lifecycle_event,
    ADD COLUMN excluded_recipient_id BINARY(16) NULL AFTER minimum_rank,
    ADD COLUMN visibility ENUM('PUBLIC', 'PRIVATE') NULL AFTER excluded_recipient_id,
    ADD COLUMN schema_version INT UNSIGNED NULL AFTER visibility,
    ADD COLUMN state ENUM('PENDING', 'LEASED', 'DELIVERED', 'DEAD_LETTER') NOT NULL DEFAULT 'PENDING' AFTER payload_json,
    ADD COLUMN attempt_count INT UNSIGNED NOT NULL DEFAULT 0 AFTER state,
    ADD COLUMN available_at TIMESTAMP(6) NULL AFTER attempt_count,
    ADD COLUMN lease_owner VARCHAR(128) NULL AFTER available_at,
    ADD COLUMN lease_until TIMESTAMP(6) NULL AFTER lease_owner,
    ADD COLUMN last_error_code VARCHAR(64) NULL AFTER lease_until,
    ADD COLUMN delivered_at TIMESTAMP(6) NULL AFTER created_at,
    ADD COLUMN expires_at TIMESTAMP(6) NULL AFTER delivered_at,
    ADD UNIQUE KEY uq_staff_alerts_intent_key (intent_key),
    ADD INDEX idx_staff_alerts_direct_due (recipient_id, state, available_at, created_at, alert_id),
    ADD INDEX idx_staff_alerts_audience_due (audience, state, available_at, created_at, alert_id),
    ADD INDEX idx_staff_alerts_lease_recovery (state, lease_until, alert_id),
    ADD INDEX idx_staff_alerts_request_event (request_id, request_revision, lifecycle_event),
    ADD INDEX idx_staff_alerts_retention (state, delivered_at, expires_at, alert_id);

UPDATE staff_alerts
SET available_at = created_at
WHERE available_at IS NULL;

ALTER TABLE staff_alerts
    MODIFY COLUMN available_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
