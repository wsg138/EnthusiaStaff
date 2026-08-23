CREATE TABLE moderation_subjects (
    subject_id BINARY(16) NOT NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (subject_id),
    INDEX idx_moderation_subjects_updated (updated_at)
) ENGINE=InnoDB;

CREATE TABLE moderation_subject_minecraft_identities (
    player_id BINARY(16) NOT NULL,
    subject_id BINARY(16) NOT NULL,
    linked_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (player_id),
    UNIQUE KEY uq_subject_minecraft_membership (subject_id, player_id),
    INDEX idx_subject_minecraft_subject (subject_id),
    CONSTRAINT fk_subject_minecraft_subject
        FOREIGN KEY (subject_id) REFERENCES moderation_subjects(subject_id),
    CONSTRAINT fk_subject_minecraft_player
        FOREIGN KEY (player_id) REFERENCES players(player_id)
) ENGINE=InnoDB;

CREATE TABLE moderation_subject_discord_identities (
    discord_user_id DECIMAL(20, 0) UNSIGNED NOT NULL,
    subject_id BINARY(16) NOT NULL,
    linked_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (discord_user_id),
    UNIQUE KEY uq_subject_current_discord (subject_id),
    CONSTRAINT fk_subject_discord_subject
        FOREIGN KEY (subject_id) REFERENCES moderation_subjects(subject_id),
    CONSTRAINT ck_subject_discord_snowflake
        CHECK (discord_user_id BETWEEN 1 AND 18446744073709551615)
) ENGINE=InnoDB;

CREATE TABLE moderation_subject_main_accounts (
    subject_id BINARY(16) NOT NULL,
    player_id BINARY(16) NOT NULL,
    selection_source ENUM('AUTOMATIC', 'STAFF_OVERRIDE') NOT NULL,
    selected_at TIMESTAMP(6) NOT NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (subject_id),
    CONSTRAINT fk_subject_main_subject
        FOREIGN KEY (subject_id) REFERENCES moderation_subjects(subject_id),
    CONSTRAINT fk_subject_main_membership
        FOREIGN KEY (subject_id, player_id)
        REFERENCES moderation_subject_minecraft_identities(subject_id, player_id)
) ENGINE=InnoDB;

CREATE TABLE discord_minecraft_links (
    link_id BINARY(16) NOT NULL,
    operation_key VARCHAR(128) NOT NULL,
    unlink_operation_key VARCHAR(128) NULL,
    subject_id BINARY(16) NOT NULL,
    discord_user_id DECIMAL(20, 0) UNSIGNED NOT NULL,
    minecraft_player_id BINARY(16) NOT NULL,
    linked_at TIMESTAMP(6) NOT NULL,
    unlinked_at TIMESTAMP(6) NULL,
    source ENUM('DISCORD_CODE', 'MINECRAFT_CODE', 'MIGRATED_DISCORDSRV', 'STAFF_RECOVERY') NOT NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    active_minecraft_player_id BINARY(16) AS (
        CASE WHEN unlinked_at IS NULL THEN minecraft_player_id ELSE NULL END
    ) STORED,
    PRIMARY KEY (link_id),
    UNIQUE KEY uq_discord_link_operation (operation_key),
    UNIQUE KEY uq_discord_unlink_operation (unlink_operation_key),
    UNIQUE KEY uq_discord_current_minecraft (active_minecraft_player_id),
    INDEX idx_discord_links_user_active (discord_user_id, unlinked_at, linked_at),
    INDEX idx_discord_links_player_history (minecraft_player_id, linked_at),
    INDEX idx_discord_links_subject (subject_id, linked_at),
    CONSTRAINT fk_discord_links_subject
        FOREIGN KEY (subject_id) REFERENCES moderation_subjects(subject_id),
    CONSTRAINT fk_discord_links_player
        FOREIGN KEY (minecraft_player_id) REFERENCES players(player_id),
    CONSTRAINT ck_discord_links_user_snowflake
        CHECK (discord_user_id BETWEEN 1 AND 18446744073709551615),
    CONSTRAINT ck_discord_links_time
        CHECK (unlinked_at IS NULL OR unlinked_at >= linked_at)
) ENGINE=InnoDB;

CREATE TABLE moderation_enforcement_targets (
    target_id BINARY(16) NOT NULL,
    operation_key VARCHAR(128) NOT NULL,
    subject_id BINARY(16) NOT NULL,
    platform ENUM('MINECRAFT', 'DISCORD') NOT NULL,
    minecraft_player_id BINARY(16) NULL,
    discord_user_id DECIMAL(20, 0) UNSIGNED NULL,
    scope_type ENUM('DISCORD_GUILD', 'MINECRAFT_SERVER', 'MINECRAFT_NETWORK') NOT NULL,
    scope_value VARCHAR(96) NOT NULL,
    state VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (target_id),
    UNIQUE KEY uq_enforcement_target_operation (operation_key),
    INDEX idx_enforcement_target_subject (subject_id, state, created_at),
    INDEX idx_enforcement_target_state (state, updated_at),
    CONSTRAINT fk_enforcement_target_subject
        FOREIGN KEY (subject_id) REFERENCES moderation_subjects(subject_id),
    CONSTRAINT ck_enforcement_identity_platform CHECK (
        (platform = 'MINECRAFT' AND minecraft_player_id IS NOT NULL AND discord_user_id IS NULL
            AND scope_type IN ('MINECRAFT_SERVER', 'MINECRAFT_NETWORK'))
        OR
        (platform = 'DISCORD' AND minecraft_player_id IS NULL
            AND discord_user_id BETWEEN 1 AND 18446744073709551615
            AND scope_type = 'DISCORD_GUILD')
    )
) ENGINE=InnoDB;

