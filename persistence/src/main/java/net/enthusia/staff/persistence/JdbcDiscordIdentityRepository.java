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
        try (Connection connection = dataSource.getConnection()) {
            return Optional.ofNullable(currentLink(connection, playerId, false));
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read current Discord link", exception);
        }
    }

    VersionedLink link(
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            DiscordMinecraftLinkSource source,
            String operationKey,
            Instant linkedAt
    ) {
        requirePresent(discordUserId, "discordUserId");
        requirePresent(minecraftPlayerId, "minecraftPlayerId");
        requirePresent(source, "source");
        requireKey(operationKey, "operationKey");
        requirePresent(linkedAt, "linkedAt");
        return JdbcTransactionSupport.execute(dataSource, "Unable to link Discord and Minecraft identities", connection -> {
            VersionedLink operationReplay = linkByOperation(connection, operationKey, false);
            if (operationReplay != null) {
                return replay(operationReplay);
            }

            VersionedSubject minecraftSubject = ensureMinecraftSubject(connection, minecraftPlayerId, linkedAt);
            ModerationSubjectId discordSubjectId = subjectIdForDiscord(connection, discordUserId, true);
            VersionedLink active = currentLink(connection, minecraftPlayerId, true);
            if (active != null) {
                if (active.link().discordUserId().equals(discordUserId)) {
                    return replay(active);
                }
                throw new SQLException("Minecraft identity already has a different current Discord owner");
            }

            ModerationSubjectId minecraftSubjectId = minecraftSubject.subject().subjectId();
            ModerationSubjectId canonicalSubjectId;
            ModerationSubjectId movedFrom = null;
            if (discordSubjectId == null) {
                canonicalSubjectId = minecraftSubjectId;
                insertDiscordIdentity(connection, canonicalSubjectId, discordUserId, linkedAt);
            } else {
                canonicalSubjectId = discordSubjectId;
                if (!canonicalSubjectId.equals(minecraftSubjectId)) {
                    movedFrom = minecraftSubjectId;
                    deleteMainIfPlayer(connection, minecraftSubjectId, minecraftPlayerId);
                    moveMinecraftIdentity(connection, minecraftPlayerId, canonicalSubjectId);
                    cleanupSubjectIfEmpty(connection, minecraftSubjectId);
                }
            }

            ensureMainAccount(connection, canonicalSubjectId, minecraftPlayerId, linkedAt);
            UUID linkId = UUID.randomUUID();
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO discord_minecraft_links(
                        link_id, operation_key, subject_id, discord_user_id, minecraft_player_id,
                        linked_at, source, revision
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(linkId));
                statement.setString(2, operationKey);
                statement.setBytes(3, UuidBytes.toBytes(canonicalSubjectId.value()));
                statement.setBigDecimal(4, discordId(discordUserId));
                statement.setBytes(5, UuidBytes.toBytes(minecraftPlayerId));
                statement.setTimestamp(6, Timestamp.from(linkedAt));
                statement.setString(7, source.name());
                JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "Discord link was not inserted");
            }
            bumpSubject(connection, canonicalSubjectId, linkedAt);
            if (movedFrom != null && subjectExists(connection, movedFrom)) {
                bumpSubject(connection, movedFrom, linkedAt);
            }
            return new VersionedLink(
                    linkId,
                    canonicalSubjectId,
                    new DiscordMinecraftLink(
                            discordUserId,
                            minecraftPlayerId,
                            linkedAt,
                            Optional.empty(),
                            source
                    ),
                    0,
                    false
            );
        });
    }

    VersionedLink unlink(
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            long expectedRevision,
            String operationKey,
            Instant unlinkedAt
    ) {
        requirePresent(discordUserId, "discordUserId");
        requirePresent(minecraftPlayerId, "minecraftPlayerId");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision must not be negative");
        }
        requireKey(operationKey, "operationKey");
        requirePresent(unlinkedAt, "unlinkedAt");
        return JdbcTransactionSupport.execute(dataSource, "Unable to unlink Discord and Minecraft identities", connection -> {
            VersionedLink operationReplay = linkByUnlinkOperation(connection, operationKey, false);
            if (operationReplay != null) {
                return replay(operationReplay);
            }
            VersionedLink current = currentLink(connection, minecraftPlayerId, true);
            if (current == null || !current.link().discordUserId().equals(discordUserId)) {
                throw new SQLException("No matching current Discord/Minecraft link exists");
            }
            if (current.revision() != expectedRevision) {
                throw new SQLException("Discord link revision changed before unlink");
            }
            if (unlinkedAt.isBefore(current.link().linkedAt())) {
                throw new SQLException("unlink time precedes link time");
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE discord_minecraft_links
                    SET unlinked_at = ?, unlink_operation_key = ?, revision = revision + 1
                    WHERE link_id = ? AND revision = ? AND unlinked_at IS NULL
                    """)) {
                statement.setTimestamp(1, Timestamp.from(unlinkedAt));
                statement.setString(2, operationKey);
                statement.setBytes(3, UuidBytes.toBytes(current.linkId()));
                statement.setLong(4, expectedRevision);
                JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "Discord unlink lost revision race");
            }

            ModerationSubjectId sharedSubjectId = current.subjectId();
            deleteMainIfPlayer(connection, sharedSubjectId, minecraftPlayerId);
            ModerationSubjectId standaloneSubject = insertFreshSubject(connection, unlinkedAt);
            moveMinecraftIdentity(connection, minecraftPlayerId, standaloneSubject);
            ensureMainAccount(connection, standaloneSubject, minecraftPlayerId, unlinkedAt);
            if (subjectExists(connection, sharedSubjectId)) {
                bumpSubject(connection, sharedSubjectId, unlinkedAt);
            }

            return new VersionedLink(
                    current.linkId(),
                    sharedSubjectId,
                    new DiscordMinecraftLink(
                            discordUserId,
                            minecraftPlayerId,
                            current.link().linkedAt(),
                            Optional.of(unlinkedAt),
                            current.link().source()
                    ),
                    expectedRevision + 1,
                    false
            );
        });
    }

    VersionedSubject setMainMinecraftAccount(
            ModerationSubjectId subjectId,
            MainMinecraftAccount mainAccount,
            long expectedSubjectRevision,
            Instant selectedAt
    ) {
        requirePresent(subjectId, "subjectId");
        requirePresent(mainAccount, "mainAccount");
        requirePresent(selectedAt, "selectedAt");
        if (expectedSubjectRevision < 0) {
            throw new IllegalArgumentException("expectedSubjectRevision must not be negative");
        }
        return JdbcTransactionSupport.execute(dataSource, "Unable to update main Minecraft account", connection -> {
            long currentRevision = lockSubjectRevision(connection, subjectId);
            if (currentRevision != expectedSubjectRevision) {
                throw new SQLException("moderation subject revision changed before main-account update");
            }
            if (!minecraftIdentityBelongsTo(connection, subjectId, mainAccount.playerId())) {
                throw new SQLException("main Minecraft account does not belong to moderation subject");
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO moderation_subject_main_accounts(
                        subject_id, player_id, selection_source, selected_at, revision
                    ) VALUES (?, ?, ?, ?, 0)
                    ON DUPLICATE KEY UPDATE
                        player_id = VALUES(player_id),
                        selection_source = VALUES(selection_source),
                        selected_at = VALUES(selected_at),
                        revision = revision + 1
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
                statement.setBytes(2, UuidBytes.toBytes(mainAccount.playerId()));
                statement.setString(3, mainAccount.source().name());
                statement.setTimestamp(4, Timestamp.from(selectedAt));
                JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "main-account update was not applied");
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE moderation_subjects
                    SET revision = revision + 1, updated_at = ?
                    WHERE subject_id = ? AND revision = ?
                    """)) {
                statement.setTimestamp(1, Timestamp.from(selectedAt));
                statement.setBytes(2, UuidBytes.toBytes(subjectId.value()));
                statement.setLong(3, expectedSubjectRevision);
                JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "subject revision changed during update");
            }
            return loadSubject(connection, subjectId);
        });
    }

    private VersionedSubject ensureMinecraftSubject(Connection connection, UUID playerId, Instant now)
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
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "Minecraft identity mapping was not inserted");
        }
        ensureMainAccount(connection, subjectId, playerId, now);
        return loadSubject(connection, subjectId);
    }

    private VersionedSubject loadSubject(Connection connection, ModerationSubjectId subjectId) throws SQLException {
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

    private static VersionedLink currentLink(Connection connection, UUID playerId, boolean lock) throws SQLException {
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

    private static VersionedLink linkByOperation(Connection connection, String operationKey, boolean lock)
            throws SQLException {
        return linkByKey(connection, "operation_key", operationKey, lock);
    }

    private static VersionedLink linkByUnlinkOperation(Connection connection, String operationKey, boolean lock)
            throws SQLException {
        return linkByKey(connection, "unlink_operation_key", operationKey, lock);
    }

    private static VersionedLink linkByKey(Connection connection, String column, String key, boolean lock)
            throws SQLException {
        String sql = """
                SELECT link_id, subject_id, discord_user_id, minecraft_player_id,
                       linked_at, unlinked_at, source, revision
                FROM discord_minecraft_links
                WHERE %s = ?
                """.formatted(column) + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
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

    private static VersionedLink replay(VersionedLink value) {
        return new VersionedLink(value.linkId(), value.subjectId(), value.link(), value.revision(), true);
    }

    private static void insertDiscordIdentity(
            Connection connection,
            ModerationSubjectId subjectId,
            DiscordUserId userId,
            Instant linkedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO moderation_subject_discord_identities(discord_user_id, subject_id, linked_at)
                VALUES (?, ?, ?)
                """)) {
            statement.setBigDecimal(1, discordId(userId));
            statement.setBytes(2, UuidBytes.toBytes(subjectId.value()));
            statement.setTimestamp(3, Timestamp.from(linkedAt));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "Discord identity was not inserted");
        }
    }

    private static void moveMinecraftIdentity(
            Connection connection,
            UUID playerId,
            ModerationSubjectId subjectId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE moderation_subject_minecraft_identities SET subject_id = ? WHERE player_id = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            statement.setBytes(2, UuidBytes.toBytes(playerId));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "Minecraft identity was not moved");
        }
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

    private static void deleteMainIfPlayer(
            Connection connection,
            ModerationSubjectId subjectId,
            UUID playerId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM moderation_subject_main_accounts WHERE subject_id = ? AND player_id = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            statement.setBytes(2, UuidBytes.toBytes(playerId));
            JdbcTransactionSupport.requireOptionalSingleUpdate(statement.executeUpdate(), "unexpected main-account delete count");
        }
    }

    private static boolean minecraftIdentityBelongsTo(
            Connection connection,
            ModerationSubjectId subjectId,
            UUID playerId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM moderation_subject_minecraft_identities
                WHERE subject_id = ? AND player_id = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            statement.setBytes(2, UuidBytes.toBytes(playerId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static void cleanupSubjectIfEmpty(Connection connection, ModerationSubjectId subjectId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM moderation_subjects
                WHERE subject_id = ?
                  AND NOT EXISTS (
                      SELECT 1 FROM moderation_subject_minecraft_identities m WHERE m.subject_id = ?
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM moderation_subject_discord_identities d WHERE d.subject_id = ?
                  )
                """)) {
            byte[] id = UuidBytes.toBytes(subjectId.value());
            statement.setBytes(1, id);
            statement.setBytes(2, id);
            statement.setBytes(3, id);
            JdbcTransactionSupport.requireOptionalSingleUpdate(statement.executeUpdate(), "unexpected empty-subject delete count");
        }
    }

    private static void bumpSubject(Connection connection, ModerationSubjectId subjectId, Instant now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE moderation_subjects SET revision = revision + 1, updated_at = ? WHERE subject_id = ?
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setBytes(2, UuidBytes.toBytes(subjectId.value()));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "moderation subject was not revised");
        }
    }

    private static long lockSubjectRevision(Connection connection, ModerationSubjectId subjectId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT revision FROM moderation_subjects WHERE subject_id = ? FOR UPDATE")) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("moderation subject does not exist");
                }
                return result.getLong("revision");
            }
        }
    }

    private static boolean playerExists(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM players WHERE player_id = ?")) {
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
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "moderation subject was not inserted");
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

    private static void requireKey(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(name + " must be nonblank and at most 128 characters");
        }
    }
}
