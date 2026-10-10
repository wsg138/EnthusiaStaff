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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.security.NetworkAddressTextGuard;
import net.enthusia.staff.common.security.ProtectedNetworkIdentity;
import net.enthusia.staff.domain.alt.AltInheritancePolicy;
import net.enthusia.staff.domain.alt.AltRelationshipState;
import net.enthusia.staff.domain.alt.AltRelationshipSummary;
import net.enthusia.staff.domain.alt.NetworkIdentityObservationResult;
import net.enthusia.staff.domain.alt.NetworkIdentityRetentionResult;
import net.enthusia.staff.domain.ports.NetworkIdentityStore;
import net.enthusia.staff.domain.sanction.SanctionType;

public final class JdbcNetworkIdentityStore implements NetworkIdentityStore {
    private static final int PROTOCOL_VERSION = 1;
    private static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);
    private static final int MAX_AUTOMATED_MATCHES = 20;
    private static final int MATCH_QUERY_LIMIT = MAX_AUTOMATED_MATCHES + 1;
        private static final int MAX_VERIFIED_LINKED_ACCOUNTS = 100;
    private static final int MAX_RETENTION_BATCH_SIZE = 5_000;
    private static final Duration EVIDENCE_REFRESH_INTERVAL = Duration.ofHours(24);
    private static final List<SanctionType> INHERITABLE_SANCTION_TYPES = Arrays.stream(SanctionType.values())
            .filter(SanctionType::inheritsAcrossAltRelationships)
            .toList();

    private final DataSource dataSource;
    private final ObjectMapper json;
    private final AltInheritancePolicy inheritancePolicy = new AltInheritancePolicy();
    private final JdbcTransactionRetry transactionRetry = new JdbcTransactionRetry();

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
        Map<UUID, Boolean> observedOnline = new HashMap<>();
        return transactionRetry.execute(
                "Network identity observation retry interrupted",
                () -> observeOnce(joiningPlayerId, identity, observedAt, suppressAutomatedEvidence, observedOnline)
        );
    }

    private NetworkIdentityObservationResult observeOnce(
            UUID joiningPlayerId,
            ProtectedNetworkIdentity identity,
            Instant observedAt,
            boolean suppressAutomatedEvidence,
            Map<UUID, Boolean> observedOnline
    ) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                NetworkIdentityObservationResult result = observeInTransaction(
                        connection, joiningPlayerId, identity, observedAt, suppressAutomatedEvidence, observedOnline
                );
                connection.commit();
                return result;
            } catch (SQLException | JsonProcessingException exception) {
                rollback(connection, exception);
                throw new ModerationPersistenceException("Network identity observation transaction failed", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to open network identity transaction", exception);
        }
    }

    private NetworkIdentityObservationResult observeInTransaction(
            Connection connection,
            UUID joiningPlayerId,
            ProtectedNetworkIdentity identity,
            Instant observedAt,
            boolean suppressAutomatedEvidence,
            Map<UUID, Boolean> observedOnline
    ) throws SQLException, JsonProcessingException {
        List<MatchingPlayer> candidates = suppressAutomatedEvidence
                ? List.of()
                : matchingPlayers(connection, joiningPlayerId, identity);
        rememberOnlineCandidates(candidates, observedOnline);
        if (candidates.size() > MAX_AUTOMATED_MATCHES) {
            lockObservationPlayers(connection, joiningPlayerId, List.of(), observedOnline);
            upsertIdentity(connection, joiningPlayerId, identity, observedAt);
            return new NetworkIdentityObservationResult(candidates.size(), 0, 0, true);
        }

        LockedObservation locked = lockObservationPlayers(connection, joiningPlayerId, candidates, observedOnline);
        upsertIdentity(connection, joiningPlayerId, identity, observedAt);
        if (suppressAutomatedEvidence) {
            return new NetworkIdentityObservationResult(0, 0, 0, true);
        }
        return processMatches(connection, joiningPlayerId, locked, observedAt);
    }

    private NetworkIdentityObservationResult processMatches(
            Connection connection,
            UUID joiningPlayerId,
            LockedObservation locked,
            Instant observedAt
    ) throws SQLException, JsonProcessingException {
        int inherited = 0;
        int alerts = 0;
        for (MatchingPlayer match : locked.matches()) {
            MatchOutcome outcome = processMatch(
                    connection, joiningPlayerId, match, observedAt
            );
            inherited += outcome.inherited();
            alerts += outcome.alerts();
        }
        return new NetworkIdentityObservationResult(locked.matches().size(), inherited, alerts, false);
    }

    private MatchOutcome processMatch(
            Connection connection,
            UUID joiningPlayerId,
            MatchingPlayer match,
            Instant observedAt
    ) throws SQLException, JsonProcessingException {
        Relationship relationship = lockOrCreateRelationship(
                connection, joiningPlayerId, match.playerId(), observedAt
        );
        relationship = recordPresenceEvidence(connection, relationship, match, observedAt);
        List<SourceSanction> active = activeInheritableSanctions(connection, match.playerId(), observedAt);
        if (active.isEmpty()) {
            return new MatchOutcome(0, 0);
        }
        if (inheritancePolicy.shouldInherit(relationship.state(), false)) {
            int inherited = inheritAll(
                    connection, joiningPlayerId, match.playerId(), relationship.state(), active, observedAt
            );
            return new MatchOutcome(inherited, inherited);
        }
        if (relationship.state().preventsAutomaticInheritance()) {
            return new MatchOutcome(0, 0);
        }
        int alerts = insertLowerConfidenceAlert(
                connection, joiningPlayerId, match.playerId(), relationship.state(), active, observedAt
        );
        return new MatchOutcome(0, alerts);
    }

    private Relationship recordPresenceEvidence(
            Connection connection,
            Relationship relationship,
            MatchingPlayer match,
            Instant observedAt
    ) throws SQLException, JsonProcessingException {
        if (match.currentlyOnline() && !relationship.manuallyManaged()) {
            Relationship lowered = lowerAutomaticConfidence(connection, relationship, observedAt);
            insertEvidenceIfDue(
                    connection, lowered, "SIMULTANEOUS_PLAY", -0.2500,
                    "PLAYER_DIRECTORY_PRESENCE", observedAt
            );
            return lowered;
        }
        insertEvidenceIfDue(
                connection, relationship, "SAME_NETWORK", 0.2500,
                "PROXY_IDENTITY_TOKEN", observedAt
        );
        return relationship;
    }

    private int inheritAll(
            Connection connection,
            UUID joiningPlayerId,
            UUID sourcePlayerId,
            AltRelationshipState state,
            List<SourceSanction> active,
            Instant observedAt
    ) throws SQLException, JsonProcessingException {
        int inherited = 0;
        for (SourceSanction source : active) {
            if (inherit(connection, joiningPlayerId, sourcePlayerId, source, state, observedAt)) {
                inherited++;
            }
        }
        return inherited;
    }

    /**
     * This reconciles current Discord-linked peers AND manually confirmed high-confidence
     * alt decisions without requiring matching network tokens. An ephemeral current link
     * does not automatically create a durable relationship state.
     */
    @Override
    public NetworkIdentityObservationResult observeConnectedAlts(
            UUID joiningPlayerId, Instant observedAt, boolean suppressAutomatedEvidence
    ) {
        if (joiningPlayerId == null || observedAt == null) {
            throw new IllegalArgumentException("connected-alt observation fields must be present");
        }
        if (suppressAutomatedEvidence) {
            return new NetworkIdentityObservationResult(0, 0, 0, true);
        }
        return transactionRetry.execute(
                "Connected-alt inheritance retry interrupted",
                () -> observeConnectedAltsOnce(joiningPlayerId, observedAt)
        );
    }

    private NetworkIdentityObservationResult observeConnectedAltsOnce(UUID joiningPlayerId, Instant observedAt) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<UUID, Boolean> connected = new LinkedHashMap<>();
                for (MatchingPlayer account : verifiedLinkPeers(connection, joiningPlayerId)) {
                    connected.put(account.playerId(), true);
                }
                for (MatchingPlayer account : manuallyConfirmedPeers(connection, joiningPlayerId)) {
                    connected.putIfAbsent(account.playerId(), false);
                }
                List<MatchingPlayer> candidates = new ArrayList<>();
                for (UUID account : connected.keySet()) {
                    candidates.add(new MatchingPlayer(account, false));
                }
                // Fail closed against an anomalous or overly broad linked-account set.
                if (candidates.size() > MAX_VERIFIED_LINKED_ACCOUNTS) {
                    connection.rollback();
                    return new NetworkIdentityObservationResult(candidates.size(), 0, 0, true);
                }
                lockObservationPlayers(connection, joiningPlayerId, candidates, Map.of());
                int inherited = 0;
                for (MatchingPlayer peer : candidates) {
                    Relationship explicitRelationship = lockRelationship(
                            connection, ordered(joiningPlayerId, peer.playerId())
                    );
                    AltRelationshipState state = explicitRelationship == null
                            ? null : explicitRelationship.state();
                    boolean currentVerifiedDiscordLink = connected.getOrDefault(peer.playerId(), false);
                    if (!inheritancePolicy.shouldInherit(state, currentVerifiedDiscordLink)) {
                        continue;
                    }
                    List<SourceSanction> active = activeInheritableSanctions(
                            connection, peer.playerId(), observedAt
                    );
                    inherited += inheritAll(
                            connection, joiningPlayerId, peer.playerId(),
                            currentVerifiedDiscordLink ? AltRelationshipState.CONFIRMED_ALT : state,
                            active, observedAt
                    );
                }
                connection.commit();
                return new NetworkIdentityObservationResult(candidates.size(), inherited, inherited, false);
            } catch (SQLException | JsonProcessingException exception) {
                rollback(connection, exception);
                throw new ModerationPersistenceException("Connected-alt inheritance transaction failed", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to open connected-alt transaction", exception);
        }
    }

    /**
     * Explicit staff-confirmed alt relationships qualify without current Discord
     * linkage or a matching network token. A pair-level exception is still checked
     * before inheritance, and no transitive graph assumption is made.
     */
    private static List<MatchingPlayer> manuallyConfirmedPeers(Connection connection, UUID joiningPlayerId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT CASE WHEN r.lower_player_id = ? THEN r.upper_player_id ELSE r.lower_player_id END
                       AS related_player_id,
                       CASE WHEN p.current_server IS NULL THEN FALSE ELSE TRUE END AS currently_online
                FROM alt_relationships r
                JOIN players p ON p.player_id =
                  CASE WHEN r.lower_player_id = ? THEN r.upper_player_id ELSE r.lower_player_id END
                WHERE (r.lower_player_id = ? OR r.upper_player_id = ?)
                  AND r.relationship_state IN ('VERY_CONFIDENT', 'CONFIRMED_ALT')
                ORDER BY related_player_id
                LIMIT ?
                FOR UPDATE
                """)) {
            byte[] joining = UuidBytes.toBytes(joiningPlayerId);
            statement.setBytes(1, joining);
            statement.setBytes(2, joining);
            statement.setBytes(3, joining);
            statement.setBytes(4, joining);
            statement.setInt(5, MAX_VERIFIED_LINKED_ACCOUNTS + 1);
            try (ResultSet result = statement.executeQuery()) {
                List<MatchingPlayer> peers = new ArrayList<>();
                while (result.next()) {
                    peers.add(new MatchingPlayer(
                            UuidBytes.fromBytes(result.getBytes("related_player_id")),
                            result.getBoolean("currently_online")
                    ));
                }
                return List.copyOf(peers);
            }
        }
    }

    private static List<MatchingPlayer> verifiedLinkPeers(Connection connection, UUID joiningPlayerId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT related.minecraft_player_id,
                       CASE WHEN p.current_server IS NULL THEN FALSE ELSE TRUE END AS currently_online
                FROM discord_minecraft_links anchor
                JOIN discord_minecraft_links related
                  ON related.discord_user_id = anchor.discord_user_id
                JOIN players p ON p.player_id = related.minecraft_player_id
                WHERE anchor.minecraft_player_id = ?
                  AND anchor.unlinked_at IS NULL
                  AND related.unlinked_at IS NULL
                  AND related.minecraft_player_id <> ?
                ORDER BY related.minecraft_player_id
                LIMIT ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(joiningPlayerId));
            statement.setBytes(2, UuidBytes.toBytes(joiningPlayerId));
            statement.setInt(3, MAX_VERIFIED_LINKED_ACCOUNTS + 1);
            try (ResultSet result = statement.executeQuery()) {
                List<MatchingPlayer> peers = new ArrayList<>();
                while (result.next()) {
                    peers.add(new MatchingPlayer(
                            UuidBytes.fromBytes(result.getBytes("minecraft_player_id")),
                            result.getBoolean("currently_online")
                    ));
                }
                return List.copyOf(peers);
            }
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
                    updateRelationship(
                            connection,
                            pair,
                            state,
                            actorId,
                            changedAt,
                            state == AltRelationshipState.NOT_RELATED
                    );
                }
                boolean protectedPair = state.preventsAutomaticInheritance();
                // A currently verified shared Discord identity is an independent inheritance
                // source; low-confidence network downgrades alone must not undo that link.
                boolean stillVerified = !protectedPair && verifiedLinkPeers(connection, pair.lower())
                        .stream().anyMatch(peer -> peer.playerId().equals(pair.upper()));
                if (protectedPair || (!state.inheritsAutomatically() && !stillVerified)) {
                    revokeExemptedInheritedSanctions(connection, pair, actorId, changedAt, state);
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

    /** Relationship exemptions reverse existing inherited sanctions between this exact pair only. */
    private void revokeExemptedInheritedSanctions(
            Connection connection, PlayerPair pair, UUID actorId, Instant now, AltRelationshipState state
    ) throws SQLException, JsonProcessingException {
        record Derived(UUID sanctionId, UUID targetId, String caseId) { }
        List<Derived> inherited = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT child.sanction_id, child.target_id, child.case_id
                  FROM sanctions child
                  JOIN sanctions source ON child.inherited_from = source.sanction_id
                 WHERE child.status = 'ACTIVE'
                   AND ((child.target_id = ? AND source.target_id = ?)
                     OR (child.target_id = ? AND source.target_id = ?))
                 FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(pair.lower()));
            statement.setBytes(2, UuidBytes.toBytes(pair.upper()));
            statement.setBytes(3, UuidBytes.toBytes(pair.upper()));
            statement.setBytes(4, UuidBytes.toBytes(pair.lower()));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    inherited.add(new Derived(
                            UuidBytes.fromBytes(result.getBytes("sanction_id")),
                            UuidBytes.fromBytes(result.getBytes("target_id")),
                            result.getString("case_id")));
                }
            }
        }
        for (Derived derived : inherited) {
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE sanctions SET status = 'REVOKED', ended_at = ?, revision = revision + 1
                    WHERE sanction_id = ? AND status = 'ACTIVE'
                    """)) {
                update.setTimestamp(1, Timestamp.from(now));
                update.setBytes(2, UuidBytes.toBytes(derived.sanctionId()));
                if (update.executeUpdate() != 1) {
                    continue;
                }
            }
            String key = "alt-exception:" + UUID.nameUUIDFromBytes(
                    (derived.sanctionId() + ":" + state.name()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String payload = json.writeValueAsString(Map.of(
                    "caseId", derived.caseId(),
                    "targetId", derived.targetId().toString(),
                    "sanctionId", derived.sanctionId().toString(),
                    "action", "ALT_RELATIONSHIP_EXEMPTION",
                    "relationshipState", state.name()
            ));
            try (PreparedStatement network = connection.prepareStatement("""
                    INSERT INTO network_outbox(message_id, idempotency_key, destination, message_type,
                        protocol_version, payload_json, available_at, created_at)
                    VALUES (?, ?, 'broadcast', 'SANCTION_CHANGED', ?, ?, ?, ?)
                    """);
                 PreparedStatement discord = connection.prepareStatement("""
                    INSERT INTO discord_outbox(message_id, idempotency_key, destination, event_type,
                        payload_json, available_at, created_at)
                    VALUES (?, ?, 'punishments', 'ALT_SANCTION_EXEMPTION', ?, ?, ?)
                    """);
                 PreparedStatement event = connection.prepareStatement("""
                    INSERT INTO sanction_events(event_id, sanction_id, event_type, actor_id,
                        occurred_at, reason, event_json, idempotency_key, case_id, subject_id)
                    VALUES (?, ?, 'ALT_RELATIONSHIP_EXEMPTION', ?, ?, ?, ?, ?, ?, ?)
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

                event.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
                event.setBytes(2, UuidBytes.toBytes(derived.sanctionId()));
                event.setBytes(3, UuidBytes.toBytes(actorId));
                event.setTimestamp(4, Timestamp.from(now));
                event.setString(5, "Alt relationship exempted: " + state.name());
                event.setString(6, payload);
                event.setString(7, key + ":event");
                event.setString(8, derived.caseId());
                event.setBytes(9, UuidBytes.toBytes(derived.targetId()));
                event.executeUpdate();
            }
        }
    }

    @Override
    public boolean reopen(UUID firstPlayerId, UUID secondPlayerId, UUID actorId, Instant changedAt, String reason) {
        validateManualChange(
                firstPlayerId,
                secondPlayerId,
                AltRelationshipState.LOW_CONFIDENCE,
                actorId,
                changedAt,
                reason
        );
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

    private static LockedObservation lockObservationPlayers(
            Connection connection,
            UUID joiningPlayerId,
            List<MatchingPlayer> candidates,
            Map<UUID, Boolean> observedOnline
    ) throws SQLException {
        List<UUID> playerIds = new ArrayList<>(candidates.size() + 1);
        playerIds.add(joiningPlayerId);
        candidates.forEach(candidate -> playerIds.add(candidate.playerId()));
        playerIds.sort((first, second) -> Arrays.compareUnsigned(
                UuidBytes.toBytes(first), UuidBytes.toBytes(second)
        ));

        Map<UUID, Boolean> locked = new HashMap<>();
        for (UUID playerId : playerIds) {
            locked.put(playerId, lockPlayer(connection, playerId));
        }
        List<MatchingPlayer> matches = candidates.stream()
                .map(candidate -> new MatchingPlayer(
                        candidate.playerId(),
                        candidate.currentlyOnline()
                                || locked.get(candidate.playerId())
                                || observedOnline.getOrDefault(candidate.playerId(), false)
                ))
                .toList();
        return new LockedObservation(matches);
    }

    private static void rememberOnlineCandidates(
            List<MatchingPlayer> candidates,
            Map<UUID, Boolean> observedOnline
    ) {
        for (MatchingPlayer candidate : candidates) {
            if (candidate.currentlyOnline()) {
                observedOnline.put(candidate.playerId(), true);
            }
        }
    }

    private static boolean lockPlayer(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT current_server FROM players WHERE player_id = ? FOR UPDATE")) {
            statement.setBytes(1, UuidBytes.toBytes(playerId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Player directory record is missing");
                }
                return result.getString("current_server") != null;
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
                SELECT s.sanction_id, s.case_id, s.sanction_type, s.expiration_at, c.public_reason
                FROM sanctions s JOIN cases c ON c.case_id = s.case_id
                WHERE s.target_id = ? AND s.status = 'ACTIVE'
                  AND s.sanction_type IN (%s)
                  AND (s.expiration_at IS NULL OR s.expiration_at > ?)
                  AND c.state <> 'FULLY_OVERTURNED'
                  AND s.inherited_from IS NULL
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
                            expiration == null ? Optional.empty() : Optional.of(expiration.toInstant()),
                            result.getString("public_reason")
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
        String networkPayload = json.writeValueAsString(Map.of(
                "caseId", source.caseId(),
                "targetId", joiningPlayerId.toString(),
                "sanctionTypes", List.of(source.type().name()),
                "publicReason", source.publicReason(),
                "issuedAt", now.toString()
        ));
        insertInheritedEvent(connection, sanctionId, baseKey, payload, now);
        insertInheritedAudit(connection, joiningPlayerId, source.caseId(), baseKey, payload, now);
        insertOutboxes(connection, baseKey, payload, networkPayload, now);
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

    private static void insertOutboxes(
            Connection connection, String key, String payload, String networkPayload, Instant now)
            throws SQLException {
        try (PreparedStatement network = connection.prepareStatement("""
                INSERT INTO network_outbox(message_id, idempotency_key, destination, message_type,
                    protocol_version, payload_json, available_at, created_at)
                VALUES (?, ?, 'broadcast', 'PUNISHMENT_CREATED', ?, ?, ?, ?)
                """);
             PreparedStatement discord = connection.prepareStatement("""
                INSERT INTO discord_outbox(message_id, idempotency_key, destination, event_type,
                    payload_json, available_at, created_at)
                VALUES (?, ?, 'punishments', 'SANCTION_INHERITED', ?, ?, ?)
                """);
             PreparedStatement review = connection.prepareStatement("""
                INSERT INTO discord_outbox(message_id, idempotency_key, destination, event_type,
                    payload_json, available_at, created_at)
                VALUES (?, ?, 'alerts', 'ALT_SANCTION_INHERITED', ?, ?, ?)
                """)) {
            network.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            network.setString(2, key + ":network");
            network.setInt(3, PROTOCOL_VERSION);
            network.setString(4, networkPayload);
            network.setTimestamp(5, Timestamp.from(now));
            network.setTimestamp(6, Timestamp.from(now));
            network.executeUpdate();

            discord.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            discord.setString(2, key + ":discord");
            discord.setString(3, payload);
            discord.setTimestamp(4, Timestamp.from(now));
            discord.setTimestamp(5, Timestamp.from(now));
            discord.executeUpdate();

            review.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            review.setString(2, key + ":alt-review");
            review.setString(3, payload);
            review.setTimestamp(4, Timestamp.from(now));
            review.setTimestamp(5, Timestamp.from(now));
            review.executeUpdate();
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
                    "relationshipState", state.name(),
                    "trigger", "JOIN",
                    "confidencePolicyGrade", state.confidence()
            ));
            insertStaffAlert(connection, "LOW_CONFIDENCE_RELATED_ACCOUNT_ONLINE", payload, now);
            insertDiscordAltReviewAlert(connection, joiningPlayerId, sourcePlayerId, source, payload, now);
            alerts++;
        }
        return alerts;
    }

    /** One Discord alert per joining player, source sanction and hour; duplicate logins do not spam staff. */
    private static void insertDiscordAltReviewAlert(
            Connection connection, UUID joiningPlayerId, UUID relatedPlayerId,
            SourceSanction source, String payload, Instant now
    ) throws SQLException {
        String key = "alt-review:" + joiningPlayerId + ":" + relatedPlayerId
                + ":" + source.sanctionId() + ":" + now.getEpochSecond() / 3600;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT IGNORE INTO discord_outbox(message_id, idempotency_key, destination, event_type,
                    payload_json, available_at, created_at)
                VALUES (?, ?, 'alerts', 'ALT_EVASION_REVIEW', ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setString(2, key);
            statement.setString(3, payload);
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setTimestamp(5, Timestamp.from(now));
            statement.executeUpdate();
        }
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

    private static double confidence(AltRelationshipState state) {
        return state.confidence();
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

    private record LockedObservation(List<MatchingPlayer> matches) {
    }

    private record MatchOutcome(int inherited, int alerts) {
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
            Optional<Instant> expiration,
            String publicReason
    ) {
    }
}
