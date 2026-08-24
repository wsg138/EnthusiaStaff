CREATE TABLE discord_link_codes (
    code_id BINARY(16) NOT NULL,
    code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    direction ENUM('DISCORD_TO_MINECRAFT', 'MINECRAFT_TO_DISCORD') NOT NULL,
    initiator_discord_user_id DECIMAL(20, 0) NULL,
    initiator_minecraft_player_id BINARY(16) NULL,
    state ENUM('ACTIVE', 'CLAIMED', 'CONSUMED', 'SUPERSEDED', 'EXPIRED') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    claim_operation_key VARCHAR(128) NULL,
    claim_until DATETIME(6) NULL,
    consumed_operation_key VARCHAR(128) NULL,
    consumed_at DATETIME(6) NULL,
    superseded_at DATETIME(6) NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (code_id),
    UNIQUE KEY uq_discord_link_codes_hash (code_hash),
    KEY idx_discord_link_codes_discord (initiator_discord_user_id, direction, state),
    KEY idx_discord_link_codes_minecraft (initiator_minecraft_player_id, direction, state),
    CONSTRAINT chk_discord_link_codes_owner CHECK (
        (direction = 'DISCORD_TO_MINECRAFT'
            AND initiator_discord_user_id IS NOT NULL
            AND initiator_minecraft_player_id IS NULL)
        OR
        (direction = 'MINECRAFT_TO_DISCORD'
            AND initiator_discord_user_id IS NULL
            AND initiator_minecraft_player_id IS NOT NULL)
    ),
    CONSTRAINT chk_discord_link_codes_expiry CHECK (expires_at > created_at),
    CONSTRAINT chk_discord_link_codes_claim CHECK (
        (state = 'CLAIMED' AND claim_operation_key IS NOT NULL AND claim_until IS NOT NULL)
        OR state <> 'CLAIMED'
    ),
    CONSTRAINT chk_discord_link_codes_consumed CHECK (
        (state = 'CONSUMED' AND consumed_operation_key IS NOT NULL AND consumed_at IS NOT NULL)
        OR state <> 'CONSUMED'
    ),
    CONSTRAINT fk_discord_link_codes_discord FOREIGN KEY (initiator_discord_user_id)
        REFERENCES moderation_subject_discord_identities(discord_user_id),
    CONSTRAINT fk_discord_link_codes_minecraft FOREIGN KEY (initiator_minecraft_player_id)
        REFERENCES moderation_subject_minecraft_identities(player_id)
) ENGINE=InnoDB;

CREATE TABLE discord_link_audit (
    audit_id BINARY(16) NOT NULL,
    operation_key VARCHAR(128) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    actor_name VARCHAR(64) NOT NULL,
    actor_rank VARCHAR(32) NOT NULL,
    action ENUM('FORCE_LINK', 'FORCE_UNLINK', 'REASSIGN', 'MAIN_OVERRIDE_SET', 'MAIN_OVERRIDE_CLEAR') NOT NULL,
    discord_user_id DECIMAL(20, 0) NULL,
    minecraft_player_id BINARY(16) NULL,
    detail VARCHAR(512) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (audit_id),
    UNIQUE KEY uq_discord_link_audit_operation (operation_key),
    KEY idx_discord_link_audit_discord (discord_user_id, created_at),
    KEY idx_discord_link_audit_minecraft (minecraft_player_id, created_at)
) ENGINE=InnoDB;
