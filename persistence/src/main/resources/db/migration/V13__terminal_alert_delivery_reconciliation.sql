ALTER TABLE staff_alerts
    ADD COLUMN occurrence_key VARCHAR(160) NULL AFTER lifecycle_event,
    ADD COLUMN lifecycle_actor_id BINARY(16) NULL AFTER occurrence_key;

UPDATE staff_alerts -- nosemgrep
SET occurrence_key = COALESCE(
        CONCAT('legacy-request-revision:', request_revision),
        CONCAT('legacy-alert-id:', LOWER(HEX(alert_id)))
    )
WHERE occurrence_key IS NULL
  AND request_id IS NOT NULL;

ALTER TABLE staff_alerts
    ADD INDEX idx_staff_alerts_event_occurrence (
        request_id, lifecycle_event, occurrence_key, audience, alert_id
    );

ALTER TABLE staff_alert_deliveries
    MODIFY COLUMN state ENUM('PENDING', 'LEASED', 'DELIVERED', 'CANCELLED', 'DEAD_LETTER')
        NOT NULL DEFAULT 'PENDING',
    ADD COLUMN cancelled_at TIMESTAMP(6) NULL AFTER delivered_at,
    ADD COLUMN cancel_reason VARCHAR(64) NULL AFTER cancelled_at;
