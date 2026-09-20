package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
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
import javax.sql.DataSource;
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
    private static final String SOURCE_NAME = "Source";
    private static final String JOINING_NAME = "Joining";

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_identity_store_test")
            .withUsername("enthusia_test")
            .withPassword("enthusia_test_password");

    private HikariDataSource dataSource;

    @BeforeAll
    static void migrate() {
        try (MariaDbRuntime runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            runtime.operationalStateStore().current();
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
        MariaDbIntegrationSupport.insertPlayer(DATABASE, source, SOURCE_NAME, now.minusSeconds(60));
        MariaDbIntegrationSupport.insertPlayer(DATABASE, joining, JOINING_NAME, now.minusSeconds(60));
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
        ProtectedNetworkIdentity identity = identity(1, (byte) 11);

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
        MariaDbIntegrationSupport.insertPlayer(DATABASE, source, SOURCE_NAME, now.minusSeconds(60));
        MariaDbIntegrationSupport.insertPlayer(DATABASE, joining, JOINING_NAME, now.minusSeconds(60));
        setCurrentServer(source, "SMP");
        JdbcNetworkIdentityStore store = store();
        ProtectedNetworkIdentity identity = identity(1, (byte) 12);

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
    void stableOfflineSingleNewAccountStillInheritsExactlyOnce() throws SQLException {
        Instant now = Instant.parse("2026-08-07T15:15:00Z");
        AltInheritanceFixture fixture = automaticInheritanceFixture(now, (byte) 21, "ALTCASE000000021");

        NetworkIdentityObservationResult first = store().observeAndInherit(
                fixture.joining(), fixture.identity(), now, false
        );
        NetworkIdentityObservationResult retry = store().observeAndInherit(
                fixture.joining(), fixture.identity(), now.plusSeconds(1), false
        );

        assertEquals(1, first.inheritedSanctions());
        assertEquals(0, retry.inheritedSanctions());
        assertEquals(1, inheritedSanctionCount(fixture.joining(), fixture.sourceSanction()));
    }

    @Test
    void concurrentSourceLoginAfterMatchDiscoveryPreventsAutomaticInheritance() throws Exception {
        Instant now = Instant.parse("2026-08-07T15:30:00Z");
        AltInheritanceFixture fixture = automaticInheritanceFixture(now, (byte) 22, "ALTCASE000000022");
        CountDownLatch matched = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        JdbcNetworkIdentityStore paused = pausedMatchStore(matched, resume);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<NetworkIdentityObservationResult> observation = executor.submit(() -> paused.observeAndInherit(
                    fixture.joining(), fixture.identity(), now, false
            ));
            assertTrue(matched.await(10, TimeUnit.SECONDS));
            setCurrentServer(fixture.source(), "SMP");
            resume.countDown();

            NetworkIdentityObservationResult result = observation.get(20, TimeUnit.SECONDS);
            assertEquals(0, result.inheritedSanctions());
            assertEquals(AltRelationshipState.LOW_CONFIDENCE,
                    relationshipState(fixture.source(), fixture.joining()));
            assertEquals(1, evidenceCount(fixture.source(), fixture.joining(), "SIMULTANEOUS_PLAY"));
            assertEquals(0, inheritedSanctionCount(fixture.joining(), fixture.sourceSanction()));
        } finally {
            resume.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentSourceLogoutDoesNotUpgradeTransientSimultaneousPlayToAutomaticInheritance() throws Exception {
        Instant now = Instant.parse("2026-08-07T15:45:00Z");
        AltInheritanceFixture fixture = automaticInheritanceFixture(now, (byte) 23, "ALTCASE000000023");
        setCurrentServer(fixture.source(), "SMP");
        CountDownLatch matched = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        JdbcNetworkIdentityStore paused = pausedMatchStore(matched, resume);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<NetworkIdentityObservationResult> observation = executor.submit(() -> paused.observeAndInherit(
                    fixture.joining(), fixture.identity(), now, false
            ));
            assertTrue(matched.await(10, TimeUnit.SECONDS));
            clearCurrentServer(fixture.source());
            resume.countDown();

            NetworkIdentityObservationResult result = observation.get(20, TimeUnit.SECONDS);
            assertEquals(0, result.inheritedSanctions());
            assertEquals(AltRelationshipState.LOW_CONFIDENCE,
                    relationshipState(fixture.source(), fixture.joining()));
            assertEquals(1, evidenceCount(fixture.source(), fixture.joining(), "SIMULTANEOUS_PLAY"));
        } finally {
            resume.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void oppositeDirectionObservationsUseOneDeterministicPlayerLockOrder() throws Exception {
        Instant now = Instant.parse("2026-08-07T15:50:00Z");
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000020");
        ProtectedNetworkIdentity identity = identity(1, (byte) 24);
        MariaDbIntegrationSupport.insertPlayer(DATABASE, first, "FirstLock", now.minusSeconds(60));
        MariaDbIntegrationSupport.insertPlayer(DATABASE, second, "SecondLock", now.minusSeconds(60));
        insertToken(first, identity, now.minusSeconds(10));
        insertToken(second, identity, now.minusSeconds(10));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstObservation = executor.submit(() -> observeAfter(start, first, identity, now));
            Future<?> secondObservation = executor.submit(() -> observeAfter(start, second, identity, now));
            start.countDown();
            firstObservation.get(20, TimeUnit.SECONDS);
            secondObservation.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, relationshipCount(first));
        assertEquals(1, evidenceCount(first, second, "SAME_NETWORK"));
    }

    @Test
    void broadSharedNetworkSuppressesAutomaticGraphExpansion() throws SQLException {
        Instant now = Instant.parse("2026-08-07T16:00:00Z");
        ProtectedNetworkIdentity identity = identity(1, (byte) 13);
        for (int index = 0; index < 21; index++) {
            UUID player = UUID.randomUUID();
            MariaDbIntegrationSupport.insertPlayer(DATABASE, player, "Shared" + index, now.minusSeconds(60));
            insertToken(player, identity, now.minusSeconds(30));
        }
        UUID joining = UUID.randomUUID();
        MariaDbIntegrationSupport.insertPlayer(DATABASE, joining, JOINING_NAME, now.minusSeconds(10));

        NetworkIdentityObservationResult result = store().observeAndInherit(joining, identity, now, false);

        assertTrue(result.evidenceSuppressed());
        assertEquals(21, result.matchedPlayers());
        assertEquals(0, relationshipCount(joining));
    }

    @Test
    void equalityKeyVersionsDoNotCrossMatchEvenWithIdenticalTokenBytes() throws SQLException {
        Instant now = Instant.parse("2026-08-07T16:30:00Z");
        UUID source = UUID.randomUUID();
        UUID joining = UUID.randomUUID();
        MariaDbIntegrationSupport.insertPlayer(DATABASE, source, SOURCE_NAME, now.minusSeconds(30));
        MariaDbIntegrationSupport.insertPlayer(DATABASE, joining, JOINING_NAME, now.minusSeconds(10));
        ProtectedNetworkIdentity oldVersion = identity(1, (byte) 18);
        ProtectedNetworkIdentity newVersion = identity(2, (byte) 18);
        insertToken(source, oldVersion, now.minusSeconds(20));

        NetworkIdentityObservationResult result = store().observeAndInherit(joining, newVersion, now, false);

        assertEquals(0, result.matchedPlayers());
        assertEquals(0, relationshipCount(joining));
    }

    @Test
    void duplicateProxyObservationsAreIdempotentForRelationshipAndEvidence() throws Exception {
        Instant now = Instant.parse("2026-08-07T17:00:00Z");
        UUID source = UUID.randomUUID();
        UUID joining = UUID.randomUUID();
        MariaDbIntegrationSupport.insertPlayer(DATABASE, source, SOURCE_NAME, now.minusSeconds(60));
        MariaDbIntegrationSupport.insertPlayer(DATABASE, joining, JOINING_NAME, now.minusSeconds(60));
        ProtectedNetworkIdentity identity = identity(1, (byte) 14);
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
        MariaDbIntegrationSupport.insertPlayer(DATABASE, source, SOURCE_NAME, now.minus(Duration.ofDays(200)));
        MariaDbIntegrationSupport.insertPlayer(DATABASE, joining, JOINING_NAME, now.minus(Duration.ofDays(200)));
        JdbcNetworkIdentityStore firstStore = store();
        ProtectedNetworkIdentity currentIdentity = identity(1, (byte) 15);
        firstStore.observeAndInherit(source, currentIdentity, now.minusSeconds(2), false);
        firstStore.observeAndInherit(joining, currentIdentity, now.minusSeconds(1), false);
        insertToken(source, identity(1, (byte) 16), old);
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

    private JdbcNetworkIdentityStore pausedMatchStore(CountDownLatch matched, CountDownLatch resume) {
        return new JdbcNetworkIdentityStore(pausingMatchDataSource(matched, resume), new ObjectMapper());
    }

    private AltInheritanceFixture automaticInheritanceFixture(Instant now, byte token, String caseId)
            throws SQLException {
        UUID actor = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        UUID joining = UUID.randomUUID();
        UUID sourceSanction = UUID.randomUUID();
        MariaDbIntegrationSupport.insertPlayer(DATABASE, actor, "AutoAdmin", now.minusSeconds(90));
        MariaDbIntegrationSupport.insertPlayer(DATABASE, source, SOURCE_NAME, now.minusSeconds(60));
        MariaDbIntegrationSupport.insertPlayer(DATABASE, joining, JOINING_NAME, now.minusSeconds(10));
        MariaDbIntegrationSupport.insertCase(DATABASE, caseId, source, actor, now.minusSeconds(45));
        MariaDbIntegrationSupport.insertSanction(
                DATABASE, sourceSanction, caseId, source, "BAN", "ACTIVE",
                now.minusSeconds(40), now.plus(Duration.ofDays(2))
        );
        ProtectedNetworkIdentity identity = identity(1, token);
        insertToken(source, identity, now.minusSeconds(30));
        insertCutover(actor, now.minusSeconds(20));
        return new AltInheritanceFixture(source, joining, sourceSanction, identity);
    }

    private NetworkIdentityObservationResult observeAfter(
            CountDownLatch start,
            UUID playerId,
            ProtectedNetworkIdentity identity,
            Instant observedAt
    ) throws Exception {
        start.await();
        return store().observeAndInherit(playerId, identity, observedAt, false);
    }

    private DataSource pausingMatchDataSource(CountDownLatch matched, CountDownLatch resume) {
        return (DataSource) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, args) -> {
                    Object value = invoke(method, dataSource, args);
                    if ("getConnection".equals(method.getName())) {
                        return pausingConnection((Connection) value, matched, resume);
                    }
                    return value;
                }
        );
    }

    private Connection pausingConnection(Connection connection, CountDownLatch matched, CountDownLatch resume) {
        return (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    Object value = invoke(method, connection, args);
                    if (isMatchQuery(method, args)) {
                        return pausingStatement((PreparedStatement) value, matched, resume);
                    }
                    return value;
                }
        );
    }

    private static boolean isMatchQuery(Method method, Object[] args) {
        return "prepareStatement".equals(method.getName())
                && args != null
                && args.length > 0
                && args[0] instanceof String sql
                && sql.contains("FROM network_identity_tokens n")
                && sql.contains("JOIN players p");
    }

    private PreparedStatement pausingStatement(
            PreparedStatement statement,
            CountDownLatch matched,
            CountDownLatch resume
    ) {
        return (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    Object value = invoke(method, statement, args);
                    if ("executeQuery".equals(method.getName()) && (args == null || args.length == 0)) {
                        matched.countDown();
                        awaitResume(resume);
                    }
                    return value;
                }
        );
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static void awaitResume(CountDownLatch resume) throws SQLException {
        try {
            if (!resume.await(10, TimeUnit.SECONDS)) {
                throw new SQLException("Timed out waiting to resume network identity match query");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting to resume network identity match query", exception);
        }
    }

    private static ProtectedNetworkIdentity identity(int keyVersion, byte value) {
        byte[] token = new byte[32];
        byte[] encrypted = new byte[32];
        Arrays.fill(token, value);
        Arrays.fill(encrypted, (byte) (value + 1));
        return new ProtectedNetworkIdentity(keyVersion, token, keyVersion, encrypted);
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

    private void insertCutover(UUID actorId, Instant authorizedAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO cutover_records(
                         cutover_id, assessment_json, blockers_json, founder_override_used,
                         authorized_by, authorized_at
                     ) VALUES (?, '{}', '[]', FALSE, ?, ?)
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(UUID.randomUUID()));
            statement.setBytes(2, MariaDbIntegrationSupport.uuidBytes(actorId));
            statement.setTimestamp(3, Timestamp.from(authorizedAt));
            statement.executeUpdate();
        }
    }

    private void insertOldEvidence(UUID first, UUID second, Instant observedAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO alt_evidence(
                         evidence_id, relationship_id, evidence_type, weight, evidence_json, observed_at
                     ) VALUES (?, ?, 'SAME_NETWORK', 0.2500, '{\"source\":\"TEST_OLD\"}', ?)
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(UUID.randomUUID()));
            statement.setBytes(2, MariaDbIntegrationSupport.uuidBytes(relationshipId(first, second)));
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

    private void clearCurrentServer(UUID playerId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE players SET current_server = NULL WHERE player_id = ?")) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(playerId));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private int relationshipCount(UUID playerId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM alt_relationships
                     WHERE lower_player_id = ? OR upper_player_id = ?
                     """)) {
            byte[] player = MariaDbIntegrationSupport.uuidBytes(playerId);
            statement.setBytes(1, player);
            statement.setBytes(2, player);
            return readCount(statement);
        }
    }

    private AltRelationshipState relationshipState(UUID first, UUID second) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT relationship_state FROM alt_relationships WHERE relationship_id = ?")) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(relationshipId(first, second)));
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
                ByteBuffer buffer = ByteBuffer.wrap(result.getBytes(1));
                return new UUID(buffer.getLong(), buffer.getLong());
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
            return readCount(statement);
        }
    }

    private int inheritedSanctionCount(UUID targetId, UUID inheritedFrom) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM sanctions
                     WHERE target_id = ? AND inherited_from = ?
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(targetId));
            statement.setBytes(2, MariaDbIntegrationSupport.uuidBytes(inheritedFrom));
            return readCount(statement);
        }
    }

    private Instant inheritedExpiration(UUID targetId, UUID inheritedFrom) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT expiration_at FROM sanctions WHERE target_id = ? AND inherited_from = ?")) {
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
            return readCount(statement);
        }
    }

    private int auditCount() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM audit_events WHERE event_type = 'ALT_RELATIONSHIP_CHANGED'")) {
            return readCount(statement);
        }
    }

    private static int readCount(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private void clearFixtures() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM staff_alerts");
            statement.executeUpdate("DELETE FROM cutover_records");
            statement.executeUpdate("DELETE FROM discord_outbox");
            statement.executeUpdate("DELETE FROM network_outbox");
            statement.executeUpdate("DELETE FROM sanction_events");
            statement.executeUpdate("UPDATE sanctions SET inherited_from = NULL WHERE inherited_from IS NOT NULL");
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

    private record AltInheritanceFixture(
            UUID source,
            UUID joining,
            UUID sourceSanction,
            ProtectedNetworkIdentity identity
    ) {
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
}
