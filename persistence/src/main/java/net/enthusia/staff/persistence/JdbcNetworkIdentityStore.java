package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.security.NetworkAddressTextGuard;
import net.enthusia.staff.common.security.ProtectedNetworkIdentity;
import net.enthusia.staff.domain.alt.AltRelationshipState;
import net.enthusia.staff.domain.alt.AltRelationshipSummary;
import net.enthusia.staff.domain.alt.AltInheritancePolicy;
import net.enthusia.staff.domain.alt.NetworkIdentityObservationResult;
import net.enthusia.staff.domain.alt.NetworkIdentityRetentionResult;
import net.enthusia.staff.domain.ports.NetworkIdentityStore;
import net.enthusia.staff.domain.sanction.SanctionType;

public final class JdbcNetworkIdentityStore implements NetworkIdentityStore {
    private static final int PROTOCOL_VERSION = 1;
    private static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);
    private static final int MAX_AUTOMATED_MATCHES = 20;
    private static final int MATCH_QUERY_LIMIT = MAX_AUTOMATED_MATCHES + 1;
    private static final int MAX_RETENTION_BATCH_SIZE = 5_000;
    private static final int AUTOMATIC_RETENTION_BATCH_SIZE = 500;
    private static final Duration SENSITIVE_RETENTION = Duration.ofDays(90);
    private static final Duration AUTOMATIC_RETENTION_INTERVAL = Duration.ofHours(1);
    private static final Duration EVIDENCE_REFRESH_INTERVAL = Duration.ofHours(24);
    private static final List<SanctionType> INHERITABLE_SANCTION_TYPES = Arrays.stream(SanctionType.values())
            .filter(SanctionType::inheritsAcrossAltRelationships)
            .toList();

    private final DataSource dataSource;
    private final ObjectMapper json;
    private final AltInheritancePolicy inheritancePolicy = new AltInheritancePolicy();
    private Instant nextAutomaticRetentionAt = Instant.EPOCH;

    public JdbcNetworkIdentityStore(DataSource dataSource, ObjectMapper json) {
        if (dataSource == null || json == null) {
            throw new IllegalArgumentException("network identity store dependencies must be present");
        }
        this.dataSource = dataSource;
        this.json = json;
    }

    @Override
    public NetworkIdentityObservationResult observeAndInherit(
            UUID joiningPlayerId,
            ProtectedNetworkIdentity identity,
            Instant observedAt,
            boolean suppressAutomatedEvidence
    ) {
        if (joiningPlayerId == null || identity == null || observedAt == null) {
            throw new IllegalArgumentException("network identity observation fields must be present");
        }
        boolean automaticRetentionReserved = false;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Instant firstSeenAt = lockPlayerFirstSeen(connection, joiningPlayerId);
                upsertIdentity(connection, joiningPlayerId, identity, observedAt);
                if (suppressAutomatedEvidence) {
                    connection.commit();
                    return new NetworkIdentityObservationResult(0, 0, 0, true);
                }

                automaticRetentionReserved = reserveAutomaticRetention(observedAt);
                if (automaticRetentionReserved) {
                    purgeExpired(
                            connection,
                            observedAt.minus(SENSITIVE_RETENTION),
                            AUTOMATIC_RETENTION_BATCH_SIZE
                    );
                }

                Optional<Instant> cutoverAt = latestCutover(connection);
                List<MatchingPlayer> matches = matchingPlayers(connection, joiningPlayerId, identity);
                if (matches.size() > MAX_AUTOMATED_MATCHES) {
                    connection.commit();
                    return new NetworkIdentityObservationResult(matches.size(), 0, 0, true);
                }

                boolean singleNetworkCandidate = matches.size() == 1;
                boolean hasProtectedHistory = hasProtectedRelationshipHistory(connection, joiningPlayerId);
                int inherited = 0;
                int alerts = 0;
                for (MatchingPlayer match : matches) {
                    UUID otherPlayerId = match.playerId();
                    Relationship relationship = lockOrCreateRelationship(
                            connection, joiningPlayerId, otherPlayerId, observedAt
                    );
                    if (match.currentlyOnline() && !relationship.manuallyManaged()) {
                        relationship = lowerAutomaticConfidence(connection, relationship, observedAt);
                        insertEvidenceIfDue(
                                connection,
                                relationship,
                                "SIMULTANEOUS_PLAY",
                                -0.2500,
                                "PLAYER_DIRECTORY_PRESENCE",
                                observedAt
                        );
                    } else {
                        insertEvidenceIfDue(
                                connection,
                                relationship,
                                "SAME_NETWORK",
                                0.2500,
                                "PROXY_IDENTITY_TOKEN",
                                observedAt
                        );
                    }

                    List<SourceSanction> active = activeInheritableSanctions(connection, otherPlayerId, observedAt);
                    if (active.isEmpty()) {
                        continue;
                    }
                    boolean unambiguousNewAccountEvidence = relationship.created()
                            && singleNetworkCandidate
                            && !match.currentlyOnline();
                    boolean shouldInherit = inheritancePolicy.shouldInherit(
                            relationship.state(),
                            unambiguousNewAccountEvidence,
                            firstSeenAt,
                            cutoverAt,
                            hasProtectedHistory
                    );
                    if (shouldInherit) {
                        for (SourceSanction source : active) {
                            if (inherit(connection, joiningPlayerId, otherPlayerId, source, relationship.state(), observedAt)) {
                                inherited++;
                                alerts++;
                            }
                        }
                    } else if (!relationship.state().preventsAutomaticInheritance()) {
                        alerts += insertLowerConfidenceAlert(
                                connection,
                                joiningPlayerId,
                                otherPlayerId,
                                relationship.state(),
                                active,
                                observedAt
                        );
                    }
                }
                connection.commit();
                return new NetworkIdentityObservationResult(matches.size(), inherited, alerts, false);
            } catch (SQLException | JsonProcessingException exception) {
                if (automaticRetentionReserved) {
                    releaseAutomaticRetentionReservation(observedAt);
                }
                rollback(connection, exception);
                throw new ModerationPersistenceException("Network identity observation transaction failed", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            if (automaticRetentionReserved) {
                releaseAutomaticRetentionReservation(observedAt);
            }
            throw new ModerationPersistenceException("Unable to open network identity transaction", exception);
        }
    }

    @Override
    public List<AltRelationshipSummary> relationships(UUID playerId) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId must be present");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT lower_player_id, upper_player_id, relationship_state, confidence,
                            locked_until_reopened, updated_at
                     FROM alt_relationships
                     WHERE lower_player_id = ? OR upper_player_id = ?
                     ORDER BY confidence DESC, updated_at DESC
                     LIMIT 100
                     """)) {
            byte[] player = UuidBytes.toBytes(playerId);
            statement.setBytes(1, player);
            statement.setBytes(2, player);
            try (ResultSet result = statement.executeQuery()) {
                List<AltRelationshipSummary> relationships = new ArrayList<>();
                while (result.next()) {
                    UUID lower = UuidBytes.fromBytes(result.getBytes("lower_player_id"));
                    UUID upper = UuidBytes.fromBytes(result.getBytes("upper_player_id"));
                    relationships.add(new AltRelationshipSummary(
                            lower.equals(playerId) ? upper : lower,
                            AltRelationshipState.valueOf(result.getString("relationship_state")),
                            result.getBigDecimal("confidence").doubleValue(),
                            result.getBoolean("locked_until_reopened"),
                            result.getTimestamp("updated_at").toInstant()
                    ));
                }
                return List.copyOf(relationships);
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new ModerationPersistenceException("Unable to list alt relationships", exception);
        }
    }

    @Override
    public boolean setRelationship(
            UUID firstPlayerId,
            UUID secondPlayerId,
            AltRelationshipState state,
            UUID actorId,
            Instant changedAt,
            String reason
    ) {
        validateManualChange(firstPlayerId, secondPlayerId, state, actorId, changedAt, reason);
        PlayerPair pair = ordered(firstPlayerId, secondPlayerId);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePlayers(connection, pair);
                Relationship existing = lockRelationship(connection, pair);
                if (existing != null && existing.locked() && existing.state() == AltRelationshipState.NOT_RELATED
                        && state != AltRelationshipState.NOT_RELATED) {
                    connection.rollback();
                    return false;
                }
                if (existing == null) {
                    insertRelationship(connection, pair, state, actorId, changedAt);
                } else {
                    updateRelationship(connection, pair, state, actorId, changedAt, state == AltRelationshipState.NOT_RELATED);
                }
                insertRelationshipAudit(connection, pair, actorId, state.name(), changedAt, reason);
                connection.commit();
                return true;
            } catch (SQLException | JsonProcessingException exception) {
                rollback(connection, exception);
                throw new ModerationPersistenceException("Alt relationship change transaction failed", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to open alt relationship transaction", exception);
        }
    }

    @Override
    public boolean reopen(UUID firstPlayerId, UUID secondPlayerId, UUID actorId, Instant changedAt, String reason) {
        validateManualChange(firstPlayerId, secondPlayerId, AltRelationshipState.LOW_CONFIDENCE,
                actorId, changedAt, reason);
        PlayerPair pair = ordered(firstPlayerId, secondPlayerId);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE alt_relationships
                    SET relationship_state = 'LOW_CONFIDENCE', confidence = 0.2000,
                        locked_until_reopened = FALSE, updated_by = ?, updated_at = ?, revision = revision + 1
                    WHERE lower_player_id = ? AND upper_player_id = ?
                      AND relationship_state = 'NOT_RELATED' AND locked_until_reopened = TRUE
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(actorId));
                statement.setTimestamp(2, Timestamp.from(changedAt));
                statement.setBytes(3, UuidBytes.toBytes(pair.lower()));
                statement.setBytes(4, UuidBytes.toBytes(pair.upper()));
                boolean changed = statement.executeUpdate() == 1;
                if (changed) {
                    insertRelationshipAudit(connection, pair, actorId, "REOPENED", changedAt, reason);
                }
                connection.commit();
                return changed;
            } catch (SQLException | JsonProcessingException exception) {
                rollback(connection, exception);
                throw new ModerationPersistenceException("Alt relationship reopen transaction failed", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to open alt relationship transaction", exception);
        }
    }

    @Override
    public NetworkIdentityRetentionResult purgeExpired(Instant cutoff, int batchSize) {
        validateRetentionRequest(cutoff, batchSize);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                NetworkIdentityRetentionResult result = purgeExpired(connection, cutoff, batchSize);
                connection.commit();
                return result;
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw new ModerationPersistenceException("Network identity retention transaction failed", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to open network identity retention transaction", exception);
        }
    }

    private static Instant lockPlayerFirstSeen(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT first_seen_at FROM players WHERE player_id = ? FOR UPDATE")) {
            statement.setBytes(1, UuidBytes.toBytes(playerId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Player directory record is missing");
                }
                return result.getTimestamp(1).toInstant();
            }
        }
    }

    private static void upsertIdentity(
            Connection connection,
            UUID playerId,
            ProtectedNetworkIdentity identity,
            Instant observedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO network_identity_tokens(token_id, player_id, hmac_key_version, equality_token,
                    encryption_key_version, encrypted_value, first_seen_at, last_seen_at, session_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE encryption_key_version = VALUES(encryption_key_version),
                    encrypted_value = VALUES(encrypted_value), last_seen_at = GREATEST(last_seen_at, VALUES(last_seen_at)),
                    session_count = session_count + 1
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

    private static Optional<Instant> latestCutover(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT MAX(authorized_at) FROM cutover_records");
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                return Optional.empty();
            }
            Timestamp value = result.getTimestamp(1);
            return value == null ? Optional.empty() : Optional.of(value.toInstant());
        }
    }

    private static List<MatchingPlayer> matchingPlayers(
            Connection connection,
            UUID joiningPlayerId,
            ProtectedNetworkIdentity identity
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT DISTINCT n.player_id,
                       CASE WHEN p.current_server IS NULL THEN FALSE ELSE TRUE END AS currently_online
                FROM network_identity_tokens n
                JOIN players p ON p.player_id = n.player_id
                WHERE n.hmac_key_version = ? AND n.equality_token = ? AND n.player_id <> ?
                ORDER BY n.player_id
                LIMIT ?
                """)) {
            statement.setInt(1, identity.equalityKeyVersion());
            statement.setBytes(2, identity.equalityToken());
            statement.setBytes(3, UuidBytes.toBytes(joiningPlayerId));
            statement.setInt(4, MATCH_QUERY_LIMIT);
            try (ResultSet result = statement.executeQuery()) {
                List<MatchingPlayer> players = new ArrayList<>();
                while (result.next()) {
                    players.add(new MatchingPlayer(
                            UuidBytes.fromBytes(result.getBytes("player_id")),
                            result.getBoolean("currently_online")
                    ));
                }
                return List.copyOf(players);
            }
        }
    }

    private static boolean hasProtectedRelationshipHistory(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS(
                    SELECT 1 FROM alt_relationships
                    WHERE (lower_player_id = ? OR upper_player_id = ?)
                      AND relationship_state IN ('APPROVED_ALT', 'SHARED_HOUSEHOLD', 'NOT_RELATED')
                )
                """)) {
            byte[] id = UuidBytes.toBytes(playerId);
            statement.setBytes(1, id);
            statement.setBytes(2, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    private static Relationship lockOrCreateRelationship(
            Connection connection,
            UUID first,
            UUID second,
            Instant observedAt
    ) throws SQLException {
        PlayerPair pair = ordered(first, second);
        Relationship relationship = lockRelationship(connection, pair);
        if (relationship != null) {
            return relationship;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT IGNORE INTO alt_relationships(relationship_id, lower_player_id, upper_player_id,
                    relationship_state, confidence, locked_until_reopened, updated_at)
                VALUES (?, ?, ?, 'SAME_NETWORK', 0.2500, FALSE, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(pair.lower()));
            statement.setBytes(3, UuidBytes.toBytes(pair.upper()));
            statement.setTimestamp(4, Timestamp.from(observedAt));
            boolean created = statement.executeUpdate() == 1;
            Relationship locked = lockRelationship(connection, pair);
            if (locked == null) {
                throw new SQLException("Alt relationship could not be created");
            }
            return new Relationship(
                    locked.id(),
                    locked.state(),
                    locked.locked(),
                    locked.manuallyManaged(),
                    created
            );
        }
    }

    private static Relationship lockRelationship(Connection connection, PlayerPair pair) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT relationship_id, relationship_state, locked_until_reopened, updated_by
                FROM alt_relationships
                WHERE lower_player_id = ? AND upper_player_id = ? FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(pair.lower()));
            statement.setBytes(2, UuidBytes.toBytes(pair.upper()));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new Relationship(
                        UuidBytes.fromBytes(result.getBytes("relationship_id")),
                        AltRelationshipState.valueOf(result.getString("relationship_state")),
                        result.getBoolean("locked_until_reopened"),
                        result.getBytes("updated_by") != null,
                        false
                ) : null;
            }
        }
    }

    private static Relationship lowerAutomaticConfidence(
            Connection connection,
            Relationship relationship,
            Instant observedAt
    ) throws SQLException {
        if (relationship.manuallyManaged() || relationship.state() != AltRelationshipState.SAME_NETWORK) {
            return relationship;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE alt_relationships
                SET relationship_state = 'LOW_CONFIDENCE', confidence = 0.2000,
                    updated_at = GREATEST(updated_at, ?), revision = revision + 1
                WHERE relationship_id = ? AND updated_by IS NULL AND relationship_state = 'SAME_NETWORK'
                """)) {
            statement.setTimestamp(1, Timestamp.from(observedAt));
            statement.setBytes(2, UuidBytes.toBytes(relationship.id()));
            statement.executeUpdate();
        }
        return new Relationship(
                relationship.id(),
                AltRelationshipState.LOW_CONFIDENCE,
                relationship.locked(),
                false,
                relationship.created()
        );
    }

    private void insertEvidenceIfDue(
            Connection connection,
            Relationship relationship,
            String evidenceType,
            double weight,
            String source,
            Instant observedAt
    ) throws SQLException, JsonProcessingException {
        Instant threshold = observedAt.minus(EVIDENCE_REFRESH_INTERVAL);
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT MAX(observed_at)
                FROM alt_evidence
                WHERE relationship_id = ? AND evidence_type = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(relationship.id()));
            statement.setString(2, evidenceType);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    Timestamp latest = result.getTimestamp(1);
                    if (latest != null && !latest.toInstant().isBefore(threshold)) {
                        return;
                    }
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO alt_evidence(evidence_id, relationship_id, evidence_type, weight,
                    evidence_json, observed_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(relationship.id()));
            statement.setString(3, evidenceType);
            statement.setDouble(4, weight);
            statement.setString(5, json.writeValueAsString(Map.of("source", source)));
            statement.setTimestamp(6, Timestamp.from(observedAt));
            statement.executeUpdate();
        }
        if (!relationship.manuallyManaged()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE alt_relationships
                    SET updated_at = GREATEST(updated_at, ?), revision = revision + 1
                    WHERE relationship_id = ? AND updated_by IS NULL
                    """)) {
                statement.setTimestamp(1, Timestamp.from(observedAt));
                statement.setBytes(2, UuidBytes.toBytes(relationship.id()));
                statement.executeUpdate();
            }
        }
    }

    private static List<SourceSanction> activeInheritableSanctions(
            Connection connection,
            UUID playerId,
            Instant now
    ) throws SQLException {
        String placeholders = String.join(",", Collections.nCopies(INHERITABLE_SANCTION_TYPES.size(), "?"));
        String sql = """
                SELECT s.sanction_id, s.case_id, s.sanction_type, s.expiration_at
                FROM sanctions s JOIN cases c ON c.case_id = s.case_id
                WHERE s.target_id = ? AND s.status = 'ACTIVE'
                  AND s.sanction_type IN (%s)
                  AND (s.expiration_at IS NULL OR s.expiration_at > ?)
                  AND c.state <> 'FULLY_OVERTURNED'
                ORDER BY s.issued_at
                LIMIT 100
                FOR UPDATE
                """.formatted(placeholders);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(playerId));
            int index = 2;
            for (SanctionType type : INHERITABLE_SANCTION_TYPES) {
                statement.setString(index++, type.name());
            }
            statement.setTimestamp(index, Timestamp.from(now));
            try (ResultSet result = statement.executeQuery()) {
                List<SourceSanction> sanctions = new ArrayList<>();
                while (result.next()) {
                    Timestamp expiration = result.getTimestamp("expiration_at");
                    sanctions.add(new SourceSanction(
                            UuidBytes.fromBytes(result.getBytes("sanction_id")),
                            result.getString("case_id"),
                            SanctionType.valueOf(result.getString("sanction_type")),
                            expiration == null ? Optional.empty() : Optional.of(expiration.toInstant())
                    ));
                }
                return List.copyOf(sanctions);
            }
        }
    }

    private boolean inherit(
            Connection connection,
            UUID joiningPlayerId,
            UUID sourcePlayerId,
            SourceSanction source,
            AltRelationshipState state,
            Instant now
    ) throws SQLException, JsonProcessingException {
        UUID sanctionId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT IGNORE INTO sanctions(sanction_id, case_id, target_id, sanction_type, status,
                    issued_at, activated_at, expiration_at, inherited_from)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(sanctionId));
            statement.setString(2, source.caseId());
            statement.setBytes(3, UuidBytes.toBytes(joiningPlayerId));
            statement.setString(4, source.type().name());
            statement.setTimestamp(5, Timestamp.from(now));
            statement.setTimestamp(6, Timestamp.from(now));
            if (source.expiration().isPresent()) {
                statement.setTimestamp(7, Timestamp.from(source.expiration().orElseThrow()));
            } else {
                statement.setNull(7, Types.TIMESTAMP);
            }
            statement.setBytes(8, UuidBytes.toBytes(source.sanctionId()));
            if (statement.executeUpdate() == 0) {
                return false;
            }
        }
        String baseKey = "inherit:" + joiningPlayerId + ':' + source.sanctionId();
        String payload = json.writeValueAsString(Map.of(
                "caseId", source.caseId(),
                "targetId", joiningPlayerId.toString(),
                "sourcePlayerId", sourcePlayerId.toString(),
                "sourceSanctionId", source.sanctionId().toString(),
                "sanctionType", source.type().name(),
                "relationshipState", state.name()
        ));
        insertInheritedEvent(connection, sanctionId, baseKey, payload, now);
        insertInheritedAudit(connection, joiningPlayerId, source.caseId(), baseKey, payload, now);
        insertOutboxes(connection, baseKey, payload, now);
        insertStaffAlert(connection, "SAME_NETWORK_SANCTION_INHERITANCE", payload, now);
        return true;
    }

    private void insertInheritedEvent(
            Connection connection,
            UUID sanctionId,
            String key,
            String payload,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sanction_events(event_id, sanction_id, event_type, actor_id,
                    occurred_at, reason, event_json, idempotency_key)
                VALUES (?, ?, 'INHERITED', ?, ?, 'Automatic qualifying alt inheritance', ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(sanctionId));
            statement.setBytes(3, UuidBytes.toBytes(SYSTEM_ACTOR));
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setString(5, payload);
            statement.setString(6, key + ":event");
            statement.executeUpdate();
        }
    }

    private void insertInheritedAudit(
            Connection connection,
            UUID targetId,
            String caseId,
            String key,
            String payload,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_events(event_id, correlation_id, actor_id, target_id, case_id,
                    event_type, outcome, event_json, idempotency_key, occurred_at)
                VALUES (?, ?, NULL, ?, ?, 'SANCTION_INHERITED', 'COMMITTED', ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(3, UuidBytes.toBytes(targetId));
            statement.setString(4, caseId);
            statement.setString(5, payload);
            statement.setString(6, key + ":audit");
            statement.setTimestamp(7, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void insertOutboxes(Connection connection, String key, String payload, Instant now)
            throws SQLException {
        try (PreparedStatement network = connection.prepareStatement("""
                INSERT INTO network_outbox(message_id, idempotency_key, destination, message_type,
                    protocol_version, payload_json, available_at, created_at)
                VALUES (?, ?, 'broadcast', 'SANCTION_CHANGED', ?, ?, ?, ?)
                """);
             PreparedStatement discord = connection.prepareStatement("""
                INSERT INTO discord_outbox(message_id, idempotency_key, destination, event_type,
                    payload_json, available_at, created_at)
                VALUES (?, ?, 'punishments', 'SANCTION_INHERITED', ?, ?, ?)
                """)) {
            network.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            network.setString(2, key + ":network");
            network.setInt(3, PROTOCOL_VERSION);
            network.setString(4, payload);
            network.setTimestamp(5, Timestamp.from(now));
            network.setTimestamp(6, Timestamp.from(now));
            network.executeUpdate();

            discord.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            discord.setString(2, key + ":discord");
            discord.setString(3, payload);
            discord.setTimestamp(4, Timestamp.from(now));
            discord.setTimestamp(5, Timestamp.from(now));
            discord.executeUpdate();
        }
    }

    private int insertLowerConfidenceAlert(
            Connection connection,
            UUID joiningPlayerId,
            UUID sourcePlayerId,
            AltRelationshipState state,
            List<SourceSanction> active,
            Instant now
    ) throws SQLException, JsonProcessingException {
        int alerts = 0;
        for (SourceSanction source : active) {
            String payload = json.writeValueAsString(Map.of(
                    "targetId", joiningPlayerId.toString(),
                    "relatedPlayerId", sourcePlayerId.toString(),
                    "caseId", source.caseId(),
                    "sanctionType", source.type().name(),
                    "relationshipState", state.name()
            ));
            insertStaffAlert(connection, "LOW_CONFIDENCE_RELATED_ACCOUNT_ONLINE", payload, now);
            alerts++;
        }
        return alerts;
    }

    private static void insertStaffAlert(Connection connection, String type, String payload, Instant now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO staff_alerts(alert_id, recipient_id, minimum_rank, alert_type, payload_json, created_at)
                VALUES (?, NULL, 'HELPER', ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setString(2, type);
            statement.setString(3, payload);
            statement.setTimestamp(4, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void requirePlayers(Connection connection, PlayerPair pair) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM players WHERE player_id IN (?, ?)")) {
            statement.setBytes(1, UuidBytes.toBytes(pair.lower()));
            statement.setBytes(2, UuidBytes.toBytes(pair.upper()));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != 2) {
                    throw new SQLException("Both player directory records must exist");
                }
            }
        }
    }

    private static void insertRelationship(
            Connection connection,
            PlayerPair pair,
            AltRelationshipState state,
            UUID actorId,
            Instant changedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO alt_relationships(relationship_id, lower_player_id, upper_player_id,
                    relationship_state, confidence, locked_until_reopened, updated_by, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(pair.lower()));
            statement.setBytes(3, UuidBytes.toBytes(pair.upper()));
            statement.setString(4, state.name());
            statement.setDouble(5, confidence(state));
            statement.setBoolean(6, state == AltRelationshipState.NOT_RELATED);
            statement.setBytes(7, UuidBytes.toBytes(actorId));
            statement.setTimestamp(8, Timestamp.from(changedAt));
            statement.executeUpdate();
        }
    }

    private static void updateRelationship(
            Connection connection,
            PlayerPair pair,
            AltRelationshipState state,
            UUID actorId,
            Instant changedAt,
            boolean locked
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE alt_relationships
                SET relationship_state = ?, confidence = ?, locked_until_reopened = ?,
                    updated_by = ?, updated_at = ?, revision = revision + 1
                WHERE lower_player_id = ? AND upper_player_id = ?
                """)) {
            statement.setString(1, state.name());
            statement.setDouble(2, confidence(state));
            statement.setBoolean(3, locked);
            statement.setBytes(4, UuidBytes.toBytes(actorId));
            statement.setTimestamp(5, Timestamp.from(changedAt));
            statement.setBytes(6, UuidBytes.toBytes(pair.lower()));
            statement.setBytes(7, UuidBytes.toBytes(pair.upper()));
            statement.executeUpdate();
        }
    }

    private void insertRelationshipAudit(
            Connection connection,
            PlayerPair pair,
            UUID actorId,
            String action,
            Instant changedAt,
            String reason
    ) throws SQLException, JsonProcessingException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_events(event_id, correlation_id, actor_id, target_id, case_id,
                    event_type, outcome, event_json, occurred_at)
                VALUES (?, ?, ?, ?, NULL, 'ALT_RELATIONSHIP_CHANGED', 'COMMITTED', ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(3, UuidBytes.toBytes(actorId));
            statement.setBytes(4, UuidBytes.toBytes(pair.lower()));
            statement.setString(5, json.writeValueAsString(Map.of(
                    "otherPlayerId", pair.upper().toString(),
                    "action", action,
                    "reason", reason.trim()
            )));
            statement.setTimestamp(6, Timestamp.from(changedAt));
            statement.executeUpdate();
        }
    }

    private static NetworkIdentityRetentionResult purgeExpired(
            Connection connection,
            Instant cutoff,
            int batchSize
    ) throws SQLException {
        int evidenceDeleted;
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM alt_evidence
                WHERE observed_at < ?
                ORDER BY observed_at
                LIMIT ?
                """)) {
            statement.setTimestamp(1, Timestamp.from(cutoff));
            statement.setInt(2, batchSize);
            evidenceDeleted = statement.executeUpdate();
        }
        int tokensDeleted;
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM network_identity_tokens
                WHERE last_seen_at < ?
                ORDER BY last_seen_at
                LIMIT ?
                """)) {
            statement.setTimestamp(1, Timestamp.from(cutoff));
            statement.setInt(2, batchSize);
            tokensDeleted = statement.executeUpdate();
        }
        return new NetworkIdentityRetentionResult(tokensDeleted, evidenceDeleted);
    }

    private synchronized boolean reserveAutomaticRetention(Instant now) {
        if (now.isBefore(nextAutomaticRetentionAt)) {
            return false;
        }
        nextAutomaticRetentionAt = now.plus(AUTOMATIC_RETENTION_INTERVAL);
        return true;
    }

    private synchronized void releaseAutomaticRetentionReservation(Instant observedAt) {
        if (!nextAutomaticRetentionAt.isAfter(observedAt.plus(AUTOMATIC_RETENTION_INTERVAL))) {
            nextAutomaticRetentionAt = observedAt;
        }
    }

    private static double confidence(AltRelationshipState state) {
        return switch (state) {
            case SAME_NETWORK -> 0.25;
            case LOW_CONFIDENCE -> 0.20;
            case SEMI_CONFIDENT -> 0.50;
            case CONFIDENT -> 0.75;
            case VERY_CONFIDENT -> 0.90;
            case CONFIRMED_ALT, APPROVED_ALT -> 1.00;
            case SHARED_HOUSEHOLD -> 0.75;
            case NOT_RELATED -> 0.00;
        };
    }

    private static void validateManualChange(
            UUID first,
            UUID second,
            AltRelationshipState state,
            UUID actor,
            Instant changedAt,
            String reason
    ) {
        if (first == null || second == null || first.equals(second) || state == null || actor == null
                || changedAt == null || reason == null || reason.isBlank() || reason.length() > 512) {
            throw new IllegalArgumentException("valid distinct players, actor, state, time, and reason are required");
        }
        NetworkAddressTextGuard.requireNoRawAddress(reason);
    }

    private static void validateRetentionRequest(Instant cutoff, int batchSize) {
        if (cutoff == null || batchSize < 1 || batchSize > MAX_RETENTION_BATCH_SIZE) {
            throw new IllegalArgumentException("retention cutoff and batch size must be valid");
        }
    }

    private static PlayerPair ordered(UUID first, UUID second) {
        byte[] firstBytes = UuidBytes.toBytes(first);
        byte[] secondBytes = UuidBytes.toBytes(second);
        for (int index = 0; index < firstBytes.length; index++) {
            int comparison = Integer.compare(Byte.toUnsignedInt(firstBytes[index]), Byte.toUnsignedInt(secondBytes[index]));
            if (comparison < 0) {
                return new PlayerPair(first, second);
            }
            if (comparison > 0) {
                return new PlayerPair(second, first);
            }
        }
        throw new IllegalArgumentException("alt relationship players must be distinct");
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
            // Closing returns the connection to the pool; the original failure remains authoritative.
        }
    }

    private record PlayerPair(UUID lower, UUID upper) {
    }

    private record MatchingPlayer(UUID playerId, boolean currentlyOnline) {
    }

    private record Relationship(
            UUID id,
            AltRelationshipState state,
            boolean locked,
            boolean manuallyManaged,
            boolean created
    ) {
    }

    private record SourceSanction(
            UUID sanctionId,
            String caseId,
            SanctionType type,
            Optional<Instant> expiration
    ) {
    }
}
