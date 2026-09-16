ALTER TABLE cases
    MODIFY target_id BINARY(16) NULL,
    ADD COLUMN subject_id BINARY(16) NULL AFTER target_id,
    ADD INDEX idx_cases_subject_time (subject_id, issued_at),
    ADD CONSTRAINT fk_cases_subject
        FOREIGN KEY (subject_id) REFERENCES moderation_subjects(subject_id),
    ADD CONSTRAINT ck_cases_target_or_subject
        CHECK (target_id IS NOT NULL OR subject_id IS NOT NULL);

UPDATE cases c
JOIN moderation_subject_minecraft_identities membership
  ON membership.player_id = c.target_id
SET c.subject_id = membership.subject_id
WHERE c.subject_id IS NULL;

CREATE TABLE discord_investigation_cases (
    case_id CHAR(16) NOT NULL,
    operation_key VARCHAR(128) NOT NULL,
    subject_id BINARY(16) NOT NULL,
    source ENUM('DISCORD_PUNISHMENT', 'INVESTIGATION') NOT NULL,
    punishment_id BINARY(16) NULL,
    last_activity_at TIMESTAMP(6) NOT NULL,
    closed_at TIMESTAMP(6) NULL,
    punishment_ended_at TIMESTAMP(6) NULL,
    source_revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (case_id),
    UNIQUE KEY uq_discord_investigation_case_operation (operation_key),
    UNIQUE KEY uq_discord_investigation_case_punishment (punishment_id),
    INDEX idx_discord_investigation_case_subject (subject_id, last_activity_at),
    INDEX idx_discord_investigation_case_inactivity (last_activity_at),
    CONSTRAINT fk_discord_investigation_case_authority
        FOREIGN KEY (case_id) REFERENCES cases(case_id),
    CONSTRAINT fk_discord_investigation_case_subject
        FOREIGN KEY (subject_id) REFERENCES moderation_subjects(subject_id),
    CONSTRAINT ck_discord_investigation_case_source CHECK (
        (source = 'DISCORD_PUNISHMENT' AND punishment_id IS NOT NULL)
        OR (source = 'INVESTIGATION' AND punishment_id IS NULL)
    ),
    CONSTRAINT ck_discord_investigation_case_close CHECK (
        closed_at IS NULL OR closed_at >= last_activity_at
    )
) ENGINE=InnoDB;

CREATE TABLE discord_private_notes (
    note_id BINARY(16) NOT NULL,
    operation_key VARCHAR(128) NOT NULL,
    subject_id BINARY(16) NOT NULL,
    scope_type ENUM('SUBJECT', 'DISCORD_USER', 'MINECRAFT_PLAYER', 'CASE') NOT NULL,
    scope_value VARCHAR(160) NOT NULL,
    visibility ENUM('STAFF', 'MANAGEMENT') NOT NULL,
    current_text TEXT NOT NULL,
    created_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_by BINARY(16) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (note_id),
    UNIQUE KEY uq_discord_private_note_operation (operation_key),
    INDEX idx_discord_private_note_subject (subject_id, updated_at, note_id),
    INDEX idx_discord_private_note_scope (scope_type, scope_value, updated_at),
    CONSTRAINT fk_discord_private_note_subject
        FOREIGN KEY (subject_id) REFERENCES moderation_subjects(subject_id),
    CONSTRAINT fk_discord_private_note_creator
        FOREIGN KEY (created_by) REFERENCES players(player_id),
    CONSTRAINT fk_discord_private_note_updater
        FOREIGN KEY (updated_by) REFERENCES players(player_id)
) ENGINE=InnoDB;

CREATE TABLE discord_private_note_versions (
    note_id BINARY(16) NOT NULL,
    revision BIGINT UNSIGNED NOT NULL,
    operation_key VARCHAR(128) NOT NULL,
    note_text TEXT NOT NULL,
    changed_by BINARY(16) NOT NULL,
    changed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (note_id, revision),
    UNIQUE KEY uq_discord_private_note_version_operation (operation_key),
    CONSTRAINT fk_discord_private_note_version_note
        FOREIGN KEY (note_id) REFERENCES discord_private_notes(note_id),
    CONSTRAINT fk_discord_private_note_version_actor
        FOREIGN KEY (changed_by) REFERENCES players(player_id)
) ENGINE=InnoDB;

