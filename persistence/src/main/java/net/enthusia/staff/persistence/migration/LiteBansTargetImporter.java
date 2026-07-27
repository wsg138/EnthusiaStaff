package net.enthusia.staff.persistence.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.SecureIdentifiers;
import net.enthusia.staff.common.security.NetworkIdentityProtector;
import net.enthusia.staff.common.security.ProtectedNetworkIdentity;
import net.enthusia.staff.domain.migration.LegacyNetworkAddress;
import net.enthusia.staff.domain.migration.LegacySanction;
import net.enthusia.staff.domain.migration.LegacySanctionType;
import net.enthusia.staff.domain.migration.MigrationChecksum;
import net.enthusia.staff.domain.sanction.SanctionStatus;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import net.enthusia.staff.persistence.UuidBytes;

final class LiteBansTargetImporter {
    private static final UUID SYSTEM_ACTOR = new UUID(0L, 1L);

    private final DataSource target;
    private final ObjectMapper json;
    private final Clock clock;
    private final SecureIdentifiers identifiers;
    private final Optional<NetworkIdentityProtector> networkIdentityProtector;

    LiteBansTargetImporter(
            DataSource target,
            ObjectMapper json,
            Clock clock,
            NetworkIdentityProtector networkIdentityProtector
    ) {
        if (target == null || json == null || clock == null) {
            throw new IllegalArgumentException("migration target dependencies must be present");
        }
        this.target = target;
        this.json = json;
        this.clock = clock;
        this.identifiers = new SecureIdentifiers(new SecureRandom());
        this.networkIdentityProtector = Optional.ofNullable(networkIdentityProtector);
    }

    TargetImportReport importAll(UUID runId, LiteBansReadReport read, int batchSize) {
        if (runId == null || read == null || batchSize < 1) {
            throw new IllegalArgumentException("migration import inputs must be valid");
        }
        requireNetworkIdentityProtection(read);
        long protectedIdentityRecords = importNetworkObservations(read.networkObservations(), batchSize);
        long imported = 0;
        long reconciled = 0;
        long replayed = 0;
        List<LiteBansReadReport.RejectedRow> rejected = new ArrayList<>();
        for (LegacySanction sanction : read.records()) {
            ImportResult result = importOne(runId, sanction);
            if (result.outcome() == ImportOutcome.IMPORTED) {
                imported++;
            } else if (result.outcome() == ImportOutcome.RECONCILED) {
                reconciled++;
            } else if (result.outcome() == ImportOutcome.REPLAYED) {
                replayed++;
            } else {
                rejected.add(new LiteBansReadReport.RejectedRow(
                        sanction.sourceTable(), sanction.externalId(), result.rejectionCode()
                ));
            }
        }
        return new TargetImportReport(
                imported,
                reconciled,
                replayed,
                protectedIdentityRecords,
                List.copyOf(rejected)
        );
    }

