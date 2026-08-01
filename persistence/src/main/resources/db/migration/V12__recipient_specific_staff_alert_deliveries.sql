ALTER TABLE staff_alerts
    ADD COLUMN intent_state ENUM('ACTIVE', 'CLOSED', 'EXPIRED') NOT NULL DEFAULT 'ACTIVE' AFTER expires_at,
    ADD COLUMN closed_at TIMESTAMP(6) NULL AFTER intent_state,
    ADD COLUMN close_reason VARCHAR(64) NULL AFTER closed_at,
    ADD INDEX idx_staff_alerts_intent_eligibility (
        audience, intent_state, expires_at, created_at, alert_id
    ),
    ADD INDEX idx_staff_alerts_intent_retention (
        intent_state, closed_at, expires_at, alert_id
    );

UPDATE staff_alerts -- nosemgrep
SET expires_at = created_at + INTERVAL 30 DAY
WHERE expires_at IS NULL;

ALTER TABLE staff_alerts
    MODIFY COLUMN expires_at TIMESTAMP(6) NOT NULL
        DEFAULT (CURRENT_TIMESTAMP(6) + INTERVAL 30 DAY);

CREATE TABLE IF NOT EXISTS staff_alert_deliveries (
    alert_id BINARY(16) NOT NULL,
    recipient_id BINARY(16) NOT NULL,
    state ENUM('PENDING', 'LEASED', 'DELIVERED', 'DEAD_LETTER') NOT NULL DEFAULT 'PENDING',
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    available_at TIMESTAMP(6) NOT NULL,
    lease_owner VARCHAR(128) NULL,
    lease_until TIMESTAMP(6) NULL,
    last_error_code VARCHAR(64) NULL,
    delivered_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (alert_id, recipient_id),
    INDEX idx_staff_alert_delivery_recipient_due (
        recipient_id, state, available_at, created_at, alert_id
    ),
    INDEX idx_staff_alert_delivery_lease_recovery (
        state, lease_until, alert_id, recipient_id
    ),
    INDEX idx_staff_alert_delivery_retention (
        state, delivered_at, updated_at, alert_id, recipient_id
    ),
    CONSTRAINT fk_staff_alert_delivery_alert
        FOREIGN KEY (alert_id) REFERENCES staff_alerts(alert_id)
) ENGINE=InnoDB;

INSERT INTO staff_alert_deliveries( -- nosemgrep
    alert_id, recipient_id, state, attempt_count, available_at,
    lease_owner, lease_until, last_error_code, delivered_at, created_at, updated_at
)
SELECT alert_id, recipient_id, state, attempt_count, available_at, -- nosemgrep
       lease_owner, lease_until, last_error_code, delivered_at, created_at,
       COALESCE(delivered_at, lease_until, available_at, created_at)
FROM staff_alerts
WHERE audience = 'DIRECT_RECIPIENT'
  AND recipient_id IS NOT NULL
ON DUPLICATE KEY UPDATE alert_id = VALUES(alert_id); -- nosemgrep

UPDATE staff_alerts
SET intent_state = CASE
        WHEN audience = 'DIRECT_RECIPIENT' AND state = 'DELIVERED' THEN 'CLOSED'
        WHEN expires_at <= CURRENT_TIMESTAMP(6) THEN 'EXPIRED'
        ELSE 'ACTIVE'
    END,
    closed_at = CASE
        WHEN audience = 'DIRECT_RECIPIENT' AND state = 'DELIVERED'
            THEN COALESCE(delivered_at, created_at)
        WHEN expires_at <= CURRENT_TIMESTAMP(6) THEN expires_at
        ELSE NULL
    END,
    close_reason = CASE
        WHEN audience = 'DIRECT_RECIPIENT' AND state = 'DELIVERED' THEN 'LEGACY_DIRECT_DELIVERED'
        WHEN expires_at <= CURRENT_TIMESTAMP(6) THEN 'INTENT_EXPIRED'
        ELSE NULL
    END;