CREATE TABLE discord_investigation_evidence (
    evidence_id BINARY(16) NOT NULL,
    case_id CHAR(16) NOT NULL,
    message_link VARCHAR(512) NOT NULL,
    message_created_at TIMESTAMP(6) NOT NULL,
    last_observed_at TIMESTAMP(6) NOT NULL,
    edited_at TIMESTAMP(6) NULL,
    message_content MEDIUMTEXT NOT NULL,
    attachment_metadata_json JSON NOT NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (evidence_id),
    INDEX idx_discord_investigation_evidence_case (case_id, last_observed_at),
    CONSTRAINT fk_discord_investigation_evidence_parent
        FOREIGN KEY (evidence_id) REFERENCES discord_evidence_metadata(evidence_id),
    CONSTRAINT fk_discord_investigation_evidence_case
        FOREIGN KEY (case_id) REFERENCES discord_investigation_cases(case_id),
    CONSTRAINT ck_discord_investigation_evidence_times CHECK (
        last_observed_at >= message_created_at
        AND (edited_at IS NULL OR edited_at >= message_created_at)
    )
) ENGINE=InnoDB;

CREATE TABLE discord_investigation_evidence_versions (
    evidence_id BINARY(16) NOT NULL,
    revision BIGINT UNSIGNED NOT NULL,
    operation_key VARCHAR(128) NOT NULL,
    message_content MEDIUMTEXT NOT NULL,
    attachment_metadata_json JSON NOT NULL,
    edited_at TIMESTAMP(6) NULL,
    recorded_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (evidence_id, revision),
    UNIQUE KEY uq_discord_investigation_evidence_version_operation (operation_key),
    CONSTRAINT fk_discord_investigation_evidence_version_parent
        FOREIGN KEY (evidence_id) REFERENCES discord_investigation_evidence(evidence_id)
) ENGINE=InnoDB;

CREATE TABLE discord_investigation_context (
    evidence_id BINARY(16) NOT NULL,
    message_id DECIMAL(20, 0) UNSIGNED NOT NULL,
    guild_id DECIMAL(20, 0) UNSIGNED NOT NULL,
    channel_id DECIMAL(20, 0) UNSIGNED NOT NULL,
    author_user_id DECIMAL(20, 0) UNSIGNED NOT NULL,
    message_created_at TIMESTAMP(6) NOT NULL,
    edited_at TIMESTAMP(6) NULL,
    message_link VARCHAR(512) NOT NULL,
    message_content MEDIUMTEXT NOT NULL,
    attachment_metadata_json JSON NOT NULL,
    captured_at TIMESTAMP(6) NOT NULL,
    last_observed_at TIMESTAMP(6) NOT NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (evidence_id, message_id),
    INDEX idx_discord_investigation_context_channel (guild_id, channel_id, message_id),
    CONSTRAINT fk_discord_investigation_context_evidence
        FOREIGN KEY (evidence_id) REFERENCES discord_investigation_evidence(evidence_id),
    CONSTRAINT ck_discord_investigation_context_snowflakes CHECK (
        message_id BETWEEN 1 AND 18446744073709551615
        AND guild_id BETWEEN 1 AND 18446744073709551615
        AND channel_id BETWEEN 1 AND 18446744073709551615
        AND author_user_id BETWEEN 1 AND 18446744073709551615
    ),
    CONSTRAINT ck_discord_investigation_context_times CHECK (
        last_observed_at >= message_created_at
        AND captured_at >= message_created_at
        AND (edited_at IS NULL OR edited_at >= message_created_at)
    )
) ENGINE=InnoDB;

CREATE TABLE discord_investigation_context_operations (
    operation_key VARCHAR(128) NOT NULL,
    evidence_id BINARY(16) NOT NULL,
    captured_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (operation_key),
    CONSTRAINT fk_discord_investigation_context_operation_evidence
        FOREIGN KEY (evidence_id) REFERENCES discord_investigation_evidence(evidence_id)
) ENGINE=InnoDB;

