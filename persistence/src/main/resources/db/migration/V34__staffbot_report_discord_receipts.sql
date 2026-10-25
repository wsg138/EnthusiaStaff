-- Only the owning future sender can produce a confirmed Discord receipt.
-- Historical V33 DELIVERED entries have no message ID. Quarantine them and
-- preserve their original completion timestamp for manual reconciliation.
-- Never invent a receipt ID or reactivate/retry these old delivery claims.
ALTER TABLE staffbot_report_notification_intents
    ADD COLUMN discord_message_id VARCHAR(20) NULL,
    ADD COLUMN legacy_delivered_at TIMESTAMP(6) NULL;

UPDATE staffbot_report_notification_intents
   SET legacy_delivered_at = delivered_at,
       delivered_at = NULL,
       state = 'REVIEW_REQUIRED',
       lease_owner = NULL,
       lease_expires_at = NULL
 WHERE state = 'DELIVERED';

-- The absolute end anchor requires two backslashes in an SQL string: one
-- reaches PCRE as an escaped backslash, then PCRE sees the literal \z anchor.
ALTER TABLE staffbot_report_notification_intents
    ADD UNIQUE KEY uq_staffbot_discord_message_id (discord_message_id),
    ADD CONSTRAINT ck_staffbot_discord_receipt
        CHECK (
            (state = 'DELIVERED' AND discord_message_id IS NOT NULL
                AND discord_message_id REGEXP '^[1-9][0-9]{5,19}\\z')
            OR (state <> 'DELIVERED' AND discord_message_id IS NULL)
        );
