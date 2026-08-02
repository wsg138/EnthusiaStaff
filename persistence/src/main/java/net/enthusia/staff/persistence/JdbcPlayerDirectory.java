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
import javax.sql.DataSource;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.player.PlayerPresence;
import net.enthusia.staff.domain.player.PlayerResolution;
import net.enthusia.staff.domain.ports.PlayerDirectory;

public final class JdbcPlayerDirectory implements PlayerDirectory {
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
                LIMIT 4
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
                List<PlayerIdentity> historical = List.copyOf(matches.values());
                if (historical.size() == 1) {
                    return new PlayerResolution.Resolved(
                            historical.getFirst(),
                            PlayerResolution.MatchKind.HISTORICAL_USERNAME
                    );
                }
                return new PlayerResolution.Ambiguous(historical);
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new ModerationPersistenceException("Unable to resolve player directory username", exception);
        }
    }

    @Override
    public List<PlayerIdentity> search(String prefix, int limit) {
        if (prefix == null || !prefix.matches("[A-Za-z0-9_]{0,32}") || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("prefix or limit is invalid");
        }
        String sql = """
                SELECT DISTINCT p.player_id, p.current_username, p.platform, p.first_seen_at, p.last_seen_at
                FROM players p
                LEFT JOIN player_names n ON n.player_id = p.player_id
                WHERE p.lowercase_username LIKE ? OR n.lowercase_username LIKE ?
                ORDER BY p.last_seen_at DESC
                LIMIT ?
                """;
        String pattern = prefix.toLowerCase(Locale.ROOT) + '%';
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
        if (playerId == null || username == null || !username.matches("[A-Za-z0-9_]{1,32}")
                || platform == null || serverId == null || serverId.isBlank() || serverId.length() > 64
                || seenAt == null) {
            throw new IllegalArgumentException("valid player observation fields must be present");
        }
        String lower = username.toLowerCase(Locale.ROOT);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement player = connection.prepareStatement("""
                    INSERT INTO players(player_id, current_username, lowercase_username, platform,
                        current_server, first_seen_at, last_seen_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE current_username = VALUES(current_username),
                        lowercase_username = VALUES(lowercase_username), platform = VALUES(platform),
                        last_server = current_server, current_server = VALUES(current_server),
                        last_seen_at = VALUES(last_seen_at), revision = revision + 1
                    """);
                 PreparedStatement name = connection.prepareStatement("""
                    INSERT INTO player_names(player_id, username, lowercase_username, first_seen_at, last_seen_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE username = VALUES(username), last_seen_at = VALUES(last_seen_at)
                    """)) {
                byte[] id = UuidBytes.toBytes(playerId);
                player.setBytes(1, id);
                player.setString(2, username);
                player.setString(3, lower);
                player.setString(4, platform.name());
                player.setString(5, serverId);
                player.setTimestamp(6, Timestamp.from(seenAt));
                player.setTimestamp(7, Timestamp.from(seenAt));
                player.executeUpdate();

                name.setBytes(1, id);
                name.setString(2, username);
                name.setString(3, lower);
                name.setTimestamp(4, Timestamp.from(seenAt));
                name.setTimestamp(5, Timestamp.from(seenAt));
                name.executeUpdate();
                connection.commit();
            } catch (SQLException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    exception.addSuppressed(rollback);
                }
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to record player observation", exception);
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
                WHERE player_id = ? AND current_server = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(disconnectedAt));
            statement.setBytes(2, UuidBytes.toBytes(playerId));
            statement.setString(3, serverId);
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

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
