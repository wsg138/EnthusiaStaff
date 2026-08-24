package net.enthusia.staff.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.moderation.DiscordIdentityRef;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLink;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLinkSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.MainAccountSelectionSource;
import net.enthusia.staff.domain.moderation.MainMinecraftAccount;
import net.enthusia.staff.domain.moderation.MinecraftIdentityRef;
import net.enthusia.staff.domain.moderation.ModerationIdentity;
import net.enthusia.staff.domain.moderation.ModerationSubject;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedLink;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedSubject;

/** Read/ensure owner for moderation subjects. Link mutations live in JdbcDiscordLinkRepository. */
final class JdbcDiscordIdentityRepository {
    private final DataSource dataSource;

    JdbcDiscordIdentityRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    VersionedSubject ensureMinecraftSubject(UUID playerId, Instant now) {
        requirePresent(playerId, "playerId");
        requirePresent(now, "now");
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to ensure Minecraft moderation subject",
                connection -> ensureMinecraftSubject(connection, playerId, now)
        );
    }

    VersionedSubject ensureDiscordSubject(DiscordUserId userId, Instant now) {
        requirePresent(userId, "discordUserId");
        requirePresent(now, "now");
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to ensure Discord moderation subject",
                connection -> {
                    ModerationSubjectId existing = subjectIdForDiscord(connection, userId, true);
                    if (existing != null) {
                        return loadSubject(connection, existing);
                    }
                    ModerationSubjectId subjectId = insertFreshSubject(connection, now);
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO moderation_subject_discord_identities(
                                discord_user_id, subject_id, linked_at
                            ) VALUES (?, ?, ?)
                            """)) {
                        statement.setBigDecimal(1, discordId(userId));
                        statement.setBytes(2, UuidBytes.toBytes(subjectId.value()));
                        statement.setTimestamp(3, Timestamp.from(now));
                        JdbcTransactionSupport.requireSingleUpdate(
                                statement.executeUpdate(),
                                "Discord identity mapping was not inserted"
                        );
                    }
                    return loadSubject(connection, subjectId);
                }
        );
    }

    Optional<VersionedSubject> subjectForMinecraft(UUID playerId) {
        requirePresent(playerId, "playerId");
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to read Minecraft moderation subject",
                connection -> {
                    ModerationSubjectId subjectId = subjectIdForMinecraft(connection, playerId, false);
                    return subjectId == null ? Optional.empty() : Optional.of(loadSubject(connection, subjectId));
                }
        );
    }

    Optional<VersionedSubject> subjectForDiscord(DiscordUserId userId) {
        requirePresent(userId, "discordUserId");
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to read Discord moderation subject",
                connection -> {
                    ModerationSubjectId subjectId = subjectIdForDiscord(connection, userId, false);
                    return subjectId == null ? Optional.empty() : Optional.of(loadSubject(connection, subjectId));
                }
        );
    }

    Optional<VersionedLink> currentLink(UUID playerId) {
        requirePresent(playerId, "playerId");
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to read current Discord link",
                connection -> Optional.ofNullable(currentLink(connection, playerId, false))
        );
    }

    private static VersionedSubject ensureMinecraftSubject(Connection connection, UUID playerId, Instant now)
            throws SQLException {
        ModerationSubjectId existing = subjectIdForMinecraft(connection, playerId, true);
        if (existing != null) {
            return loadSubject(connection, existing);
        }
        if (!playerExists(connection, playerId)) {
            throw new SQLException("Minecraft player must exist before a moderation subject is created");
        }
        ModerationSubjectId subjectId = subjectIdAvailable(connection, playerId)
                ? new ModerationSubjectId(playerId)
                : insertFreshSubject(connection, now);
        if (subjectId.value().equals(playerId)) {
            insertSubject(connection, subjectId, now);
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO moderation_subject_minecraft_identities(player_id, subject_id, linked_at)
                VALUES (?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(playerId));
            statement.setBytes(2, UuidBytes.toBytes(subjectId.value()));
            statement.setTimestamp(3, Timestamp.from(now));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Minecraft identity mapping was not inserted"
            );
        }
        ensureMainAccount(connection, subjectId, playerId, now);
        return loadSubject(connection, subjectId);
    }

    private static VersionedSubject loadSubject(Connection connection, ModerationSubjectId subjectId)
            throws SQLException {
        long revision;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT revision FROM moderation_subjects WHERE subject_id = ?")) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("moderation subject does not exist");
                }
                revision = result.getLong("revision");
            }
        }

        Set<ModerationIdentity> identities = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT player_id FROM moderation_subject_minecraft_identities WHERE subject_id = ?")) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    identities.add(new MinecraftIdentityRef(UuidBytes.fromBytes(result.getBytes("player_id"))));
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT discord_user_id FROM moderation_subject_discord_identities WHERE subject_id = ?")) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    identities.add(new DiscordIdentityRef(discordUserId(result.getBigDecimal("discord_user_id"))));
                }
            }
        }

        Optional<MainMinecraftAccount> mainAccount = Optional.empty();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT player_id, selection_source
                FROM moderation_subject_main_accounts
                WHERE subject_id = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    mainAccount = Optional.of(new MainMinecraftAccount(
                            UuidBytes.fromBytes(result.getBytes("player_id")),
                            MainAccountSelectionSource.valueOf(result.getString("selection_source"))
                    ));
                }
            }
        }
        return new VersionedSubject(new ModerationSubject(subjectId, Set.copyOf(identities), mainAccount), revision);
    }

    private static ModerationSubjectId subjectIdForMinecraft(Connection connection, UUID playerId, boolean lock)
            throws SQLException {
        String sql = "SELECT subject_id FROM moderation_subject_minecraft_identities WHERE player_id = ?"
                + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(playerId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? new ModerationSubjectId(UuidBytes.fromBytes(result.getBytes("subject_id")))
                        : null;
            }
        }
    }

    private static ModerationSubjectId subjectIdForDiscord(
            Connection connection,
            DiscordUserId userId,
            boolean lock
    ) throws SQLException {
        String sql = "SELECT subject_id FROM moderation_subject_discord_identities WHERE discord_user_id = ?"
                + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, discordId(userId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? new ModerationSubjectId(UuidBytes.fromBytes(result.getBytes("subject_id")))
                        : null;
            }
        }
    }

    private static VersionedLink currentLink(Connection connection, UUID playerId, boolean lock)
            throws SQLException {
        String sql = """
                SELECT link_id, subject_id, discord_user_id, minecraft_player_id,
                       linked_at, unlinked_at, source, revision
                FROM discord_minecraft_links
                WHERE minecraft_player_id = ? AND unlinked_at IS NULL
                """ + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(playerId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readLink(result, false) : null;
            }
        }
    }

    private static VersionedLink readLink(ResultSet result, boolean replayed) throws SQLException {
        Timestamp unlinked = result.getTimestamp("unlinked_at");
        return new VersionedLink(
                UuidBytes.fromBytes(result.getBytes("link_id")),
                new ModerationSubjectId(UuidBytes.fromBytes(result.getBytes("subject_id"))),
                new DiscordMinecraftLink(
                        discordUserId(result.getBigDecimal("discord_user_id")),
                        UuidBytes.fromBytes(result.getBytes("minecraft_player_id")),
                        result.getTimestamp("linked_at").toInstant(),
                        unlinked == null ? Optional.empty() : Optional.of(unlinked.toInstant()),
                        DiscordMinecraftLinkSource.valueOf(result.getString("source"))
                ),
                result.getLong("revision"),
                replayed
        );
    }

    private static void ensureMainAccount(
            Connection connection,
            ModerationSubjectId subjectId,
            UUID playerId,
            Instant selectedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT IGNORE INTO moderation_subject_main_accounts(
                    subject_id, player_id, selection_source, selected_at, revision
                ) VALUES (?, ?, 'AUTOMATIC', ?, 0)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            statement.setBytes(2, UuidBytes.toBytes(playerId));
            statement.setTimestamp(3, Timestamp.from(selectedAt));
            JdbcTransactionSupport.requireOptionalSingleUpdate(
                    statement.executeUpdate(),
                    "unexpected main-account insert count"
            );
        }
    }

    private static boolean playerExists(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM players WHERE player_id = ?")) {
            statement.setBytes(1, UuidBytes.toBytes(playerId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static boolean subjectExists(Connection connection, ModerationSubjectId subjectId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM moderation_subjects WHERE subject_id = ?")) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static boolean subjectIdAvailable(Connection connection, UUID candidate) throws SQLException {
        return !subjectExists(connection, new ModerationSubjectId(candidate));
    }

    private static ModerationSubjectId insertFreshSubject(Connection connection, Instant now) throws SQLException {
        for (int attempt = 0; attempt < 4; attempt++) {
            ModerationSubjectId subjectId = new ModerationSubjectId(UUID.randomUUID());
            try {
                insertSubject(connection, subjectId, now);
                return subjectId;
            } catch (SQLException exception) {
                if (!JdbcSqlErrors.isDuplicateKey(exception)) {
                    throw exception;
                }
            }
        }
        throw new SQLException("unable to allocate unique moderation subject id");
    }

    private static void insertSubject(Connection connection, ModerationSubjectId subjectId, Instant now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO moderation_subjects(subject_id, revision, created_at, updated_at)
                VALUES (?, 0, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setTimestamp(3, Timestamp.from(now));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "moderation subject was not inserted"
            );
        }
    }

    private static BigDecimal discordId(DiscordUserId userId) {
        return new BigDecimal(userId.value());
    }

    private static DiscordUserId discordUserId(BigDecimal value) {
        return new DiscordUserId(value.toBigIntegerExact().toString());
    }

    private static void requirePresent(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must be present");
        }
    }
}
