package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import net.enthusia.staff.common.security.ProtectedNetworkIdentity;
import net.enthusia.staff.domain.alt.NetworkIdentityObservationResult;
import net.enthusia.staff.persistence.JdbcNetworkIdentityStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class NetworkIdentityStoreFailureIntegrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("enthusia_staff_identity_failure_test")
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
    void transientFailureRollsBackWholeObservationAndRetryInheritsExactlyOnce() throws SQLException {
        Instant now = Instant.parse("2026-08-07T20:00:00Z");
        AltInheritanceFixture fixture = automaticInheritanceFixture(now, (byte) 31, "ALTCASE000000031");

        assertThrows(
                ModerationPersistenceException.class,
                () -> failingInheritedEventStore().observeAndInherit(
                        fixture.joining(), fixture.identity(), now, false
                )
        );
        assertEquals(0, relationshipCount(fixture.joining()));
        assertEquals(0, inheritedSanctionCount(fixture.joining(), fixture.sourceSanction()));
        assertEquals(0, tokenCount(fixture.joining(), fixture.identity()));
        assertEquals(0, inheritedEventCount());

        NetworkIdentityObservationResult retry = store().observeAndInherit(
                fixture.joining(), fixture.identity(), now.plusSeconds(1), false
        );
        NetworkIdentityObservationResult duplicate = store().observeAndInherit(
                fixture.joining(), fixture.identity(), now.plusSeconds(2), false
        );

        assertEquals(1, retry.inheritedSanctions());
        assertEquals(0, duplicate.inheritedSanctions());
        assertCommittedInheritanceExactlyOnce(fixture);
    }

    @Test
    void concurrentEligibleObservationsSerializeWithoutDuplicateSanctions() throws Exception {
        Instant now = Instant.parse("2026-08-07T20:15:00Z");
        AltInheritanceFixture fixture = automaticInheritanceFixture(now, (byte) 32, "ALTCASE000000032");
        JdbcNetworkIdentityStore firstStore = store();
        JdbcNetworkIdentityStore secondStore = store();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<NetworkIdentityObservationResult> first = executor.submit(
                    () -> observeAfter(start, firstStore, fixture, now)
            );
            Future<NetworkIdentityObservationResult> second = executor.submit(
                    () -> observeAfter(start, secondStore, fixture, now)
            );
            start.countDown();
            NetworkIdentityObservationResult firstResult = first.get(20, TimeUnit.SECONDS);
            NetworkIdentityObservationResult secondResult = second.get(20, TimeUnit.SECONDS);

            assertEquals(1, firstResult.inheritedSanctions() + secondResult.inheritedSanctions());
        } finally {
            executor.shutdownNow();
        }
        assertCommittedInheritanceExactlyOnce(fixture);
        assertEquals(1, evidenceCount(fixture.source(), fixture.joining(), "SAME_NETWORK"));
    }

    private NetworkIdentityObservationResult observeAfter(
            CountDownLatch start,
            JdbcNetworkIdentityStore observationStore,
            AltInheritanceFixture fixture,
            Instant observedAt
    ) throws Exception {
        assertTrue(start.await(10, TimeUnit.SECONDS));
        return observationStore.observeAndInherit(fixture.joining(), fixture.identity(), observedAt, false);
    }

    private void assertCommittedInheritanceExactlyOnce(AltInheritanceFixture fixture) throws SQLException {
        assertEquals(1, relationshipCount(fixture.joining()));
        assertEquals(1, inheritedSanctionCount(fixture.joining(), fixture.sourceSanction()));
        assertEquals(1, tokenCount(fixture.joining(), fixture.identity()));
        assertEquals(1, inheritedEventCount());
        assertEquals(1, networkOutboxCount());
        assertEquals(1, discordOutboxCount());
    }

    private JdbcNetworkIdentityStore store() {
        return new JdbcNetworkIdentityStore(dataSource, new ObjectMapper());
    }

    private JdbcNetworkIdentityStore failingInheritedEventStore() {
        return new JdbcNetworkIdentityStore(failingInheritedEventDataSource(), new ObjectMapper());
    }

    private DataSource failingInheritedEventDataSource() {
        AtomicBoolean failed = new AtomicBoolean();
        return (DataSource) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, args) -> {
                    Object value = invoke(method, dataSource, args);
                    if ("getConnection".equals(method.getName())) {
                        return failingInheritedEventConnection((Connection) value, failed);
                    }
                    return value;
                }
        );
    }

    private Connection failingInheritedEventConnection(Connection connection, AtomicBoolean failed) {
        return (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    Object value = invoke(method, connection, args);
                    if (isInheritedEventInsert(method, args)) {
                        return failingInheritedEventStatement((PreparedStatement) value, failed);
                    }
                    return value;
                }
        );
    }

    private static boolean isInheritedEventInsert(Method method, Object[] args) {
        return "prepareStatement".equals(method.getName())
                && args != null
                && args.length > 0
                && args[0] instanceof String sql
                && sql.contains("INSERT INTO sanction_events")
                && sql.contains("'INHERITED'");
    }

    private PreparedStatement failingInheritedEventStatement(PreparedStatement statement, AtomicBoolean failed) {
        return (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if ("executeUpdate".equals(method.getName())
                            && (args == null || args.length == 0)
                            && failed.compareAndSet(false, true)) {
                        throw new SQLException("Synthetic transient inherited-event failure");
                    }
                    return invoke(method, statement, args);
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

    private AltInheritanceFixture automaticInheritanceFixture(Instant now, byte token, String caseId)
            throws SQLException {
        UUID actor = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        UUID joining = UUID.randomUUID();
        UUID sourceSanction = UUID.randomUUID();
        MariaDbIntegrationSupport.insertPlayer(DATABASE, actor, "FailureAdmin", now.minusSeconds(90));
        MariaDbIntegrationSupport.insertPlayer(DATABASE, source, "FailureSource", now.minusSeconds(60));
        MariaDbIntegrationSupport.insertPlayer(DATABASE, joining, "FailureJoining", now.minusSeconds(10));
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

    private int evidenceCount(UUID first, UUID second, String evidenceType) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM alt_evidence e
                     JOIN alt_relationships r ON r.relationship_id = e.relationship_id
                     WHERE ((r.lower_player_id = ? AND r.upper_player_id = ?)
                         OR (r.lower_player_id = ? AND r.upper_player_id = ?))
                       AND e.evidence_type = ?
                     """)) {
            byte[] firstBytes = MariaDbIntegrationSupport.uuidBytes(first);
            byte[] secondBytes = MariaDbIntegrationSupport.uuidBytes(second);
            statement.setBytes(1, firstBytes);
            statement.setBytes(2, secondBytes);
            statement.setBytes(3, secondBytes);
            statement.setBytes(4, firstBytes);
            statement.setString(5, evidenceType);
            return readCount(statement);
        }
    }

    private int inheritedEventCount() throws SQLException {
        return count("SELECT COUNT(*) FROM sanction_events WHERE event_type = 'INHERITED'");
    }

    private int networkOutboxCount() throws SQLException {
        return count("SELECT COUNT(*) FROM network_outbox WHERE message_type = 'SANCTION_CHANGED'");
    }

    private int discordOutboxCount() throws SQLException {
        return count("SELECT COUNT(*) FROM discord_outbox WHERE event_type = 'SANCTION_INHERITED'");
    }

    private int count(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
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