CREATE TABLE discord_evidence_metadata (
    evidence_id BINARY(16) NOT NULL,
    operation_key VARCHAR(128) NOT NULL,
    subject_id BINARY(16) NOT NULL,
    case_id CHAR(16) NULL,
    guild_id DECIMAL(20, 0) UNSIGNED NOT NULL,
    channel_id DECIMAL(20, 0) UNSIGNED NOT NULL,
    message_id DECIMAL(20, 0) UNSIGNED NOT NULL,
    author_user_id DECIMAL(20, 0) UNSIGNED NOT NULL,
    captured_at TIMESTAMP(6) NOT NULL,
    retain_until TIMESTAMP(6) NOT NULL,
    metadata_json JSON NOT NULL,
    purge_state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (evidence_id),
    UNIQUE KEY uq_discord_evidence_operation (operation_key),
    INDEX idx_discord_evidence_subject (subject_id, captured_at),
    INDEX idx_discord_evidence_retention (purge_state, retain_until),
    INDEX idx_discord_evidence_message (guild_id, channel_id, message_id),
    CONSTRAINT fk_discord_evidence_subject
        FOREIGN KEY (subject_id) REFERENCES moderation_subjects(subject_id),
    CONSTRAINT fk_discord_evidence_case
        FOREIGN KEY (case_id) REFERENCES cases(case_id),
    CONSTRAINT ck_discord_evidence_snowflakes CHECK (
        guild_id BETWEEN 1 AND 18446744073709551615
        AND channel_id BETWEEN 1 AND 18446744073709551615
        AND message_id BETWEEN 1 AND 18446744073709551615
        AND author_user_id BETWEEN 1 AND 18446744073709551615
    ),
    CONSTRAINT ck_discord_evidence_retention CHECK (retain_until >= captured_at)
) ENGINE=InnoDB;

CREATE TABLE discord_security_locks (
    lock_id BINARY(16) NOT NULL,
    operation_key VARCHAR(128) NOT NULL,
    release_operation_key VARCHAR(128) NULL,
    subject_id BINARY(16) NOT NULL,
    discord_user_id DECIMAL(20, 0) UNSIGNED NOT NULL,
    reason_code VARCHAR(96) NOT NULL,
    state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    locked_at TIMESTAMP(6) NOT NULL,
    released_at TIMESTAMP(6) NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    active_discord_user_id DECIMAL(20, 0) UNSIGNED AS (
        CASE WHEN state = 'ACTIVE' THEN discord_user_id ELSE NULL END
    ) STORED,
    PRIMARY KEY (lock_id),
    UNIQUE KEY uq_security_lock_operation (operation_key),
    UNIQUE KEY uq_security_lock_release_operation (release_operation_key),
    UNIQUE KEY uq_security_lock_active_user (active_discord_user_id),
    INDEX idx_security_lock_subject (subject_id, locked_at),
    CONSTRAINT fk_security_lock_subject
        FOREIGN KEY (subject_id) REFERENCES moderation_subjects(subject_id),
    CONSTRAINT ck_security_lock_snowflake
        CHECK (discord_user_id BETWEEN 1 AND 18446744073709551615),
    CONSTRAINT ck_security_lock_release CHECK (
        (state = 'ACTIVE' AND released_at IS NULL)
        OR (state = 'RELEASED' AND released_at IS NOT NULL AND released_at >= locked_at)
    )
) ENGINE=InnoDB;

CREATE TABLE discord_reconciliation_state (
    reconciliation_key VARCHAR(160) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    desired_state_json JSON NOT NULL,
    observed_state_json JSON NULL,
    state VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NULL,
    last_error_code VARCHAR(96) NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (reconciliation_key),
    INDEX idx_discord_reconciliation_due (state, next_attempt_at, updated_at),
    INDEX idx_discord_reconciliation_resource (resource_type, resource_id)
) ENGINE=InnoDB;

CREATE TABLE discord_maintenance_work (
    work_id BINARY(16) NOT NULL,
    work_type VARCHAR(48) NOT NULL,
    resource_key VARCHAR(160) NOT NULL,
    due_at TIMESTAMP(6) NOT NULL,
    state VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    lease_owner VARCHAR(96) NULL,
    lease_until TIMESTAMP(6) NULL,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    last_error_code VARCHAR(96) NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (work_id),
    UNIQUE KEY uq_discord_maintenance_resource (work_type, resource_key),
    INDEX idx_discord_maintenance_due (state, due_at, lease_until),
    CONSTRAINT ck_discord_maintenance_lease CHECK (
        (state = 'CLAIMED' AND lease_owner IS NOT NULL AND lease_until IS NOT NULL)
        OR (state <> 'CLAIMED')
    )
) ENGINE=InnoDB;

INSERT INTO moderation_subjects(subject_id, revision, created_at, updated_at)
SELECT player_id, 0, first_seen_at, GREATEST(first_seen_at, last_seen_at)
FROM players;

INSERT INTO moderation_subject_minecraft_identities(player_id, subject_id, linked_at)
SELECT player_id, player_id, first_seen_at
FROM players;

INSERT INTO moderation_subject_main_accounts(
    subject_id, player_id, selection_source, selected_at, revision
)
SELECT player_id, player_id, 'AUTOMATIC', first_seen_at, 0
FROM players;
