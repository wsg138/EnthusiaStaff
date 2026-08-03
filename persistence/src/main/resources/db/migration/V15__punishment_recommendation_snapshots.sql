ALTER TABLE punishment_steps
    ADD COLUMN recommended_sanctions_json JSON NULL AFTER contribution_json;
