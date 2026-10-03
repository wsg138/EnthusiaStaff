-- Durable Staff-side intent and reconciliation metadata for the provider-owned
-- EnthusiaMarket moderation journal. Existing rows remain readable as legacy
-- records; new ES-X03 rows always populate the added fields.

ALTER TABLE market_compliance_cases
    ADD COLUMN idempotency_key VARCHAR(128) NULL AFTER compliance_id,
    ADD COLUMN recovery_until TIMESTAMP(6) NULL AFTER review_due_at,
    ADD COLUMN revision BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER snapshot_json,
    ADD COLUMN created_at TIMESTAMP(6) NULL AFTER revision,
    ADD COLUMN review_alerted_at TIMESTAMP(6) NULL AFTER updated_at,
    ADD UNIQUE KEY uq_market_compliance_idempotency (idempotency_key),
    ADD INDEX idx_market_compliance_recovery (state, updated_at);
