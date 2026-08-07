package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.player.PlayerPresence;
import net.enthusia.staff.domain.player.PlayerResolution;
import net.enthusia.staff.domain.ports.PlayerDirectory;

public final class JdbcPlayerDirectory implements PlayerDirectory {
    private static final Pattern JAVA_OR_UNPREFIXED_NAME = Pattern.compile("[A-Za-z0-9_]{1,32}");
    private static final Pattern BEDROCK_ALIAS = Pattern.compile("\\*[A-Za-z0-9_]{1,31}");
    private static final Pattern JAVA_OR_UNPREFIXED_PREFIX = Pattern.compile("[A-Za-z0-9_]{0,32}");
    private static final Pattern BEDROCK_ALIAS_PREFIX = Pattern.compile("\\*[A-Za-z0-9_]{0,31}");
    private static final String OBSERVATION_WINS = """
            VALUES(last_seen_at) > last_seen_at OR (
                VALUES(last_seen_at) = last_seen_at
                AND CAST(CONCAT(
                    VALUES(lowercase_username), CHAR(0),
                    HEX(VALUES(current_username)), CHAR(0),
                    VALUES(current_server)
                ) AS BINARY) > CAST(CONCAT(
                    COALESCE(lowercase_username, ''), CHAR(0),
                    COALESCE(HEX(current_username), ''), CHAR(0),
                    COALESCE(current_server, '')
                ) AS BINARY)
            )
            """;
    private static final String UPSERT_PLAYER = """
            INSERT INTO players(player_id, current_username, lowercase_username, platform,
                current_server, first_seen_at, last_seen_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                current_username = IF(%1$s,
                    VALUES(current_username), current_username),
                lowercase_username = IF(%1$s,
                    VALUES(lowercase_username), lowercase_username),
                platform = CASE
                    WHEN VALUES(platform) = 'BEDROCK' THEN 'BEDROCK'
                    WHEN platform = 'UNKNOWN' AND VALUES(platform) = 'JAVA' THEN 'JAVA'
                    ELSE platform
                END,
                last_server = IF(%1$s,
                    current_server, last_server),
                current_server = IF(%1$s,
                    VALUES(current_server), current_server),
                first_seen_at = LEAST(first_seen_at, VALUES(first_seen_at)),
                last_seen_at = GREATEST(last_seen_at, VALUES(last_seen_at)),
                revision = revision + 1
            """.formatted(OBSERVATION_WINS);
    private static final String UPSERT_PLAYER_NAME = """
            INSERT INTO player_names(player_id, username, lowercase_username, first_seen_at, last_seen_at)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                username = IF(
                    VALUES(last_seen_at) > last_seen_at OR (
                        VALUES(last_seen_at) = last_seen_at
                        AND CAST(VALUES(username) AS BINARY) > CAST(username AS BINARY)
                    ),
                    VALUES(username), username
                ),
                first_seen_at = LEAST(first_seen_at, VALUES(first_seen_at)),
                last_seen_at = GREATEST(last_seen_at, VALUES(last_seen_at))
            """;

    private final DataSource dataSource;

