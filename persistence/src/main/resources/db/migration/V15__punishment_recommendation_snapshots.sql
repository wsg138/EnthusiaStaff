ALTER TABLE punishment_steps
    ADD COLUMN selected_ordinal INT UNSIGNED NULL AFTER effective_ordinal,
    ADD COLUMN recommended_sanctions_json JSON NULL AFTER contribution_json,
    ADD CONSTRAINT ck_punishment_steps_recommendation_snapshot CHECK (
        (selected_ordinal IS NULL AND recommended_sanctions_json IS NULL)
        OR (selected_ordinal IS NOT NULL AND recommended_sanctions_json IS NOT NULL)
    );
