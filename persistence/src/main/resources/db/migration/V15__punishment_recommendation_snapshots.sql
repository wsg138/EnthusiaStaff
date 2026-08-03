ALTER TABLE punishment_steps
    ADD COLUMN selected_ordinal INT UNSIGNED NULL AFTER effective_ordinal,
    ADD COLUMN recommended_sanctions_json JSON NULL AFTER contribution_json;
