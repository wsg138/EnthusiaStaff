package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.enthusia.staff.common.security.ProtectedNetworkIdentity;
import net.enthusia.staff.domain.alt.AltRelationshipState;
import net.enthusia.staff.domain.alt.NetworkIdentityObservationResult;
import net.enthusia.staff.domain.alt.NetworkIdentityRetentionResult;
import net.enthusia.staff.persistence.JdbcNetworkIdentityStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class NetworkIdentityStoreIntegrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_identity_store_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    private HikariDataSource dataSource;

    @BeforeAll
    static void migrate() {
        try (MariaDbRuntime ignored = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            // Flyway initialization is the only purpose of this runtime.
        }
    }

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = dataSource();
        clearFixtures();
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void confirmedRelationshipInheritsExactRemainingSanctionOnlyOnce() throws SQLException {
        Instant now = Instant.parse("2026-08-07T14:00:00Z");
        Instant expiration = now.plus(Duration.ofDays(2));
        UUID actor = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        UUID joining = UUID.randomUUID();
        UUID sourceSanction = UUID.randomUUID();
        MariaDbIntegrationSupport.insertPlayer(DATABASE, actor, "Moderator", now.minusSeconds(60));
        MariaDbIntegrationSupport.insertPlayer(DATABASE, source, "Source", now.minusSeconds(60));
        MariaDbIntegrationSupport.insertPlayer(DATABASE, joining, "Joining", now.minusSeconds(60));
        MariaDbIntegrationSupport.insertCase(DATABASE, "ALTCASE000000001", source, actor, now.minusSeconds(30));
        MariaDbIntegrationSupport.insertSanction(
                DATABASE,
                sourceSanction,
                "ALTCASE000000001",
                source,
                "BAN",
                "ACTIVE",
                now.minusSeconds(30),
                expiration
        );
        JdbcNetworkIdentityStore store = store();
        ProtectedNetworkIdentity identity = identity((byte) 11);

        store.observeAndInherit(source, identity, now, false);
        assertTrue(store.setRelationship(
                source,
                joining,
                AltRelationshipState.CONFIRMED_ALT,
                actor,
                now.plusSeconds(1),
                "Confirmed account ownership from independent evidence"
        ));

        NetworkIdentityObservationResult first = store.observeAndInherit(
                joining,
                identity,
                now.plusSeconds(2),
                false
        );
        NetworkIdentityObservationResult duplicate = store.observeAndInherit(
                joining,
                identity,
                now.plusSeconds(3),
                false
        );

        assertEquals(1, first.inheritedSanctions());
        assertEquals(0, duplicate.inheritedSanctions());
        assertEquals(1, inheritedSanctionCount(joining, sourceSanction));
        assertEquals(expiration, inheritedExpiration(joining, sourceSanction));
    }

    @Test
    void simultaneousPlayLowersAutomaticConfidenceAndNeverOverridesManualDecisions() throws SQLException {
        Instant now = Instant.parse("2026-08-07T15:00:00Z");
        UUID actor = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        UUID joining = UUID.randomUUID();
        MariaDbIntegrationSupport.insertPlayer(DATABASE, actor, "Admin", now.minusSeconds(60));
        MariaDbIntegrationSupport.insertPlayer(DATABASE, source, "Source", now.minusSeconds(60));
        MariaDbIntegrationSupport.insertPlayer(DATABASE, joining, "Joining", now.minusSeconds(60));
        setCurrentServer(source, "SMP");
        JdbcNetworkIdentityStore store = store();
        ProtectedNetworkIdentity identity = identity((byte) 12);

        store.observeAndInherit(source, identity, now, false);
        NetworkIdentityObservationResult observation = store.observeAndInherit(
                joining,
                identity,
                now.plusSeconds(1),
                false
        );

        assertFalse(observation.evidenceSuppressed());
        assertEquals(AltRelationshipState.LOW_CONFIDENCE, relationshipState(source, joining));
        assertEquals(1, evidenceCount(source, joining, "SIMULTANEOUS_PLAY"));

        assertTrue(store.setRelationship(
                source,
                joining,
                AltRelationshipState.CONFIRMED_ALT,
                actor,
                now.plusSeconds(2),
                "Staff confirmed ownership after investigation"
        ));
        store.observeAndInherit(joining, identity, now.plus(Duration.ofDays(1)).plusSeconds(3), false);
        assertEquals(AltRelationshipState.CONFIRMED_ALT, relationshipState(source, joining));
    }

    @Test
    void broadSharedNetworkSuppressesAutomaticGraphExpansion() throws SQLException {
        Instant now = Instant.parse("2026-08-07T16:00:00Z");
        ProtectedNetworkIdentity identity = identity((byte) 13);
        for (int index = 0; index < 21; index++) {
            UUID player = UUID.randomUUID();
            MariaDbIntegrationSupport.insertPlayer(DATABASE, player, "Shared" + index, now.minusSeconds(60));
            insertToken(player, identity, now.minusSeconds(30));
        }
        UUID joining = UUID.randomUUID();
        MariaDbIntegrationSupport.insertPlayer(DATABASE, joining, "Joining", now.minusSeconds(10));

        NetworkIdentityObservationResult result = store().observeAndInherit(joining, identity, now, false);

        assertTrue(result.evidenceSuppressed());
        assertEquals(21, result.matchedPlayers());
        assertEquals(0, relationshipCount(joining));
    }

    @Test
    void duplicateProxyObservationsAreIdempotentForRelationshipAndEvidence() throws Exception {
        Instant now = Instant.parse("2026-08-07T17:00:00Z");
        UUID source = UUID.randomUUID();
        UUID joining = UUID.randomUUID();
        MariaDbIntegrationSupport.insertPlayer(DATABASE, source, "Source", now.minusSeconds(60));
        MariaDbIntegrationSupport.insertPlayer(DATABASE, joining, "Joining", now.minusSeconds(60));
        ProtectedNetworkIdentity identity = identity((byte) 14);
        store().observeAndInherit(source, identity, now.minusSeconds(1), false);

        JdbcNetworkIdentityStore firstStore = store();
        JdbcNetworkIdentityStore secondStore = store();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<NetworkIdentityObservationResult> first = executor.submit(() -> {
                start.await();
                return firstStore.observeAndInherit(joining, identity, now, false);
            });
            Future<NetworkIdentityObservationResult> second = executor.submit(() -> {
                start.await();
                return secondStore.observeAndInherit(joining, identity, now, false);
            });
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, relationshipCount(joining));
        assertEquals(1, evidenceCount(source, joining, "SAME_NETWORK"));
    }

    @Test
    void retentionPurgesSensitiveRowsInBatchesButPreservesRelationshipDecisionAcrossRestart() throws SQLException {
        Instant now = Instant.parse("2026-08-07T18:00:00Z");
        Instant old = now.minus(Duration.ofDays(120));
        UUID source = UUID.randomUUID();
        UUID joining = UUID.randomUUID();
        MariaDbIntegrationSupport.insertPlayer(DATABASE, source, "Source", now.minus(Duration.ofDays(200)));
        MariaDbIntegrationSupport.insertPlayer(DATABASE, joining, "Joining", now.minus(Duration.ofDays(200)));
        JdbcNetworkIdentityStore firstStore = store();
        ProtectedNetworkIdentity currentIdentity = identity((byte) 15);
        firstStore.observeAndInherit(source, currentIdentity, now.minusSeconds(2), false);
        firstStore.observeAndInherit(joining, currentIdentity, now.minusSeconds(1), false);
        insertToken(source, identity((byte) 16), old);
        insertOldEvidence(source, joining, old);

        NetworkIdentityRetentionResult firstBatch = firstStore.purgeExpired(now.minus(Duration.ofDays(90)), 1);
        NetworkIdentityRetentionResult secondBatch = firstStore.purgeExpired(now.minus(Duration.ofDays(90)), 10);
        JdbcNetworkIdentityStore restartedStore = store();

        assertEquals(1, firstBatch.identityTokensDeleted());
        assertEquals(1, firstBatch.evidenceRowsDeleted());
        assertEquals(0, secondBatch.totalDeleted());
        assertEquals(1, restartedStore.relationships(joining).size());
        assertEquals(1, tokenCount(source, currentIdentity));
    }

    @Test
    void rawAddressLiteralCannotEnterManualRelationshipAudit() throws SQLException {
        Instant now = Instant.parse("2026-08-07T19:00:00Z");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        MariaDbIntegrationSupport.insertPlayer(DATABASE, first, "First", now);
        MariaDbIntegrationSupport.insertPlayer(DATABASE, second, "Second", now);

        assertThrows(
                IllegalArgumentException.class,
                () -> store().setRelationship(
                        first,
                        second,
                        AltRelationshipState.CONFIRMED_ALT,
                        actor,
                        now,
                        "Matched raw address 203.0.113.77"
                )
        );
        assertEquals(0, auditCount());
    }

    private JdbcNetworkIdentityStore store() {
        return new JdbcNetworkIdentityStore(dataSource, new ObjectMapper());
    }

    private static ProtectedNetworkIdentity identity(byte value) {
        byte[] token = new byte[32];
        byte[] encrypted = new byte[32];
        Arrays.fill(token, value);
        Arrays.fill(encrypted, (byte) (value + 1));
        return new ProtectedNetworkIdentity(1, token, 1, encrypted);
    }

    private void insertToken(UUID playerId, ProtectedNetworkIdentity identity, Instant seenAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO network_identity_tokens(
                         token_id, player_id, hmac_key_version, equality_token,
                         encryption_key_version, encrypted_value, first_seen_at, last_seen_at, session_count
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(UUID.randomUUID()));
            statement.setBytes(2, MariaDbIntegrationSupport.uuidBytes(playerId));
            statement.setInt(3, identity.equalityKeyVersion());
            statement.setBytes(4, identity.equalityToken());
            statement.setInt(5, identity.encryptionKeyVersion());
            statement.setBytes(6, identity.encryptedValue());
            statement.setTimestamp(7, Timestamp.from(seenAt));
            statement.setTimestamp(8, Timestamp.from(seenAt));
            statement.executeUpdate();
        }
    }

    private void insertOldEvidence(UUID first, UUID second, Instant observedAt) throws SQLException {
        UUID relationshipId = relationshipId(first, second);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO alt_evidence(
                         evidence_id, relationship_id, evidence_type, weight, evidence_json, observed_at
                     ) VALUES (?, ?, 'SAME_NETWORK', 0.2500, '{\"source\":\"TEST_OLD\"}', ?)
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(UUID.randomUUID()));
            statement.setBytes(2, MariaDbIntegrationSupport.uuidBytes(relationshipId));
            statement.setTimestamp(3, Timestamp.from(observedAt));
            statement.executeUpdate();
        }
    }

    private void setCurrentServer(UUID playerId, String serverId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE players SET current_server = ? WHERE player_id = ?")) {
            statement.setString(1, serverId);
            statement.setBytes(2, MariaDbIntegrationSupport.uuidBytes(playerId));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private int relationshipCount(UUID playerId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM alt_relationships
                     WHERE lower_player_id = ? OR upper_player_id = ?
                     """)) {
            byte[] id = MariaDbIntegrationSupport.uuidBytes(playerId);
            statement.setBytes(1, id);
            statement.setBytes(2, id);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private AltRelationshipState relationshipState(UUID first, UUID second) throws SQLException {
        UUID relationshipId = relationshipId(first, second);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT relationship_state FROM alt_relationships WHERE relationship_id = ?")) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(relationshipId));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return AltRelationshipState.valueOf(result.getString(1));
            }
        }
    }

    private UUID relationshipId(UUID first, UUID second) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT relationship_id FROM alt_relationships
                     WHERE (lower_player_id = ? AND upper_player_id = ?)
                        OR (lower_player_id = ? AND upper_player_id = ?)
                     """)) {
            byte[] firstBytes = MariaDbIntegrationSupport.uuidBytes(first);
            byte[] secondBytes = MariaDbIntegrationSupport.uuidBytes(second);
            statement.setBytes(1, firstBytes);
            statement.setBytes(2, secondBytes);
            statement.setBytes(3, secondBytes);
            statement.setBytes(4, firstBytes);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return uuid(result.getBytes(1));
            }
        }
    }

    private int evidenceCount(UUID first, UUID second, String evidenceType) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM alt_evidence
                     WHERE relationship_id = ? AND evidence_type = ?
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(relationshipId(first, second)));
            statement.setString(2, evidenceType);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private int inheritedSanctionCount(UUID targetId, UUID inheritedFrom) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM sanctions WHERE target_id = ? AND inherited_from = ?
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(targetId));
            statement.setBytes(2, MariaDbIntegrationSupport.uuidBytes(inheritedFrom));
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private Instant inheritedExpiration(UUID targetId, UUID inheritedFrom) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT expiration_at FROM sanctions WHERE target_id = ? AND inherited_from = ?
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(targetId));
            statement.setBytes(2, MariaDbIntegrationSupport.uuidBytes(inheritedFrom));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getTimestamp(1).toInstant();
            }
        }
    }

    private int tokenCount(UUID playerId, ProtectedNetworkIdentity identity) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM network_identity_tokens
                     WHERE player_id = ? AND hmac_key_version = ? AND equality_token = ?
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(playerId));
            statement.setInt(2, identity.equalityKeyVersion());
            statement.setBytes(3, identity.equalityToken());
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private int auditCount() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM audit_events WHERE event_type = 'ALT_RELATIONSHIP_CHANGED'");
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        }
    }

    private void clearFixtures() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM staff_alerts");
            statement.executeUpdate("DELETE FROM discord_outbox");
            statement.executeUpdate("DELETE FROM network_outbox");
            statement.executeUpdate("DELETE FROM sanction_events");
            statement.executeUpdate("DELETE FROM sanctions");
            statement.executeUpdate("DELETE FROM audit_events");
            statement.executeUpdate("DELETE FROM alt_evidence");
            statement.executeUpdate("DELETE FROM alt_relationships");
            statement.executeUpdate("DELETE FROM network_identity_tokens");
            statement.executeUpdate("DELETE FROM cases");
            statement.executeUpdate("DELETE FROM player_names");
            statement.executeUpdate("DELETE FROM players");
        }
    }

    private static HikariDataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DATABASE.getJdbcUrl().replace("jdbc:mysql:", "jdbc:mariadb:"));
        config.setUsername(DATABASE.getUsername());
        config.setPassword(DATABASE.getPassword());
        config.setMaximumPoolSize(8);
        config.setConnectionTimeout(5_000);
        return new HikariDataSource(config);
    }

    private static UUID uuid(byte[] bytes) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