CREATE TABLE discord_evasion_alerts (
    alert_id BINARY(16) NOT NULL,
    operation_key VARCHAR(160) NOT NULL,
    subject_id BINARY(16) NOT NULL,
    punishment_id BINARY(16) NOT NULL,
    target_discord_user_id DECIMAL(20, 0) UNSIGNED NOT NULL,
    punishment_type VARCHAR(32) NOT NULL,
    punishment_summary VARCHAR(512) NOT NULL,
    punishment_state VARCHAR(32) NOT NULL,
    punishment_expires_at TIMESTAMP(6) NULL,
    triggering_minecraft_player_id BINARY(16) NOT NULL,
    triggering_minecraft_username VARCHAR(32) NULL,
    current_server VARCHAR(64) NOT NULL,
    player_revision BIGINT UNSIGNED NOT NULL,
    trigger_type VARCHAR(48) NOT NULL,
    triggered_at TIMESTAMP(6) NOT NULL,
    state ENUM('OPEN', 'RESOLVED') NOT NULL DEFAULT 'OPEN',
    discord_delivery ENUM('PENDING', 'DELIVERED', 'RETRY') NOT NULL DEFAULT 'PENDING',
    minecraft_delivery ENUM('PENDING', 'DELIVERED', 'RETRY') NOT NULL DEFAULT 'PENDING',
    discord_attempts INT UNSIGNED NOT NULL DEFAULT 0,
    minecraft_attempts INT UNSIGNED NOT NULL DEFAULT 0,
    discord_error_code VARCHAR(96) NULL,
    minecraft_error_code VARCHAR(96) NULL,
    discord_next_attempt_at TIMESTAMP(6) NULL,
    minecraft_next_attempt_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    resolved_at TIMESTAMP(6) NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (alert_id),
    UNIQUE KEY uq_discord_evasion_alert_operation (operation_key),
    INDEX idx_discord_evasion_alert_discord_due (state, discord_delivery, discord_next_attempt_at, updated_at),
    INDEX idx_discord_evasion_alert_minecraft_due (state, minecraft_delivery, minecraft_next_attempt_at, updated_at),
    INDEX idx_discord_evasion_alert_subject (subject_id, created_at),
    CONSTRAINT fk_discord_evasion_alert_subject
        FOREIGN KEY (subject_id) REFERENCES moderation_subjects(subject_id),
    CONSTRAINT fk_discord_evasion_alert_player
        FOREIGN KEY (triggering_minecraft_player_id) REFERENCES players(player_id),
    CONSTRAINT ck_discord_evasion_alert_discord_snowflake CHECK (
        target_discord_user_id BETWEEN 1 AND 18446744073709551615
    ),
    CONSTRAINT ck_discord_evasion_alert_trigger_time CHECK (triggered_at <= created_at),
    CONSTRAINT ck_discord_evasion_alert_resolution CHECK (
        (state = 'OPEN' AND resolved_at IS NULL)
        OR (state = 'RESOLVED' AND resolved_at IS NOT NULL AND resolved_at >= created_at)
    ),
    CONSTRAINT ck_discord_evasion_alert_discord_delivery CHECK (
        (discord_delivery = 'DELIVERED' AND discord_error_code IS NULL AND discord_next_attempt_at IS NULL)
        OR (discord_delivery = 'PENDING' AND discord_error_code IS NULL AND discord_next_attempt_at IS NOT NULL)
        OR (discord_delivery = 'RETRY' AND discord_error_code IS NOT NULL AND discord_next_attempt_at IS NOT NULL)
    ),
    CONSTRAINT ck_discord_evasion_alert_minecraft_delivery CHECK (
        (minecraft_delivery = 'DELIVERED' AND minecraft_error_code IS NULL AND minecraft_next_attempt_at IS NULL)
        OR (minecraft_delivery = 'PENDING' AND minecraft_error_code IS NULL AND minecraft_next_attempt_at IS NOT NULL)
        OR (minecraft_delivery = 'RETRY' AND minecraft_error_code IS NOT NULL AND minecraft_next_attempt_at IS NOT NULL)
    )
) ENGINE=InnoDB;