    public JdbcPlayerDirectory(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must be present");
        }
        this.dataSource = dataSource;
    }

    @Override
    public Optional<PlayerIdentity> find(String uuidOrUsername) {
        PlayerResolution resolution = resolve(uuidOrUsername);
        if (resolution instanceof PlayerResolution.Resolved resolved) {
            return Optional.of(resolved.identity());
        }
        return Optional.empty();
    }

    @Override
    public PlayerResolution resolve(String uuidOrUsername) {
        if (uuidOrUsername == null || uuidOrUsername.isBlank() || uuidOrUsername.length() > 36) {
            return new PlayerResolution.Missing();
        }
        String input = uuidOrUsername.trim();
        UUID parsed = parseUuid(input);
        return parsed == null ? resolveUsername(input) : resolveUuid(parsed);
    }

    private PlayerResolution resolveUuid(UUID playerId) {
        String sql = """
                SELECT player_id, current_username, platform, first_seen_at, last_seen_at
                FROM players
                WHERE player_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(playerId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return new PlayerResolution.Missing();
                }
                return new PlayerResolution.Resolved(
                        read(result),
                        PlayerResolution.MatchKind.UUID
                );
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new ModerationPersistenceException("Unable to resolve player directory UUID", exception);
        }
    }

    private PlayerResolution resolveUsername(String username) {
        String lower = username.toLowerCase(Locale.ROOT);
        String sql = """
                SELECT p.player_id, p.current_username, p.platform,
                    p.first_seen_at, p.last_seen_at
                FROM players p
                WHERE p.lowercase_username = ? OR EXISTS (
                    SELECT 1 FROM player_names n
                    WHERE n.player_id = p.player_id AND n.lowercase_username = ?
                )
                ORDER BY (p.lowercase_username = ?) DESC, p.last_seen_at DESC, p.player_id ASC
                LIMIT 26
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, lower);
            statement.setString(2, lower);
            statement.setString(3, lower);
            try (ResultSet result = statement.executeQuery()) {
                Map<UUID, PlayerIdentity> matches = new LinkedHashMap<>();
                while (result.next()) {
                    PlayerIdentity identity = read(result);
                    matches.putIfAbsent(identity.playerId(), identity);
                }
                if (matches.isEmpty()) {
                    return new PlayerResolution.Missing();
                }
                List<PlayerIdentity> current = matches.values().stream()
                        .filter(identity -> identity.currentUsername()
                                .filter(value -> value.equalsIgnoreCase(username))
                                .isPresent())
                        .toList();
                if (current.size() == 1) {
                    return new PlayerResolution.Resolved(
                            current.getFirst(),
                            PlayerResolution.MatchKind.CURRENT_USERNAME
                    );
                }
                if (current.size() > 1) {
                    return new PlayerResolution.Ambiguous(current);
                }
                boolean truncated = matches.size() > 25;
                List<PlayerIdentity> historical = matches.values().stream().limit(25).toList();
                if (historical.size() == 1) {
                    return new PlayerResolution.Resolved(
                            historical.getFirst(),
                            PlayerResolution.MatchKind.HISTORICAL_USERNAME
                    );
                }
                return new PlayerResolution.Ambiguous(historical, truncated);
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new ModerationPersistenceException("Unable to resolve player directory username", exception);
        }
    }

    @Override
    public List<PlayerIdentity> search(String prefix, int limit) {
        if (!validPrefix(prefix) || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("prefix or limit is invalid");
        }
        String sql = """
                SELECT DISTINCT p.player_id, p.current_username, p.platform, p.first_seen_at, p.last_seen_at
                FROM players p
                LEFT JOIN player_names n ON n.player_id = p.player_id
                WHERE p.lowercase_username LIKE ? ESCAPE '!'
                    OR n.lowercase_username LIKE ? ESCAPE '!'
                ORDER BY p.last_seen_at DESC
                LIMIT ?
                """;
        String pattern = escapeLike(prefix.toLowerCase(Locale.ROOT)) + '%';
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, pattern);
            statement.setString(2, pattern);
            statement.setInt(3, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<PlayerIdentity> matches = new ArrayList<>();
                while (result.next()) {
                    matches.add(read(result));
                }
                return List.copyOf(matches);
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new ModerationPersistenceException("Unable to search the player directory", exception);
        }
    }

    @Override
    public Optional<PlayerPresence> presence(UUID playerId) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId must be present");
        }
        String sql = """
                SELECT current_server, last_server, last_seen_at
                FROM players
                WHERE player_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(playerId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PlayerPresence(
                        playerId,
                        Optional.ofNullable(result.getString("current_server")),
                        Optional.ofNullable(result.getString("last_server")),
                        result.getTimestamp("last_seen_at").toInstant()
                ));
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read player presence", exception);
        }
    }

    @Override
    public void recordSeen(
            UUID playerId,
            String username,
            PlayerPlatform platform,
            String serverId,
            Instant seenAt
    ) {
        if (platform == null) {
            throw new IllegalArgumentException("platform compatibility hint must be present");
        }
        recordObservation(playerId, username, PlayerPlatform.UNKNOWN, serverId, seenAt);
    }

    @Override
    public void recordSeenVerified(
            UUID playerId,
            String username,
            PlayerPlatform platform,
            String serverId,
            Instant seenAt
    ) {
        recordObservation(playerId, username, platform, serverId, seenAt);
    }

    private void recordObservation(
            UUID playerId,
            String username,
            PlayerPlatform platform,
            String serverId,
            Instant seenAt
    ) {
        validateObservation(playerId, username, platform, serverId, seenAt);
        PlayerObservation observation = new PlayerObservation(
                playerId,
                username,
                username.toLowerCase(Locale.ROOT),
                platform,
                serverId,
                seenAt
        );
        try (Connection connection = dataSource.getConnection()) {
            persistObservation(connection, observation);
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to record player observation", exception);
        }
    }

    private static void validateObservation(
            UUID playerId,
            String username,
            PlayerPlatform platform,
            String serverId,
            Instant seenAt
    ) {
        boolean invalidServer = serverId == null || serverId.isBlank() || serverId.length() > 64;
        if (playerId == null || !validUsername(username) || platform == null
                || invalidServer || seenAt == null) {
            throw new IllegalArgumentException("valid player observation fields must be present");
        }
    }

    private static void persistObservation(
            Connection connection,
            PlayerObservation observation
    ) throws SQLException {
        connection.setAutoCommit(false);
        try {
            upsertPlayer(connection, observation);
            upsertPlayerName(connection, observation);
            connection.commit();
        } catch (SQLException exception) {
            rollback(connection, exception);
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static void upsertPlayer(
            Connection connection,
            PlayerObservation observation
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_PLAYER)) {
            byte[] id = UuidBytes.toBytes(observation.playerId());
            Timestamp seenAt = Timestamp.from(observation.seenAt());
            statement.setBytes(1, id);
            statement.setString(2, observation.username());
            statement.setString(3, observation.lowercaseUsername());
            statement.setString(4, observation.platform().name());
            statement.setString(5, observation.serverId());
            statement.setTimestamp(6, seenAt);
            statement.setTimestamp(7, seenAt);
            statement.executeUpdate();
        }
    }

    private static void upsertPlayerName(
            Connection connection,
            PlayerObservation observation
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_PLAYER_NAME)) {
            byte[] id = UuidBytes.toBytes(observation.playerId());
            Timestamp seenAt = Timestamp.from(observation.seenAt());
            statement.setBytes(1, id);
            statement.setString(2, observation.username());
            statement.setString(3, observation.lowercaseUsername());
            statement.setTimestamp(4, seenAt);
            statement.setTimestamp(5, seenAt);
            statement.executeUpdate();
        }
    }

    private static void rollback(Connection connection, SQLException failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    @Override
    public void recordDisconnected(UUID playerId, String serverId, Instant disconnectedAt) {
        if (playerId == null || serverId == null || serverId.isBlank() || serverId.length() > 64
                || disconnectedAt == null) {
            throw new IllegalArgumentException("valid disconnect fields must be present");
        }
        String sql = """
                UPDATE players
                SET last_server = current_server, current_server = NULL,
                    last_seen_at = ?, revision = revision + 1
                WHERE player_id = ? AND current_server = ? AND last_seen_at < ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp disconnected = Timestamp.from(disconnectedAt);
            statement.setTimestamp(1, disconnected);
            statement.setBytes(2, UuidBytes.toBytes(playerId));
            statement.setString(3, serverId);
            statement.setTimestamp(4, disconnected);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to record player disconnect", exception);
        }
    }

    private static PlayerIdentity read(ResultSet result) throws SQLException {
        return new PlayerIdentity(
                UuidBytes.fromBytes(result.getBytes("player_id")),
                Optional.ofNullable(result.getString("current_username")),
                PlayerPlatform.valueOf(result.getString("platform")),
                result.getTimestamp("first_seen_at").toInstant(),
                result.getTimestamp("last_seen_at").toInstant()
        );
    }

    private static boolean validUsername(String username) {
        return username != null && (JAVA_OR_UNPREFIXED_NAME.matcher(username).matches()
                || BEDROCK_ALIAS.matcher(username).matches());
    }

    private static boolean validPrefix(String prefix) {
        return prefix != null && (JAVA_OR_UNPREFIXED_PREFIX.matcher(prefix).matches()
                || BEDROCK_ALIAS_PREFIX.matcher(prefix).matches());
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private record PlayerObservation(
            UUID playerId,
            String username,
            String lowercaseUsername,
            PlayerPlatform platform,
            String serverId,
            Instant seenAt
    ) {
    }
}