    private ImportResult importOne(UUID runId, LegacySanction legacy) {
        TargetResolution resolution = resolveTarget(legacy);
        if (resolution.playerId().isEmpty()) {
            return ImportResult.rejected(resolution.rejectionCode());
        }
        UUID targetId = resolution.playerId().orElseThrow();
        String checksum = new MigrationChecksum().calculate(List.of(legacy));
        Instant now = clock.instant();
        try (Connection connection = target.getConnection()) {
            connection.setAutoCommit(false);
            boolean mappingCheckedAndAbsent = false;
            try {
                ensurePlayer(connection, targetId, legacy.username(), legacy.issuedAt());
                if (legacy.networkAddress().isPresent()) {
                    upsertProtectedIdentity(
                            connection,
                            targetId,
                            protect(legacy.networkAddress().orElseThrow()),
                            legacy.issuedAt()
                    );
                }
                LockedMapping mapping = lockMapping(connection, legacy);
                if (mapping != null) {
                    ImportResult result = reconcile(
                            connection, runId, mapping, legacy, targetId, checksum, now
                    );
                    if (result.outcome() == ImportOutcome.REJECTED) {
                        connection.rollback();
                        return result;
                    }
                    connection.commit();
                    return result;
                }
                mappingCheckedAndAbsent = true;
                CaseId caseId = identifiers.newCaseId();
                LegacyProjection projection = project(legacy, now);
                insertCase(connection, caseId, targetId, legacy, projection);
                insertStep(connection, caseId);
                UUID sanctionId = insertSanction(connection, caseId, targetId, legacy, projection);
                insertEvent(connection, sanctionId, legacy.issuedAt());
                insertMapping(connection, runId, legacy, caseId, checksum, now);
                insertAudit(connection, runId, caseId, targetId, legacy, sanctionId, now);
                connection.commit();
                return ImportResult.imported();
            } catch (SQLException | JsonProcessingException exception) {
                rollback(connection, exception);
                if (mappingCheckedAndAbsent && mappingExistsAfterConflict(legacy)) {
                    return ImportResult.replayed();
                }
                throw new ModerationPersistenceException("Unable to import a LiteBans source record", exception);
            } catch (RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to open a LiteBans import transaction", exception);
        }
    }

    private void requireNetworkIdentityProtection(LiteBansReadReport read) {
        boolean needed = !read.networkObservations().isEmpty()
                || read.records().stream().anyMatch(sanction -> sanction.networkAddress().isPresent());
        if (needed && networkIdentityProtector.isEmpty()) {
            throw new IllegalStateException(
                    "Protected network identity keys are required to migrate LiteBans IP history and IP bans"
            );
        }
    }

    private long importNetworkObservations(List<LegacyNetworkObservation> observations, int batchSize) {
        if (observations.isEmpty()) {
            return 0;
        }
        try (Connection connection = target.getConnection()) {
            connection.setAutoCommit(false);
            long imported = 0;
            int pending = 0;
            try {
                for (LegacyNetworkObservation observation : observations) {
                    ensurePlayer(
                            connection,
                            observation.playerId(),
                            observation.username(),
                            observation.observedAt()
                    );
                    upsertProtectedIdentity(
                            connection,
                            observation.playerId(),
                            protect(observation.networkAddress()),
                            observation.observedAt()
                    );
                    imported++;
                    pending++;
                    if (pending >= batchSize) {
                        connection.commit();
                        pending = 0;
                    }
                }
                if (pending > 0) {
                    connection.commit();
                }
                return imported;
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw new ModerationPersistenceException(
                        "Unable to import protected LiteBans network identity history",
                        exception
                );
            } catch (RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to open network identity import transaction", exception);
        }
    }

    private ProtectedNetworkIdentity protect(LegacyNetworkAddress address) {
        byte[] raw = address.addressBytes();
        try {
            return networkIdentityProtector.orElseThrow().protect(raw);
        } finally {
            java.util.Arrays.fill(raw, (byte) 0);
        }
    }

    private static void upsertProtectedIdentity(
            Connection connection,
            UUID playerId,
            ProtectedNetworkIdentity identity,
            Instant observedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO network_identity_tokens(token_id, player_id, hmac_key_version, equality_token,
                    encryption_key_version, encrypted_value, first_seen_at, last_seen_at, session_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE
                    encryption_key_version = VALUES(encryption_key_version),
                    encrypted_value = VALUES(encrypted_value),
                    first_seen_at = LEAST(first_seen_at, VALUES(first_seen_at)),
                    last_seen_at = GREATEST(last_seen_at, VALUES(last_seen_at))
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(playerId));
            statement.setInt(3, identity.equalityKeyVersion());
            statement.setBytes(4, identity.equalityToken());
            statement.setInt(5, identity.encryptionKeyVersion());
            statement.setBytes(6, identity.encryptedValue());
            statement.setTimestamp(7, Timestamp.from(observedAt));
            statement.setTimestamp(8, Timestamp.from(observedAt));
            statement.executeUpdate();
        }
    }

    private TargetResolution resolveTarget(LegacySanction legacy) {
        if (legacy.playerId().isPresent()) {
            return new TargetResolution(legacy.playerId(), "");
        }
        List<UUID> usernameCandidates = legacy.username()
                .map(this::playerIdsForUsername)
                .orElseGet(List::of);
        if (usernameCandidates.size() == 1) {
            return new TargetResolution(Optional.of(usernameCandidates.getFirst()), "");
        }
        if (legacy.type() == LegacySanctionType.IP_BAN && legacy.networkAddress().isPresent()) {
            List<UUID> networkCandidates = playerIdsForNetworkAddress(legacy.networkAddress().orElseThrow());
            if (usernameCandidates.size() > 1) {
                networkCandidates = networkCandidates.stream().filter(usernameCandidates::contains).toList();
            }
            if (networkCandidates.size() == 1) {
                return new TargetResolution(Optional.of(networkCandidates.getFirst()), "");
            }
            if (networkCandidates.size() > 1) {
                return new TargetResolution(Optional.empty(), "AMBIGUOUS_NETWORK_IDENTITY");
            }
        }
        if (usernameCandidates.size() > 1) {
            return new TargetResolution(Optional.empty(), "AMBIGUOUS_USERNAME_IDENTITY");
        }
        return new TargetResolution(
                Optional.empty(),
                legacy.type() == LegacySanctionType.IP_BAN
                        ? "UNRESOLVED_NETWORK_IDENTITY"
                        : "UNRESOLVED_PLAYER_IDENTITY"
        );
    }

    private List<UUID> playerIdsForUsername(String username) {
        if (!username.matches("[A-Za-z0-9_]{1,32}")) {
            return List.of();
        }
        try (Connection connection = target.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT DISTINCT p.player_id
                     FROM players p
                     LEFT JOIN player_names n ON n.player_id = p.player_id
                     WHERE p.lowercase_username = LOWER(?) OR n.lowercase_username = LOWER(?)
                     LIMIT 2
                     """)) {
            statement.setString(1, username);
            statement.setString(2, username);
            try (ResultSet result = statement.executeQuery()) {
                List<UUID> candidates = new ArrayList<>();
                while (result.next()) {
                    candidates.add(UuidBytes.fromBytes(result.getBytes(1)));
                }
                return List.copyOf(candidates);
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new ModerationPersistenceException("Unable to resolve a legacy username safely", exception);
        }
    }

    private List<UUID> playerIdsForNetworkAddress(LegacyNetworkAddress address) {
        ProtectedNetworkIdentity identity = protect(address);
        try (Connection connection = target.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT DISTINCT player_id
                     FROM network_identity_tokens
                     WHERE hmac_key_version = ? AND equality_token = ?
                     LIMIT 2
                     """)) {
            statement.setInt(1, identity.equalityKeyVersion());
            statement.setBytes(2, identity.equalityToken());
            try (ResultSet result = statement.executeQuery()) {
                List<UUID> candidates = new ArrayList<>();
                while (result.next()) {
                    candidates.add(UuidBytes.fromBytes(result.getBytes(1)));
                }
                return List.copyOf(candidates);
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new ModerationPersistenceException("Unable to resolve a legacy network identity safely", exception);
        }
    }

    private ImportResult reconcile(
            Connection connection,
            UUID runId,
            LockedMapping mapping,
            LegacySanction legacy,
            UUID targetId,
            String checksum,
            Instant now
    ) throws SQLException, JsonProcessingException {
        if (mapping.sourceChecksum().equals(checksum)) {
            updateMappingSeen(connection, runId, legacy, checksum, now);
            return ImportResult.replayed();
        }
        MappedSanction current = lockMappedSanction(connection, mapping.caseId());
        String conflict = immutableConflict(current, legacy, targetId);
        if (conflict != null) {
            return ImportResult.rejected(conflict);
        }
        LegacyProjection projection = project(legacy, now);
        try (PreparedStatement sanction = connection.prepareStatement("""
                UPDATE sanctions
                SET status = ?, expiration_at = ?, ended_at = ?, revision = revision + 1
                WHERE sanction_id = ?
                """);
             PreparedStatement caseState = connection.prepareStatement("""
                UPDATE cases SET state = ?, revision = revision + 1 WHERE case_id = ?
                """)) {
            sanction.setString(1, projection.status().name());
            setOptionalTimestamp(sanction, 2, legacy.expiresAt());
            setOptionalTimestamp(sanction, 3, projection.endedAt());
            sanction.setBytes(4, UuidBytes.toBytes(current.sanctionId()));
            if (sanction.executeUpdate() != 1) {
                throw new SQLException("mapped LiteBans sanction disappeared during reconciliation");
            }
            caseState.setString(1, projection.caseOpen() ? "OPEN" : "CLOSED");
            caseState.setString(2, mapping.caseId().value());
            if (caseState.executeUpdate() != 1) {
                throw new SQLException("mapped LiteBans case disappeared during reconciliation");
            }
        }
        insertReconciliationEvent(connection, runId, current, projection, legacy, now);
        updateMappingSeen(connection, runId, legacy, checksum, now);
        insertReconciliationAudit(connection, runId, mapping.caseId(), targetId, current, projection, now);
        return ImportResult.reconciled();
    }

    private static LockedMapping lockMapping(Connection connection, LegacySanction legacy) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT case_id, source_checksum
                FROM migration_mappings
                WHERE source_system = 'LITEBANS' AND source_table = ? AND external_id = ?
                FOR UPDATE
                """)) {
            statement.setString(1, legacy.sourceTable());
            statement.setString(2, legacy.externalId());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getString("case_id") == null) {
                    return null;
                }
                return new LockedMapping(
                        new CaseId(result.getString("case_id")),
                        result.getString("source_checksum")
                );
            }
        }
    }

    private static boolean mappingExists(Connection connection, LegacySanction legacy) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM migration_mappings
                WHERE source_system = 'LITEBANS' AND source_table = ? AND external_id = ?
                """)) {
            statement.setString(1, legacy.sourceTable());
            statement.setString(2, legacy.externalId());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static MappedSanction lockMappedSanction(Connection connection, CaseId caseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT c.target_id, c.actor_name, c.public_reason, c.issued_at,
                       s.sanction_id, s.sanction_type, s.status, s.expiration_at, s.ended_at
                FROM cases c
                JOIN sanctions s ON s.case_id = c.case_id
                WHERE c.case_id = ?
                FOR UPDATE
                """)) {
            statement.setString(1, caseId.value());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("mapped LiteBans case or sanction is missing");
                }
                Timestamp expiration = result.getTimestamp("expiration_at");
                Timestamp ended = result.getTimestamp("ended_at");
                MappedSanction mapped = new MappedSanction(
                        UuidBytes.fromBytes(result.getBytes("target_id")),
                        result.getString("actor_name"),
                        result.getString("public_reason"),
                        result.getTimestamp("issued_at").toInstant(),
                        UuidBytes.fromBytes(result.getBytes("sanction_id")),
                        SanctionType.valueOf(result.getString("sanction_type")),
                        SanctionStatus.valueOf(result.getString("status")),
                        expiration == null ? Optional.empty() : Optional.of(expiration.toInstant()),
                        ended == null ? Optional.empty() : Optional.of(ended.toInstant())
                );
                if (result.next()) {
                    throw new SQLException("mapped LiteBans case contains multiple sanctions");
                }
                return mapped;
            } catch (IllegalArgumentException exception) {
                throw new SQLException("mapped LiteBans sanction contains an unknown state", exception);
            }
        }
    }

    private static String immutableConflict(
            MappedSanction current,
            LegacySanction legacy,
            UUID resolvedTargetId
    ) {
        if (!current.targetId().equals(resolvedTargetId)) {
            return "SOURCE_IDENTITY_CHANGED";
        }
        if (current.type() != sanctionType(legacy.type())) {
            return "SOURCE_SANCTION_TYPE_CHANGED";
        }
        if (!current.issuedAt().equals(legacy.issuedAt())) {
            return "SOURCE_ISSUED_AT_CHANGED";
        }
        if (!current.actorName().equals(truncate(legacy.originalStaffName(), 64))) {
            return "SOURCE_ACTOR_CHANGED";
        }
        if (!current.publicReason().equals(truncate(legacy.originalReason(), 160))) {
            return "SOURCE_REASON_CHANGED";
        }
        return null;
    }

    static LegacyProjection project(LegacySanction legacy, Instant now) {
        boolean naturallyExpired = legacy.expiresAt().filter(expiration -> !expiration.isAfter(now)).isPresent();
        if (legacy.active() && !naturallyExpired) {
            return new LegacyProjection(SanctionStatus.ACTIVE, Optional.empty(), true);
        }
        boolean endedBeforeExpiration = legacy.endedAt().isPresent() && legacy.expiresAt().isPresent()
                && legacy.endedAt().orElseThrow().isBefore(legacy.expiresAt().orElseThrow());
        boolean endedEarly = !legacy.active() && (legacy.expiresAt().isEmpty()
                || !naturallyExpired || endedBeforeExpiration);
        if (endedEarly) {
            return new LegacyProjection(
                    SanctionStatus.ENDED_EARLY,
                    Optional.of(legacy.endedAt().orElse(now)),
                    false
            );
        }
        return new LegacyProjection(
                SanctionStatus.EXPIRED,
                Optional.of(legacy.endedAt().or(() -> legacy.expiresAt()).orElse(now)),
                false
        );
    }

    private static void setOptionalTimestamp(
            PreparedStatement statement,
            int index,
            Optional<Instant> value
    ) throws SQLException {
        if (value.isPresent()) {
            statement.setTimestamp(index, Timestamp.from(value.orElseThrow()));
        } else {
            statement.setNull(index, Types.TIMESTAMP);
        }
    }

    private static void updateMappingSeen(
            Connection connection,
            UUID runId,
            LegacySanction legacy,
            String checksum,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE migration_mappings
                SET source_checksum = ?, mapping_state = 'IMPORTED',
                    last_seen_run_id = ?, last_seen_at = ?
                WHERE source_system = 'LITEBANS' AND source_table = ? AND external_id = ?
                """)) {
            statement.setString(1, checksum);
            statement.setBytes(2, UuidBytes.toBytes(runId));
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setString(4, legacy.sourceTable());
            statement.setString(5, legacy.externalId());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("mapped LiteBans record disappeared during reconciliation");
            }
        }
    }

    private boolean mappingExistsAfterConflict(LegacySanction legacy) {
        try (Connection connection = target.getConnection()) {
            return mappingExists(connection, legacy);
        } catch (SQLException exception) {
            return false;
        }
    }

    private static void ensurePlayer(
            Connection connection,
            UUID targetId,
            Optional<String> username,
            Instant firstSeen
    ) throws SQLException {
        Optional<String> validUsername = username.filter(value -> value.matches("[A-Za-z0-9_]{1,32}"));
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT IGNORE INTO players(player_id, current_username, lowercase_username, first_seen_at, last_seen_at)
                VALUES (?, ?, LOWER(?), ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(targetId));
            if (validUsername.isPresent()) {
                statement.setString(2, validUsername.orElseThrow());
                statement.setString(3, validUsername.orElseThrow());
            } else {
                statement.setNull(2, Types.VARCHAR);
                statement.setNull(3, Types.VARCHAR);
            }
            statement.setTimestamp(4, Timestamp.from(firstSeen));
            statement.setTimestamp(5, Timestamp.from(firstSeen));
            statement.executeUpdate();
        }
        if (validUsername.isPresent()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT IGNORE INTO player_names(
                        player_id, username, lowercase_username, first_seen_at, last_seen_at
                    ) VALUES (?, ?, LOWER(?), ?, ?)
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(targetId));
                statement.setString(2, validUsername.orElseThrow());
                statement.setString(3, validUsername.orElseThrow());
                statement.setTimestamp(4, Timestamp.from(firstSeen));
                statement.setTimestamp(5, Timestamp.from(firstSeen));
                statement.executeUpdate();
            }
        }
    }

    private static void insertCase(
            Connection connection,
            CaseId caseId,
            UUID targetId,
            LegacySanction legacy,
            LegacyProjection projection
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO cases(case_id, idempotency_key, target_id, actor_id, actor_name, actor_rank,
                    public_reason, exact_reason_id, sanction_family, internal_explanation,
                    configuration_version, visibility, state, issued_at)
                VALUES (?, ?, ?, ?, ?, 'SYSTEM', ?, ?, 'legacy', ?, 'litebans-import-v1', 'PUBLIC', ?, ?)
                """)) {
            statement.setString(1, caseId.value());
            statement.setString(2, "litebans:" + legacy.sourceTable() + ':' + legacy.externalId());
            statement.setBytes(3, UuidBytes.toBytes(targetId));
            statement.setBytes(4, UuidBytes.toBytes(SYSTEM_ACTOR));
            statement.setString(5, truncate(legacy.originalStaffName(), 64));
            statement.setString(6, truncate(legacy.originalReason(), 160));
            statement.setString(7, "legacy.litebans." + legacy.type().name().toLowerCase(java.util.Locale.ROOT));
            statement.setString(8, "Imported from LiteBans without changing the original reason or expiration");
            statement.setString(9, projection.caseOpen() ? "OPEN" : "CLOSED");
            statement.setTimestamp(10, Timestamp.from(legacy.issuedAt()));
            statement.executeUpdate();
        }
    }

    private static void insertStep(Connection connection, CaseId caseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO punishment_steps(case_id, raw_ordinal, effective_ordinal, recency_bonus,
                    step_label, contribution_json, escalation_contributes)
                VALUES (?, 0, 0, 0, 'Imported LiteBans sanction', '[]', TRUE)
                """)) {
            statement.setString(1, caseId.value());
            statement.executeUpdate();
        }
    }

    private static UUID insertSanction(
            Connection connection,
            CaseId caseId,
            UUID targetId,
            LegacySanction legacy,
            LegacyProjection projection
    ) throws SQLException {
        UUID sanctionId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sanctions(sanction_id, case_id, target_id, sanction_type, status,
                    issued_at, activated_at, expiration_at, ended_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(sanctionId));
            statement.setString(2, caseId.value());
            statement.setBytes(3, UuidBytes.toBytes(targetId));
            statement.setString(4, sanctionType(legacy.type()).name());
            statement.setString(5, projection.status().name());
            statement.setTimestamp(6, Timestamp.from(legacy.issuedAt()));
            statement.setTimestamp(7, Timestamp.from(legacy.issuedAt()));
            setOptionalTimestamp(statement, 8, legacy.expiresAt());
            setOptionalTimestamp(statement, 9, projection.endedAt());
            statement.executeUpdate();
        }
        return sanctionId;
    }

    private static void insertEvent(Connection connection, UUID sanctionId, Instant occurredAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sanction_events(event_id, sanction_id, event_type, actor_id, occurred_at, event_json)
                VALUES (?, ?, 'IMPORTED', ?, ?, '{"source":"LITEBANS"}')
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(sanctionId));
            statement.setBytes(3, UuidBytes.toBytes(SYSTEM_ACTOR));
            statement.setTimestamp(4, Timestamp.from(occurredAt));
            statement.executeUpdate();
        }
    }

    private void insertReconciliationEvent(
            Connection connection,
            UUID runId,
            MappedSanction current,
            LegacyProjection projection,
            LegacySanction legacy,
            Instant now
    ) throws SQLException, JsonProcessingException {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("source", "LITEBANS");
        payload.put("sourceTable", legacy.sourceTable());
        payload.put("externalId", legacy.externalId());
        payload.put("previousStatus", current.status().name());
        payload.put("newStatus", projection.status().name());
        payload.put("previousExpiration", current.expiresAt().map(Instant::toString).orElse("PERMANENT"));
        payload.put("newExpiration", legacy.expiresAt().map(Instant::toString).orElse("PERMANENT"));
        payload.put("previousEndedAt", current.endedAt().map(Instant::toString).orElse("ACTIVE"));
        payload.put("newEndedAt", projection.endedAt().map(Instant::toString).orElse("ACTIVE"));
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sanction_events(event_id, sanction_id, event_type, actor_id, occurred_at,
                    reason, event_json, idempotency_key)
                VALUES (?, ?, 'LEGACY_SYNC', ?, ?, 'LiteBans shadow reconciliation', ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(current.sanctionId()));
            statement.setBytes(3, UuidBytes.toBytes(SYSTEM_ACTOR));
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setString(5, json.writeValueAsString(payload));
            statement.setString(6, "litebans-sync:" + runId + ':' + current.sanctionId());
            statement.executeUpdate();
        }
    }

    private void insertReconciliationAudit(
            Connection connection,
            UUID runId,
            CaseId caseId,
            UUID targetId,
            MappedSanction current,
            LegacyProjection projection,
            Instant now
    ) throws SQLException, JsonProcessingException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_events(event_id, correlation_id, actor_id, target_id, case_id,
                    event_type, outcome, event_json, occurred_at)
                VALUES (?, ?, ?, ?, ?, 'LITEBANS_RECORD_RECONCILED', 'COMMITTED', ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(runId));
            statement.setBytes(3, UuidBytes.toBytes(SYSTEM_ACTOR));
            statement.setBytes(4, UuidBytes.toBytes(targetId));
            statement.setString(5, caseId.value());
            statement.setString(6, json.writeValueAsString(Map.of(
                    "sanctionId", current.sanctionId().toString(),
                    "previousStatus", current.status().name(),
                    "newStatus", projection.status().name()
            )));
            statement.setTimestamp(7, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void insertMapping(
            Connection connection,
            UUID runId,
            LegacySanction legacy,
            CaseId caseId,
            String checksum,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO migration_mappings(mapping_id, run_id, last_seen_run_id, source_system, source_table,
                    external_id, case_id, source_checksum, mapping_state, created_at, last_seen_at)
                VALUES (?, ?, ?, 'LITEBANS', ?, ?, ?, ?, 'IMPORTED', ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(runId));
            statement.setBytes(3, UuidBytes.toBytes(runId));
            statement.setString(4, legacy.sourceTable());
            statement.setString(5, legacy.externalId());
            statement.setString(6, caseId.value());
            statement.setString(7, checksum);
            statement.setTimestamp(8, Timestamp.from(now));
            statement.setTimestamp(9, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private void insertAudit(
            Connection connection,
            UUID runId,
            CaseId caseId,
            UUID targetId,
            LegacySanction legacy,
            UUID sanctionId,
            Instant now
    ) throws SQLException, JsonProcessingException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_events(event_id, correlation_id, actor_id, target_id, case_id,
                    event_type, outcome, event_json, occurred_at)
                VALUES (?, ?, ?, ?, ?, 'LITEBANS_RECORD_IMPORTED', 'COMMITTED', ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(runId));
            statement.setBytes(3, UuidBytes.toBytes(SYSTEM_ACTOR));
            statement.setBytes(4, UuidBytes.toBytes(targetId));
            statement.setString(5, caseId.value());
            statement.setString(6, json.writeValueAsString(Map.of(
                    "sourceTable", legacy.sourceTable(),
                    "externalId", legacy.externalId(),
                    "sanctionId", sanctionId.toString()
            )));
            statement.setTimestamp(7, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    static SanctionType sanctionType(LegacySanctionType type) {
        return switch (type) {
            case BAN -> SanctionType.NETWORK_BAN;
            case MUTE -> SanctionType.MUTE;
            case IP_BAN -> SanctionType.NETWORK_IDENTITY_BAN;
        };
    }

    private static String truncate(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static void restoreAutoCommit(Connection connection) {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
            // Closing returns the connection to the pool; the original failure stays authoritative.
        }
    }

    boolean protectedIdentityExists(UUID playerId, LegacyNetworkAddress address) {
        ProtectedNetworkIdentity identity = protect(address);
        try (Connection connection = target.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT EXISTS(
                         SELECT 1 FROM network_identity_tokens
                         WHERE player_id = ? AND hmac_key_version = ? AND equality_token = ?
                     )
                     """)) {
            statement.setBytes(1, UuidBytes.toBytes(playerId));
            statement.setInt(2, identity.equalityKeyVersion());
            statement.setBytes(3, identity.equalityToken());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to compare a protected LiteBans IP-ban decision", exception);
        }
    }

    record TargetImportReport(
            long imported,
            long reconciled,
            long replayed,
            long protectedIdentityRecords,
            List<LiteBansReadReport.RejectedRow> rejectedRows
    ) {
        TargetImportReport {
            rejectedRows = List.copyOf(rejectedRows);
        }
    }

    private enum ImportOutcome {
        IMPORTED,
        RECONCILED,
        REPLAYED,
        REJECTED
    }

    private record ImportResult(ImportOutcome outcome, String rejectionCode) {
        private static ImportResult imported() {
            return new ImportResult(ImportOutcome.IMPORTED, "");
        }

        private static ImportResult reconciled() {
            return new ImportResult(ImportOutcome.RECONCILED, "");
        }

        private static ImportResult replayed() {
            return new ImportResult(ImportOutcome.REPLAYED, "");
        }

        private static ImportResult rejected(String code) {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("migration rejection code must be present");
            }
            return new ImportResult(ImportOutcome.REJECTED, code);
        }
    }

    private record TargetResolution(Optional<UUID> playerId, String rejectionCode) {
    }

    private record LockedMapping(CaseId caseId, String sourceChecksum) {
    }

    private record MappedSanction(
            UUID targetId,
            String actorName,
            String publicReason,
            Instant issuedAt,
            UUID sanctionId,
            SanctionType type,
            SanctionStatus status,
            Optional<Instant> expiresAt,
            Optional<Instant> endedAt
    ) {
    }

    record LegacyProjection(SanctionStatus status, Optional<Instant> endedAt, boolean caseOpen) {
    }
}
