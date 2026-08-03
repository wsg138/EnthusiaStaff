ALTER TABLE punishment_steps
    ADD COLUMN decay_eligible BOOLEAN NULL AFTER recommended_sanctions_json,
    ADD CONSTRAINT ck_punishment_steps_decay_eligible CHECK (
        decay_eligible IS NULL OR decay_eligible IN (FALSE, TRUE)
    );
