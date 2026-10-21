-- Atomically preserve the confirmed Discord message ID with delivery state.
-- This migration does not activate or arm private report delivery.
ALTER TABLE staffbot_report_notification_intents
    ADD COLUMN discord_message_id VARCHAR(20) NULL,
    ADD UNIQUE KEY uq_staffbot_discord_message_id (discord_message_id),
    ADD CONSTRAINT ck_staffbot_discord_receipt
        CHECK (
            (state = 'DELIVERED' AND discord_message_id IS NOT NULL
                AND discord_message_id REGEXP '^[1-9][0-9]{5,19}$')
            OR (state <> 'DELIVERED' AND discord_message_id IS NULL)
        );
