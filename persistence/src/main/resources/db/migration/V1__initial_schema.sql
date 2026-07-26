CREATE TABLE IF NOT EXISTS players (
    player_id BINARY(16) NOT NULL,
    current_username VARCHAR(32) NULL,
    lowercase_username VARCHAR(32) NULL,
    platform ENUM('JAVA', 'BEDROCK', 'UNKNOWN') NOT NULL DEFAULT 'UNKNOWN',
    current_server VARCHAR(64) NULL,
    last_server VARCHAR(64) NULL,
    first_seen_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_seen_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (player_id),
    INDEX idx_players_current_name (lowercase_username),
    INDEX idx_players_last_seen (last_seen_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS player_names (
    player_id BINARY(16) NOT NULL,
    username VARCHAR(32) NOT NULL,
    lowercase_username VARCHAR(32) NOT NULL,
    first_seen_at TIMESTAMP(6) NOT NULL,
    last_seen_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (player_id, lowercase_username),
    INDEX idx_player_names_lookup (lowercase_username),
    CONSTRAINT fk_player_names_player FOREIGN KEY (player_id) REFERENCES players(player_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS player_sessions (
    session_id BINARY(16) NOT NULL,
    player_id BINARY(16) NOT NULL,
    server_id VARCHAR(64) NOT NULL,
    maintenance_epoch BIGINT UNSIGNED NOT NULL DEFAULT 0,
    connected_at TIMESTAMP(6) NOT NULL,
    disconnected_at TIMESTAMP(6) NULL,
    genuine_gameplay BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (session_id),
    INDEX idx_player_sessions_player (player_id, connected_at),
    INDEX idx_player_sessions_server (server_id, disconnected_at),
    CONSTRAINT fk_player_sessions_player FOREIGN KEY (player_id) REFERENCES players(player_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS client_evidence_snapshots (
    snapshot_id BINARY(16) NOT NULL,
    player_id BINARY(16) NOT NULL,
    captured_at TIMESTAMP(6) NOT NULL,
    platform VARCHAR(32) NOT NULL,
    protocol_version INT NULL,
    reported_brand VARCHAR(255) NULL,
    evidence_json JSON NOT NULL,
    PRIMARY KEY (snapshot_id),
    INDEX idx_client_evidence_player (player_id, captured_at),
    CONSTRAINT fk_client_evidence_player FOREIGN KEY (player_id) REFERENCES players(player_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cases (
    case_id CHAR(16) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    target_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    actor_name VARCHAR(64) NOT NULL,
    actor_rank VARCHAR(32) NOT NULL,
    public_reason VARCHAR(160) NOT NULL,
    exact_reason_id VARCHAR(96) NOT NULL,
    sanction_family VARCHAR(64) NOT NULL,
    internal_explanation TEXT NOT NULL,
    configuration_version VARCHAR(128) NOT NULL,
    visibility ENUM('PUBLIC', 'PRIVATE') NOT NULL,
    state ENUM('OPEN', 'CLOSED', 'FULLY_OVERTURNED') NOT NULL DEFAULT 'OPEN',
    issued_at TIMESTAMP(6) NOT NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    external_source VARCHAR(32) NULL,
    external_id VARCHAR(128) NULL,
    PRIMARY KEY (case_id),
    UNIQUE KEY uq_cases_idempotency (idempotency_key),
    UNIQUE KEY uq_cases_external (external_source, external_id),
    INDEX idx_cases_target_time (target_id, issued_at),
    INDEX idx_cases_family_target (sanction_family, target_id, issued_at),
    INDEX idx_cases_public (visibility, state, issued_at),
    CONSTRAINT fk_cases_target FOREIGN KEY (target_id) REFERENCES players(player_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS case_evidence (
    evidence_id BINARY(16) NOT NULL,
    case_id CHAR(16) NOT NULL,
    evidence_type VARCHAR(64) NOT NULL,
    public_visible BOOLEAN NOT NULL DEFAULT FALSE,
    evidence_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (evidence_id),
    INDEX idx_case_evidence_case (case_id, created_at),
    CONSTRAINT fk_case_evidence_case FOREIGN KEY (case_id) REFERENCES cases(case_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS punishment_steps (
    case_id CHAR(16) NOT NULL,
    raw_ordinal INT UNSIGNED NOT NULL,
    effective_ordinal INT UNSIGNED NOT NULL,
    recency_bonus INT UNSIGNED NOT NULL,
    step_label VARCHAR(80) NOT NULL,
    contribution_json JSON NOT NULL,
    escalation_contributes BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (case_id),
    INDEX idx_punishment_steps_contribution (escalation_contributes),
    CONSTRAINT fk_punishment_steps_case FOREIGN KEY (case_id) REFERENCES cases(case_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sanctions (
    sanction_id BINARY(16) NOT NULL,
    case_id CHAR(16) NOT NULL,
    target_id BINARY(16) NOT NULL,
    sanction_type VARCHAR(48) NOT NULL,
    status ENUM('PENDING', 'ACTIVE', 'APPLIED', 'EXPIRED', 'ENDED_EARLY', 'REVOKED', 'OVERTURNED') NOT NULL,
    issued_at TIMESTAMP(6) NOT NULL,
    activated_at TIMESTAMP(6) NULL,
    expiration_at TIMESTAMP(6) NULL,
    ended_at TIMESTAMP(6) NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    inherited_from BINARY(16) NULL,
    PRIMARY KEY (sanction_id),
    INDEX idx_sanctions_active_target (target_id, status, sanction_type, expiration_at),
    INDEX idx_sanctions_expiration (status, expiration_at),
    INDEX idx_sanctions_case (case_id),
    UNIQUE KEY uq_sanctions_inherited_target (target_id, inherited_from),
    CONSTRAINT fk_sanctions_case FOREIGN KEY (case_id) REFERENCES cases(case_id),
    CONSTRAINT fk_sanctions_target FOREIGN KEY (target_id) REFERENCES players(player_id),
    CONSTRAINT fk_sanctions_inherited FOREIGN KEY (inherited_from) REFERENCES sanctions(sanction_id),
    CONSTRAINT ck_sanctions_expiration CHECK (expiration_at IS NULL OR expiration_at >= issued_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sanction_events (
    event_id BINARY(16) NOT NULL,
    sanction_id BINARY(16) NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    reason VARCHAR(512) NULL,
    event_json JSON NOT NULL,
    idempotency_key VARCHAR(128) NULL,
    PRIMARY KEY (event_id),
    UNIQUE KEY uq_sanction_event_idempotency (idempotency_key),
    INDEX idx_sanction_events_sanction (sanction_id, occurred_at),
    CONSTRAINT fk_sanction_events_sanction FOREIGN KEY (sanction_id) REFERENCES sanctions(sanction_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sanction_links (
    sanction_id BINARY(16) NOT NULL,
    linked_sanction_id BINARY(16) NOT NULL,
    link_type VARCHAR(32) NOT NULL,
    PRIMARY KEY (sanction_id, linked_sanction_id, link_type),
    CONSTRAINT fk_sanction_links_source FOREIGN KEY (sanction_id) REFERENCES sanctions(sanction_id),
    CONSTRAINT fk_sanction_links_target FOREIGN KEY (linked_sanction_id) REFERENCES sanctions(sanction_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS punishment_overturn_requests (
    request_id BINARY(16) NOT NULL,
    case_id CHAR(16) NOT NULL,
    requested_by BINARY(16) NOT NULL,
    explanation TEXT NOT NULL,
    state ENUM('OPEN', 'APPROVED', 'DENIED', 'EXPIRED') NOT NULL,
    requested_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    decided_by BINARY(16) NULL,
    decided_at TIMESTAMP(6) NULL,
    decision_reason TEXT NULL,
    open_case_id CHAR(16) AS (CASE WHEN state = 'OPEN' THEN case_id ELSE NULL END) STORED,
    PRIMARY KEY (request_id),
    UNIQUE KEY uq_overturn_open_case (open_case_id),
    INDEX idx_overturn_state_expiration (state, expires_at),
    CONSTRAINT fk_overturn_case FOREIGN KEY (case_id) REFERENCES cases(case_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS staff_notes (
    note_id BINARY(16) NOT NULL,
    target_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    note_text TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (note_id),
    INDEX idx_staff_notes_target (target_id, created_at),
    CONSTRAINT fk_staff_notes_target FOREIGN KEY (target_id) REFERENCES players(player_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS warnings (
    warning_id BINARY(16) NOT NULL,
    case_id CHAR(16) NOT NULL,
    sanction_id BINARY(16) NOT NULL,
    acknowledged_at TIMESTAMP(6) NULL,
    PRIMARY KEY (warning_id),
    UNIQUE KEY uq_warnings_sanction (sanction_id),
    CONSTRAINT fk_warnings_case FOREIGN KEY (case_id) REFERENCES cases(case_id),
    CONSTRAINT fk_warnings_sanction FOREIGN KEY (sanction_id) REFERENCES sanctions(sanction_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS reports (
    report_id BINARY(16) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    reporter_id BINARY(16) NOT NULL,
    target_id BINARY(16) NOT NULL,
    reason_id VARCHAR(96) NOT NULL,
    description TEXT NOT NULL,
    state ENUM('OPEN', 'CLAIMED', 'AWAITING_REVIEW', 'CLOSED', 'NO_VIOLATION') NOT NULL,
    assigned_to BINARY(16) NULL,
    server_id VARCHAR(64) NOT NULL,
    world_id VARCHAR(128) NULL,
    reporter_coordinates VARCHAR(128) NULL,
    target_coordinates VARCHAR(128) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (report_id),
    UNIQUE KEY uq_reports_idempotency (idempotency_key),
    INDEX idx_reports_state_created (state, created_at),
    INDEX idx_reports_assignee (assigned_to, state),
    INDEX idx_reports_target (target_id, created_at),
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES players(player_id),
    CONSTRAINT fk_reports_target FOREIGN KEY (target_id) REFERENCES players(player_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS report_submission_keys (
    idempotency_key VARCHAR(128) NOT NULL,
    report_id BINARY(16) NOT NULL,
    merged BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (idempotency_key),
    CONSTRAINT fk_report_submission_report FOREIGN KEY (report_id) REFERENCES reports(report_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS report_messages (
    message_id BINARY(16) NOT NULL,
    report_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (message_id),
    INDEX idx_report_messages_report (report_id, created_at),
    CONSTRAINT fk_report_messages_report FOREIGN KEY (report_id) REFERENCES reports(report_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS report_events (
    event_id BINARY(16) NOT NULL,
    report_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    from_state VARCHAR(32) NOT NULL,
    to_state VARCHAR(32) NOT NULL,
    note TEXT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    resulting_revision BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (event_id),
    UNIQUE KEY uq_report_event_idempotency (idempotency_key),
    INDEX idx_report_events_report (report_id, occurred_at),
    CONSTRAINT fk_report_events_report FOREIGN KEY (report_id) REFERENCES reports(report_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS report_chat_snapshots (
    snapshot_id BINARY(16) NOT NULL,
    report_id BINARY(16) NOT NULL,
    captured_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    messages_json JSON NOT NULL,
    PRIMARY KEY (snapshot_id),
    INDEX idx_report_chat_expiration (expires_at),
    CONSTRAINT fk_report_chat_report FOREIGN KEY (report_id) REFERENCES reports(report_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS report_private_message_snapshots (
    snapshot_id BINARY(16) NOT NULL,
    report_id BINARY(16) NOT NULL,
    captured_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    messages_json JSON NOT NULL,
    PRIMARY KEY (snapshot_id),
    INDEX idx_report_private_expiration (expires_at),
    CONSTRAINT fk_report_private_report FOREIGN KEY (report_id) REFERENCES reports(report_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS alt_relationships (
    relationship_id BINARY(16) NOT NULL,
    lower_player_id BINARY(16) NOT NULL,
    upper_player_id BINARY(16) NOT NULL,
    relationship_state VARCHAR(32) NOT NULL,
    confidence DECIMAL(5,4) NOT NULL,
    locked_until_reopened BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (relationship_id),
    UNIQUE KEY uq_alt_pair (lower_player_id, upper_player_id),
    INDEX idx_alt_lower (lower_player_id, relationship_state),
    INDEX idx_alt_upper (upper_player_id, relationship_state),
    CONSTRAINT fk_alt_lower_player FOREIGN KEY (lower_player_id) REFERENCES players(player_id),
    CONSTRAINT fk_alt_upper_player FOREIGN KEY (upper_player_id) REFERENCES players(player_id),
    CONSTRAINT ck_alt_order CHECK (lower_player_id < upper_player_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS alt_evidence (
    evidence_id BINARY(16) NOT NULL,
    relationship_id BINARY(16) NOT NULL,
    evidence_type VARCHAR(48) NOT NULL,
    weight DECIMAL(6,4) NOT NULL,
    evidence_json JSON NOT NULL,
    observed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (evidence_id),
    INDEX idx_alt_evidence_relationship (relationship_id, observed_at),
    CONSTRAINT fk_alt_evidence_relationship FOREIGN KEY (relationship_id) REFERENCES alt_relationships(relationship_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS network_identity_tokens (
    token_id BINARY(16) NOT NULL,
    player_id BINARY(16) NOT NULL,
    hmac_key_version INT UNSIGNED NOT NULL,
    equality_token BINARY(32) NOT NULL,
    encryption_key_version INT UNSIGNED NOT NULL,
    encrypted_value VARBINARY(512) NOT NULL,
    first_seen_at TIMESTAMP(6) NOT NULL,
    last_seen_at TIMESTAMP(6) NOT NULL,
    session_count INT UNSIGNED NOT NULL DEFAULT 1,
    PRIMARY KEY (token_id),
    UNIQUE KEY uq_identity_player_token (player_id, hmac_key_version, equality_token),
    INDEX idx_identity_token_lookup (hmac_key_version, equality_token),
    CONSTRAINT fk_identity_player FOREIGN KEY (player_id) REFERENCES players(player_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS staff_sessions (
    session_id BINARY(16) NOT NULL,
    staff_id BINARY(16) NOT NULL,
    server_id VARCHAR(64) NOT NULL,
    state ENUM('ENTERING', 'ACTIVE', 'EXITING', 'RECOVERY_REQUIRED', 'CLOSED') NOT NULL,
    vanish_active BOOLEAN NOT NULL DEFAULT FALSE,
    started_at TIMESTAMP(6) NOT NULL,
    ended_at TIMESTAMP(6) NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    active_staff_id BINARY(16) AS (CASE WHEN state IN ('ENTERING', 'ACTIVE', 'EXITING', 'RECOVERY_REQUIRED')
        THEN staff_id ELSE NULL END) STORED,
    PRIMARY KEY (session_id),
    UNIQUE KEY uq_staff_sessions_active_staff (active_staff_id),
    INDEX idx_staff_sessions_player_state (staff_id, state),
    CONSTRAINT fk_staff_sessions_player FOREIGN KEY (staff_id) REFERENCES players(player_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS staff_state_snapshots (
    snapshot_id BINARY(16) NOT NULL,
    session_id BINARY(16) NOT NULL,
    schema_version INT UNSIGNED NOT NULL,
    checksum CHAR(64) NOT NULL,
    snapshot_blob LONGBLOB NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (snapshot_id),
    UNIQUE KEY uq_staff_snapshot_session (session_id),
    CONSTRAINT fk_staff_snapshot_session FOREIGN KEY (session_id) REFERENCES staff_sessions(session_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS player_freezes (
    player_id BINARY(16) NOT NULL,
    state ENUM('ACTIVE', 'RELEASED', 'EXPIRED') NOT NULL,
    frozen_by BINARY(16) NOT NULL,
    reason VARCHAR(512) NOT NULL,
    frozen_at TIMESTAMP(6) NOT NULL,
    offline_expires_at TIMESTAMP(6) NULL,
    keep_active BOOLEAN NOT NULL DEFAULT FALSE,
    released_by BINARY(16) NULL,
    release_reason VARCHAR(512) NULL,
    released_at TIMESTAMP(6) NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (player_id),
    INDEX idx_player_freezes_active (state, offline_expires_at),
    CONSTRAINT fk_player_freezes_player FOREIGN KEY (player_id) REFERENCES players(player_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS staff_vanish_states (
    staff_id BINARY(16) NOT NULL,
    active BOOLEAN NOT NULL,
    staff_rank VARCHAR(32) NOT NULL,
    updated_by BINARY(16) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (staff_id),
    INDEX idx_staff_vanish_active (active, staff_rank, updated_at),
    CONSTRAINT fk_staff_vanish_player FOREIGN KEY (staff_id) REFERENCES players(player_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS inventory_profiles (
    profile_id BINARY(16) NOT NULL,
    player_id BINARY(16) NOT NULL,
    scope_id VARCHAR(64) NOT NULL,
    owning_server_id VARCHAR(64) NOT NULL,
    current_revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (profile_id),
    UNIQUE KEY uq_inventory_profile_scope (player_id, scope_id),
    INDEX idx_inventory_profile_server (owning_server_id),
    CONSTRAINT fk_inventory_profile_player FOREIGN KEY (player_id) REFERENCES players(player_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS inventory_profile_revisions (
    profile_id BINARY(16) NOT NULL,
    revision BIGINT UNSIGNED NOT NULL,
    checksum CHAR(64) NOT NULL,
    recorded_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (profile_id, revision),
    CONSTRAINT fk_inventory_revision_profile FOREIGN KEY (profile_id) REFERENCES inventory_profiles(profile_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS inventory_operations (
    operation_id BINARY(16) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    profile_id BINARY(16) NOT NULL,
    case_id CHAR(16) NULL,
    actor_id BINARY(16) NOT NULL,
    operation_type VARCHAR(48) NOT NULL,
    state VARCHAR(48) NOT NULL,
    expected_revision BIGINT UNSIGNED NOT NULL,
    fencing_token BIGINT UNSIGNED NOT NULL,
    operation_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uq_inventory_operation_idempotency (idempotency_key),
    INDEX idx_inventory_operation_recovery (state, updated_at),
    CONSTRAINT fk_inventory_operation_profile FOREIGN KEY (profile_id) REFERENCES inventory_profiles(profile_id),
    CONSTRAINT fk_inventory_operation_case FOREIGN KEY (case_id) REFERENCES cases(case_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS inventory_snapshots (
    snapshot_id BINARY(16) NOT NULL,
    operation_id BINARY(16) NOT NULL,
    profile_id BINARY(16) NOT NULL,
    revision BIGINT UNSIGNED NOT NULL,
    checksum CHAR(64) NOT NULL,
    snapshot_blob LONGBLOB NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (snapshot_id),
    INDEX idx_inventory_snapshot_expiration (expires_at),
    CONSTRAINT fk_inventory_snapshot_operation FOREIGN KEY (operation_id) REFERENCES inventory_operations(operation_id),
    CONSTRAINT fk_inventory_snapshot_profile FOREIGN KEY (profile_id) REFERENCES inventory_profiles(profile_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS inventory_pending_patches (
    patch_id BINARY(16) NOT NULL,
    operation_id BINARY(16) NOT NULL,
    profile_id BINARY(16) NOT NULL,
    expected_revision BIGINT UNSIGNED NOT NULL,
    state ENUM('PENDING', 'APPLYING', 'APPLIED', 'CONFLICT', 'QUARANTINED') NOT NULL,
    patch_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    applied_at TIMESTAMP(6) NULL,
    PRIMARY KEY (patch_id),
    INDEX idx_inventory_patch_due (profile_id, state, created_at),
    CONSTRAINT fk_inventory_patch_operation FOREIGN KEY (operation_id) REFERENCES inventory_operations(operation_id),
    CONSTRAINT fk_inventory_patch_profile FOREIGN KEY (profile_id) REFERENCES inventory_profiles(profile_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS confiscated_asset_snapshots (
    snapshot_id BINARY(16) NOT NULL,
    case_id CHAR(16) NOT NULL,
    inventory_operation_id BINARY(16) NOT NULL,
    checksum CHAR(64) NOT NULL,
    asset_blob LONGBLOB NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    restored_at TIMESTAMP(6) NULL,
    PRIMARY KEY (snapshot_id),
    INDEX idx_confiscated_case (case_id),
    INDEX idx_confiscated_expiration (expires_at),
    CONSTRAINT fk_confiscated_case FOREIGN KEY (case_id) REFERENCES cases(case_id),
    CONSTRAINT fk_confiscated_operation FOREIGN KEY (inventory_operation_id) REFERENCES inventory_operations(operation_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS economy_operations (
    operation_id BINARY(16) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    case_id CHAR(16) NOT NULL,
    target_id BINARY(16) NOT NULL,
    requested_amount BIGINT UNSIGNED NOT NULL,
    authoritative_total BIGINT UNSIGNED NOT NULL,
    state VARCHAR(48) NOT NULL,
    plan_json JSON NOT NULL,
    before_snapshot JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uq_economy_operation_idempotency (idempotency_key),
    INDEX idx_economy_operation_recovery (state, updated_at),
    CONSTRAINT fk_economy_operation_case FOREIGN KEY (case_id) REFERENCES cases(case_id),
    CONSTRAINT fk_economy_operation_target FOREIGN KEY (target_id) REFERENCES players(player_id),
    CONSTRAINT ck_economy_amount CHECK (requested_amount <= authoritative_total)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS market_compliance_cases (
    compliance_id BINARY(16) NOT NULL,
    case_id CHAR(16) NOT NULL,
    target_id BINARY(16) NOT NULL,
    stall_id VARCHAR(128) NULL,
    state VARCHAR(48) NOT NULL,
    review_due_at TIMESTAMP(6) NULL,
    snapshot_json JSON NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (compliance_id),
    INDEX idx_market_review_due (state, review_due_at),
    CONSTRAINT fk_market_compliance_case FOREIGN KEY (case_id) REFERENCES cases(case_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS reputation_blacklists (
    blacklist_id BINARY(16) NOT NULL,
    player_id BINARY(16) NOT NULL,
    case_id CHAR(16) NOT NULL,
    status ENUM('ACTIVE', 'EXPIRED', 'REMOVED') NOT NULL,
    starts_at TIMESTAMP(6) NOT NULL,
    expiration_at TIMESTAMP(6) NULL,
    removed_at TIMESTAMP(6) NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (blacklist_id),
    INDEX idx_reputation_blacklist_player (player_id, status, expiration_at),
    CONSTRAINT fk_reputation_blacklist_player FOREIGN KEY (player_id) REFERENCES players(player_id),
    CONSTRAINT fk_reputation_blacklist_case FOREIGN KEY (case_id) REFERENCES cases(case_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS staff_alerts (
    alert_id BINARY(16) NOT NULL,
    recipient_id BINARY(16) NULL,
    minimum_rank VARCHAR(32) NULL,
    alert_type VARCHAR(64) NOT NULL,
    payload_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    read_at TIMESTAMP(6) NULL,
    PRIMARY KEY (alert_id),
    INDEX idx_staff_alerts_unread (recipient_id, read_at, created_at),
    INDEX idx_staff_alerts_rank (minimum_rank, read_at, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS discord_outbox (
    message_id BINARY(16) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    destination VARCHAR(32) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload_json JSON NOT NULL,
    state ENUM('PENDING', 'LEASED', 'DELIVERED', 'DEAD_LETTER') NOT NULL DEFAULT 'PENDING',
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    available_at TIMESTAMP(6) NOT NULL,
    lease_owner VARCHAR(128) NULL,
    lease_until TIMESTAMP(6) NULL,
    last_error_code VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    delivered_at TIMESTAMP(6) NULL,
    PRIMARY KEY (message_id),
    UNIQUE KEY uq_discord_outbox_idempotency (idempotency_key),
    INDEX idx_discord_outbox_due (state, available_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS discord_delivery_channels (
    destination VARCHAR(32) NOT NULL,
    consecutive_failures INT UNSIGNED NOT NULL DEFAULT 0,
    open_until TIMESTAMP(6) NULL,
    last_error_code VARCHAR(64) NULL,
    last_success_at TIMESTAMP(6) NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (destination),
    INDEX idx_discord_channel_open (open_until)
) ENGINE=InnoDB;

INSERT IGNORE INTO discord_delivery_channels(destination, updated_at) VALUES
    ('punishments', CURRENT_TIMESTAMP(6)),
    ('reports', CURRENT_TIMESTAMP(6)),
    ('logs-staffmode', CURRENT_TIMESTAMP(6)),
    ('alerts', CURRENT_TIMESTAMP(6));

CREATE TABLE IF NOT EXISTS network_outbox (
    message_id BINARY(16) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    destination VARCHAR(64) NOT NULL,
    message_type VARCHAR(64) NOT NULL,
    protocol_version INT UNSIGNED NOT NULL,
    payload_json JSON NOT NULL,
    state ENUM('PENDING', 'LEASED', 'ACKNOWLEDGED', 'DEAD_LETTER') NOT NULL DEFAULT 'PENDING',
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    available_at TIMESTAMP(6) NOT NULL,
    lease_owner VARCHAR(128) NULL,
    lease_until TIMESTAMP(6) NULL,
    last_error_code VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    acknowledged_at TIMESTAMP(6) NULL,
    PRIMARY KEY (message_id),
    UNIQUE KEY uq_network_outbox_idempotency (idempotency_key),
    INDEX idx_network_outbox_due (state, available_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS network_inbox (
    consumer_id VARCHAR(64) NOT NULL,
    message_id BINARY(16) NOT NULL,
    message_type VARCHAR(64) NOT NULL,
    outcome_code VARCHAR(64) NOT NULL,
    outcome_json JSON NOT NULL,
    processed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (consumer_id, message_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS network_outbox_deliveries (
    message_id BINARY(16) NOT NULL,
    server_id VARCHAR(64) NOT NULL,
    state ENUM('PENDING', 'ACKNOWLEDGED') NOT NULL DEFAULT 'PENDING',
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP(6) NULL,
    acknowledged_at TIMESTAMP(6) NULL,
    PRIMARY KEY (message_id, server_id),
    INDEX idx_network_delivery_pending (server_id, state, last_attempt_at),
    CONSTRAINT fk_network_delivery_message FOREIGN KEY (message_id) REFERENCES network_outbox(message_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS operational_state (
    singleton_id TINYINT UNSIGNED NOT NULL,
    mode VARCHAR(32) NOT NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    reason VARCHAR(512) NOT NULL,
    updated_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (singleton_id),
    CONSTRAINT ck_operational_singleton CHECK (singleton_id = 1)
) ENGINE=InnoDB;

INSERT IGNORE INTO operational_state(singleton_id, mode, reason, updated_at)
VALUES (1, 'BOOTSTRAP', 'Initial schema bootstrap', CURRENT_TIMESTAMP(6));

CREATE TABLE IF NOT EXISTS migration_runs (
    run_id BINARY(16) NOT NULL,
    mode ENUM('DRY_RUN', 'IMPORT', 'SHADOW', 'CUTOVER') NOT NULL,
    state VARCHAR(48) NOT NULL,
    source_schema_name VARCHAR(128) NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    source_high_watermark BIGINT NULL,
    counts_json JSON NOT NULL,
    checksums_json JSON NOT NULL,
    mismatch_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    report_json JSON NOT NULL,
    PRIMARY KEY (run_id),
    INDEX idx_migration_runs_state (mode, state, started_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cutover_records (
    cutover_id BINARY(16) NOT NULL,
    migration_run_id BINARY(16) NULL,
    assessment_json JSON NOT NULL,
    blockers_json JSON NOT NULL,
    founder_override_used BOOLEAN NOT NULL DEFAULT FALSE,
    authorized_by BINARY(16) NOT NULL,
    authorized_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (cutover_id),
    INDEX idx_cutover_authorized (authorized_at),
    CONSTRAINT fk_cutover_run FOREIGN KEY (migration_run_id) REFERENCES migration_runs(run_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS migration_mappings (
    mapping_id BINARY(16) NOT NULL,
    run_id BINARY(16) NOT NULL,
    source_system VARCHAR(32) NOT NULL,
    source_table VARCHAR(64) NOT NULL,
    external_id VARCHAR(128) NOT NULL,
    case_id CHAR(16) NULL,
    source_checksum CHAR(64) NOT NULL,
    mapping_state VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (mapping_id),
    UNIQUE KEY uq_migration_external (source_system, source_table, external_id),
    INDEX idx_migration_mapping_run (run_id, mapping_state),
    CONSTRAINT fk_migration_mapping_run FOREIGN KEY (run_id) REFERENCES migration_runs(run_id),
    CONSTRAINT fk_migration_mapping_case FOREIGN KEY (case_id) REFERENCES cases(case_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS shadow_comparisons (
    comparison_id BINARY(16) NOT NULL,
    run_id BINARY(16) NOT NULL,
    player_id BINARY(16) NULL,
    comparison_type VARCHAR(48) NOT NULL,
    legacy_decision VARCHAR(64) NOT NULL,
    expected_decision VARCHAR(64) NOT NULL,
    matched BOOLEAN NOT NULL,
    detail_json JSON NOT NULL,
    compared_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (comparison_id),
    INDEX idx_shadow_mismatch (run_id, matched, compared_at),
    CONSTRAINT fk_shadow_comparison_run FOREIGN KEY (run_id) REFERENCES migration_runs(run_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS configuration_versions (
    configuration_id BINARY(16) NOT NULL,
    version_name VARCHAR(128) NOT NULL,
    checksum CHAR(64) NOT NULL,
    configuration_json JSON NOT NULL,
    activated_at TIMESTAMP(6) NOT NULL,
    activated_by BINARY(16) NOT NULL,
    PRIMARY KEY (configuration_id),
    UNIQUE KEY uq_configuration_checksum (checksum),
    INDEX idx_configuration_activation (activated_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS audit_events (
    sequence_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id BINARY(16) NOT NULL,
    correlation_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NULL,
    target_id BINARY(16) NULL,
    case_id CHAR(16) NULL,
    event_type VARCHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    event_json JSON NOT NULL,
    idempotency_key VARCHAR(128) NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (sequence_id),
    UNIQUE KEY uq_audit_event_id (event_id),
    UNIQUE KEY uq_audit_idempotency (idempotency_key),
    INDEX idx_audit_case (case_id, sequence_id),
    INDEX idx_audit_actor (actor_id, sequence_id),
    INDEX idx_audit_target (target_id, sequence_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS operation_leases (
    resource_key VARCHAR(192) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    fencing_token BIGINT UNSIGNED NOT NULL,
    lease_until TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (resource_key),
    INDEX idx_operation_leases_expiration (lease_until)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS recovery_quarantine (
    quarantine_id BINARY(16) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    operation_id BINARY(16) NOT NULL,
    resource_key VARCHAR(192) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    detail_json JSON NOT NULL,
    quarantined_at TIMESTAMP(6) NOT NULL,
    resolved_at TIMESTAMP(6) NULL,
    resolved_by BINARY(16) NULL,
    resolution_json JSON NULL,
    PRIMARY KEY (quarantine_id),
    UNIQUE KEY uq_quarantine_operation (operation_type, operation_id),
    INDEX idx_quarantine_open (resolved_at, quarantined_at)
) ENGINE=InnoDB;
