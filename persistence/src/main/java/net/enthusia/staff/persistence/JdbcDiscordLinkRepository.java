package net.enthusia.staff.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLink;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLinkSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedLink;

/** Single transactional owner for Discord/Minecraft link, unlink and reassignment mutations. */
final class JdbcDiscordLinkRepository {
    private final DataSource dataSource;

    JdbcDiscordLinkRepository(DataSource dataSource) {
        this.dataSource = dataSource;
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
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to link Discord and Minecraft identities",
                connection -> link(connection, discordUserId, minecraftPlayerId, source, operationKey, linkedAt)
        );
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
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to unlink Discord and Minecraft identities",
                connection -> unlink(
                        connection,
                        discordUserId,
                        minecraftPlayerId,
                        expectedRevision,
                        operationKey,
                        unlinkedAt
                )
        );
    }

    VersionedLink reassign(
            DiscordUserId newDiscordUserId,
            UUID minecraftPlayerId,
            String operationKey,
            Instant changedAt
    ) {
        requirePresent(newDiscordUserId, "newDiscordUserId");
        requirePresent(minecraftPlayerId, "minecraftPlayerId");
        requireBaseKey(operationKey, "operationKey");
        requirePresent(changedAt, "changedAt");
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to reassign Discord/Minecraft identity",
                connection -> reassign(connection, newDiscordUserId, minecraftPlayerId, operationKey, changedAt)
        );
    }

    static VersionedLink link(
            Connection connection,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            DiscordMinecraftLinkSource source,
            String operationKey,
            Instant linkedAt
    ) throws SQLException {
        VersionedLink operationReplay = linkByOperation(connection, operationKey, true);
        if (operationReplay != null) {
            return requireMatchingReplay(operationReplay, discordUserId, minecraftPlayerId, source);
        }

        ModerationSubjectId minecraftSubjectId = ensureMinecraftSubject(connection, minecraftPlayerId, linkedAt);
        ModerationSubjectId discordSubjectId = subjectIdForDiscord(connection, discordUserId, true);
        VersionedLink active = currentLink(connection, minecraftPlayerId, true);
        if (active != null) {
            if (active.link().discordUserId().equals(discordUserId)) {
                throw new SQLException("Discord/Minecraft link is already active under a different operation key");
            }
            throw new SQLException("Minecraft identity already has a different current Discord owner");
        }

        ModerationSubjectId canonicalSubjectId;
        if (discordSubjectId == null) {
            canonicalSubjectId = minecraftSubjectId;
            insertDiscordIdentity(connection, canonicalSubjectId, discordUserId, linkedAt);
        } else {
            canonicalSubjectId = discordSubjectId;
            if (!canonicalSubjectId.equals(minecraftSubjectId)) {
                mergeMinecraftOnlySubject(
                        connection,
                        minecraftSubjectId,
                        canonicalSubjectId,
                        minecraftPlayerId,
                        linkedAt
                );
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
    }

    static VersionedLink unlink(
            Connection connection,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            long expectedRevision,
            String operationKey,
            Instant unlinkedAt
    ) throws SQLException {
        VersionedLink operationReplay = linkByUnlinkOperation(connection, operationKey, true);
        if (operationReplay != null) {
            return requireMatchingUnlinkReplay(
                    operationReplay,
                    discordUserId,
                    minecraftPlayerId,
                    expectedRevision
            );
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

        ModerationSubjectId sharedSubjectId = current.subjectId();
        requireValidRemainingMain(connection, sharedSubjectId, minecraftPlayerId);

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

        deleteMainIfPlayer(connection, sharedSubjectId, minecraftPlayerId);
        ModerationSubjectId standaloneSubject = insertFreshSubject(connection, unlinkedAt);
        moveMinecraftIdentity(connection, minecraftPlayerId, standaloneSubject, unlinkedAt);
        ensureMainAccount(connection, standaloneSubject, minecraftPlayerId, unlinkedAt);
        bumpSubject(connection, sharedSubjectId, unlinkedAt);

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
    }

    private static VersionedLink reassign(
            Connection connection,
            DiscordUserId newDiscordUserId,
            UUID minecraftPlayerId,
            String operationKey,
            Instant changedAt
    ) throws SQLException {
        String linkOperationKey = operationKey + ":link";
        String unlinkOperationKey = operationKey + ":unlink";
        VersionedLink replay = linkByOperation(connection, linkOperationKey, true);
        if (replay != null) {
            return requireMatchingReplay(
                    replay,
                    newDiscordUserId,
                    minecraftPlayerId,
                    DiscordMinecraftLinkSource.STAFF_RECOVERY
            );
        }

        VersionedLink current = currentLink(connection, minecraftPlayerId, true);
        if (current != null && current.link().discordUserId().equals(newDiscordUserId)) {
            return new VersionedLink(
                    current.linkId(), current.subjectId(), current.link(), current.revision(), true);
        }
        if (current != null) {
            unlink(
                    connection,
                    current.link().discordUserId(),
                    minecraftPlayerId,
                    current.revision(),
                    unlinkOperationKey,
                    changedAt
            );
        }
        return link(
                connection,
                newDiscordUserId,
                minecraftPlayerId,
                DiscordMinecraftLinkSource.STAFF_RECOVERY,
                linkOperationKey,
                changedAt
        );
    }

    private static void requireValidRemainingMain(
            Connection connection,
            ModerationSubjectId subjectId,
            UUID removingPlayerId
    ) throws SQLException {
        int remaining = 0;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT player_id
                FROM moderation_subject_minecraft_identities
                WHERE subject_id = ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    UUID playerId = UuidBytes.fromBytes(result.getBytes("player_id"));
                    if (!playerId.equals(removingPlayerId)) {
                        remaining++;
                    }
                }
            }
        }
        if (remaining == 0) {
            return;
        }

        UUID main = null;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT player_id
                FROM moderation_subject_main_accounts
                WHERE subject_id = ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    main = UuidBytes.fromBytes(result.getBytes("player_id"));
                }
            }
        }
        if (main == null || main.equals(removingPlayerId)) {
            throw new SQLException(
                    "unlink would leave linked Minecraft accounts without a valid replacement main account"
            );
        }
    }

    private static ModerationSubjectId ensureMinecraftSubject(
            Connection connection,
            UUID playerId,
            Instant now
    ) throws SQLException {
        ModerationSubjectId existing = subjectIdForMinecraft(connection, playerId, true);
        if (existing != null) {
            return existing;
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
        return subjectId;
    }

    private static void mergeMinecraftOnlySubject(
            Connection connection,
            ModerationSubjectId sourceSubjectId,
            ModerationSubjectId targetSubjectId,
            UUID minecraftPlayerId,
            Instant now
    ) throws SQLException {
        requireStandaloneMinecraftSubject(connection, sourceSubjectId, minecraftPlayerId);
        deleteMainIfPlayer(connection, sourceSubjectId, minecraftPlayerId);
        moveSubjectReferences(connection, sourceSubjectId, targetSubjectId, now);
        moveMinecraftIdentity(connection, minecraftPlayerId, targetSubjectId, now);
        deleteSubject(connection, sourceSubjectId);
    }

    private static void requireStandaloneMinecraftSubject(
            Connection connection,
            ModerationSubjectId subjectId,
            UUID expectedPlayerId
    ) throws SQLException {
        int minecraftCount = 0;
        boolean expectedPlayerPresent = false;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT player_id
                FROM moderation_subject_minecraft_identities
                WHERE subject_id = ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    minecraftCount++;
                    if (expectedPlayerId.equals(UuidBytes.fromBytes(result.getBytes("player_id")))) {
                        expectedPlayerPresent = true;
                    }
                }
            }
        }
        if (minecraftCount != 1 || !expectedPlayerPresent) {
            throw new SQLException("Minecraft subject cannot be merged because it is not a standalone identity");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT discord_user_id
                FROM moderation_subject_discord_identities
                WHERE subject_id = ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    throw new SQLException("Minecraft subject cannot be merged while it owns a Discord identity");
                }
            }
        }
    }

    private static void moveSubjectReferences(
            Connection connection,
            ModerationSubjectId sourceSubjectId,
            ModerationSubjectId targetSubjectId,
            Instant now
    ) throws SQLException {
        byte[] sourceId = UuidBytes.toBytes(sourceSubjectId.value());
        byte[] targetId = UuidBytes.toBytes(targetSubjectId.value());
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE moderation_enforcement_targets
                SET subject_id = ?, revision = revision + 1, updated_at = ?
                WHERE subject_id = ?
                """)) {
            statement.setBytes(1, targetId);
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setBytes(3, sourceId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_evidence_metadata
                SET subject_id = ?, revision = revision + 1
                WHERE subject_id = ?
                """)) {
            statement.setBytes(1, targetId);
            statement.setBytes(2, sourceId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_security_locks
                SET subject_id = ?, revision = revision + 1
                WHERE subject_id = ?
                """)) {
            statement.setBytes(1, targetId);
            statement.setBytes(2, sourceId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_minecraft_links
                SET subject_id = ?, revision = revision + 1
                WHERE subject_id = ?
                """)) {
            statement.setBytes(1, targetId);
            statement.setBytes(2, sourceId);
            statement.executeUpdate();
        }
    }

    private static VersionedLink requireMatchingReplay(
            VersionedLink stored,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            DiscordMinecraftLinkSource source
    ) throws SQLException {
        DiscordMinecraftLink link = stored.link();
        if (!discordUserId.equals(link.discordUserId())
                || !minecraftPlayerId.equals(link.minecraftPlayerId())
                || source != link.source()) {
            throw new SQLException("Discord link operation key was reused for a different request");
        }
        return replay(stored);
    }

    private static VersionedLink requireMatchingUnlinkReplay(
            VersionedLink stored,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            long expectedRevision
    ) throws SQLException {
        if (!discordUserId.equals(stored.link().discordUserId())
                || !minecraftPlayerId.equals(stored.link().minecraftPlayerId())
                || stored.revision() != expectedRevision + 1) {
            throw new SQLException("Discord unlink operation key was reused for a different request");
        }
        return replay(stored);
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
            ModerationSubjectId subjectId,
            Instant linkedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE moderation_subject_minecraft_identities
                SET subject_id = ?, linked_at = ?
                WHERE player_id = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            statement.setTimestamp(2, Timestamp.from(linkedAt));
            statement.setBytes(3, UuidBytes.toBytes(playerId));
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
            JdbcTransactionSupport.requireOptionalSingleUpdate(statement.executeUpdate(), "unexpected main-account insert count");
        }
    }

    private static void deleteMainIfPlayer(
            Connection connection,
            ModerationSubjectId subjectId,
            UUID playerId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM moderation_subject_main_accounts
                WHERE subject_id = ? AND player_id = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            statement.setBytes(2, UuidBytes.toBytes(playerId));
            JdbcTransactionSupport.requireOptionalSingleUpdate(statement.executeUpdate(), "unexpected main-account delete count");
        }
    }

    private static void deleteSubject(Connection connection, ModerationSubjectId subjectId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM moderation_subjects WHERE subject_id = ?")) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "merged moderation subject was not deleted");
        }
    }

    private static void bumpSubject(Connection connection, ModerationSubjectId subjectId, Instant now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE moderation_subjects
                SET revision = revision + 1, updated_at = ?
                WHERE subject_id = ?
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setBytes(2, UuidBytes.toBytes(subjectId.value()));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "moderation subject was not revised");
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

    private static void requireBaseKey(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 116) {
            throw new IllegalArgumentException(name + " must be nonblank and at most 116 characters");
        }
    }
}
