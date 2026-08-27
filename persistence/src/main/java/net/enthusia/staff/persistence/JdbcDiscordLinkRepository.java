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
import net.enthusia.staff.domain.moderation.AccountLinkAudit;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLink;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLinkSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.MainAccountSelectionSource;
import net.enthusia.staff.domain.moderation.MainMinecraftAccount;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedLink;

/** Single transactional owner for Discord/Minecraft link, unlink and reassignment mutations. */
final class JdbcDiscordLinkRepository {
    private static final int SUBJECT_ALLOCATION_ATTEMPTS = 4;
    private static final int MAX_OPERATION_KEY_LENGTH = 128;
    private static final int MAX_BASE_OPERATION_KEY_LENGTH = 116;

    private final DataSource dataSource;
    private final JdbcDeadlockRetry deadlockRetry;

    JdbcDiscordLinkRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        this.deadlockRetry = new JdbcDeadlockRetry();
    }

    VersionedLink link(
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            DiscordMinecraftLinkSource source,
            String operationKey,
            Instant linkedAt
    ) {
        validateLink(discordUserId, minecraftPlayerId, source, operationKey, linkedAt);
        return deadlockRetry.execute(
                "Interrupted while retrying Discord/Minecraft link",
                () -> JdbcTransactionSupport.execute(
                        dataSource,
                        "Unable to link Discord and Minecraft identities",
                        connection -> link(connection, discordUserId, minecraftPlayerId, source, operationKey, linkedAt)
                )
        );
    }

    VersionedLink linkWithAudit(
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            DiscordMinecraftLinkSource source,
            String operationKey,
            Instant linkedAt,
            AccountLinkAudit audit
    ) {
        validateLink(discordUserId, minecraftPlayerId, source, operationKey, linkedAt);
        requirePresent(audit, "audit");
        return deadlockRetry.execute(
                "Interrupted while retrying audited Discord/Minecraft link",
                () -> JdbcTransactionSupport.execute(
                        dataSource,
                        "Unable to link Discord and Minecraft identities with audit",
                        connection -> {
                            JdbcAccountLinkAuditStore.append(connection, audit);
                            return link(connection, discordUserId, minecraftPlayerId, source, operationKey, linkedAt);
                        }
                )
        );
    }

    VersionedLink unlink(
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            long expectedRevision,
            Optional<MainMinecraftAccount> replacementMain,
            String operationKey,
            Instant unlinkedAt
    ) {
        validateUnlink(discordUserId, minecraftPlayerId, expectedRevision, replacementMain, operationKey, unlinkedAt);
        return deadlockRetry.execute(
                "Interrupted while retrying Discord/Minecraft unlink",
                () -> JdbcTransactionSupport.execute(
                        dataSource,
                        "Unable to unlink Discord and Minecraft identities",
                        connection -> unlink(
                                connection, discordUserId, minecraftPlayerId, expectedRevision,
                                replacementMain, operationKey, unlinkedAt)
                )
        );
    }

    VersionedLink unlinkWithAudit(
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            long expectedRevision,
            Optional<MainMinecraftAccount> replacementMain,
            String operationKey,
            Instant unlinkedAt,
            AccountLinkAudit audit
    ) {
        validateUnlink(discordUserId, minecraftPlayerId, expectedRevision, replacementMain, operationKey, unlinkedAt);
        requirePresent(audit, "audit");
        return deadlockRetry.execute(
                "Interrupted while retrying audited Discord/Minecraft unlink",
                () -> JdbcTransactionSupport.execute(
                        dataSource,
                        "Unable to unlink Discord and Minecraft identities with audit",
                        connection -> {
                            JdbcAccountLinkAuditStore.append(connection, audit);
                            return unlink(
                                    connection, discordUserId, minecraftPlayerId, expectedRevision,
                                    replacementMain, operationKey, unlinkedAt);
                        }
                )
        );
    }

    VersionedLink reassign(
            DiscordUserId newDiscordUserId,
            UUID minecraftPlayerId,
            Optional<MainMinecraftAccount> previousSubjectReplacementMain,
            String operationKey,
            Instant changedAt
    ) {
        validateReassign(newDiscordUserId, minecraftPlayerId, previousSubjectReplacementMain, operationKey, changedAt);
        return deadlockRetry.execute(
                "Interrupted while retrying Discord/Minecraft reassignment",
                () -> JdbcTransactionSupport.execute(
                        dataSource,
                        "Unable to reassign Discord/Minecraft identity",
                        connection -> reassign(
                                connection, newDiscordUserId, minecraftPlayerId,
                                previousSubjectReplacementMain, operationKey, changedAt)
                )
        );
    }

    VersionedLink reassignWithAudit(
            DiscordUserId newDiscordUserId,
            UUID minecraftPlayerId,
            Optional<MainMinecraftAccount> previousSubjectReplacementMain,
            String operationKey,
            Instant changedAt,
            AccountLinkAudit audit
    ) {
        validateReassign(newDiscordUserId, minecraftPlayerId, previousSubjectReplacementMain, operationKey, changedAt);
        requirePresent(audit, "audit");
        return deadlockRetry.execute(
                "Interrupted while retrying audited Discord/Minecraft reassignment",
                () -> JdbcTransactionSupport.execute(
                        dataSource,
                        "Unable to reassign Discord/Minecraft identity with audit",
                        connection -> {
                            JdbcAccountLinkAuditStore.append(connection, audit);
                            return reassign(
                                    connection, newDiscordUserId, minecraftPlayerId,
                                    previousSubjectReplacementMain, operationKey, changedAt);
                        }
                )
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
        LinkSubjects subjects = resolveLinkSubjects(connection, discordUserId, minecraftPlayerId, linkedAt);
        ensureMainAccount(connection, subjects.canonicalSubjectId(), minecraftPlayerId, linkedAt);
        UUID linkId = insertLink(
                connection, subjects.canonicalSubjectId(), discordUserId,
                minecraftPlayerId, source, operationKey, linkedAt);
        bumpSubject(connection, subjects.canonicalSubjectId(), linkedAt);
        return activeVersion(linkId, subjects.canonicalSubjectId(), discordUserId, minecraftPlayerId, source, linkedAt);
    }

    private static LinkSubjects resolveLinkSubjects(
            Connection connection,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            Instant linkedAt
    ) throws SQLException {
        ModerationSubjectId minecraftSubjectId = ensureMinecraftSubject(connection, minecraftPlayerId, linkedAt);
        ModerationSubjectId discordSubjectId = subjectIdForDiscord(connection, discordUserId, true);
        requireNoCurrentOwner(connection, discordUserId, minecraftPlayerId);
        ModerationSubjectId canonicalSubjectId = canonicalizeSubjects(
                connection, discordUserId, minecraftPlayerId, linkedAt, minecraftSubjectId, discordSubjectId);
        return new LinkSubjects(canonicalSubjectId);
    }

    private static void requireNoCurrentOwner(
            Connection connection,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId
    ) throws SQLException {
        VersionedLink active = currentLink(connection, minecraftPlayerId, true);
        if (active == null) {
            return;
        }
        if (active.link().discordUserId().equals(discordUserId)) {
            throw new SQLException("Discord/Minecraft link is already active under a different operation key");
        }
        throw new SQLException("Minecraft identity already has a different current Discord owner");
    }

    private static ModerationSubjectId canonicalizeSubjects(
            Connection connection,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            Instant linkedAt,
            ModerationSubjectId minecraftSubjectId,
            ModerationSubjectId discordSubjectId
    ) throws SQLException {
        if (discordSubjectId == null) {
            insertDiscordIdentity(connection, minecraftSubjectId, discordUserId, linkedAt);
            return minecraftSubjectId;
        }
        if (!discordSubjectId.equals(minecraftSubjectId)) {
            mergeMinecraftOnlySubject(connection, minecraftSubjectId, discordSubjectId, minecraftPlayerId, linkedAt);
        }
        return discordSubjectId;
    }

    private static UUID insertLink(
            Connection connection,
            ModerationSubjectId subjectId,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            DiscordMinecraftLinkSource source,
            String operationKey,
            Instant linkedAt
    ) throws SQLException {
        UUID linkId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_minecraft_links(
                    link_id, operation_key, subject_id, discord_user_id, minecraft_player_id,
                    linked_at, source, revision
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(linkId));
            statement.setString(2, operationKey);
            statement.setBytes(3, UuidBytes.toBytes(subjectId.value()));
            statement.setBigDecimal(4, discordId(discordUserId));
            statement.setBytes(5, UuidBytes.toBytes(minecraftPlayerId));
            statement.setTimestamp(6, Timestamp.from(linkedAt));
            statement.setString(7, source.name());
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "Discord link was not inserted");
        }
        return linkId;
    }

    private static VersionedLink activeVersion(
            UUID linkId,
            ModerationSubjectId subjectId,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            DiscordMinecraftLinkSource source,
            Instant linkedAt
    ) {
        return new VersionedLink(
                linkId,
                subjectId,
                new DiscordMinecraftLink(
                        discordUserId, minecraftPlayerId, linkedAt, Optional.empty(), source),
                0,
                false
        );
    }

    static VersionedLink unlink(
            Connection connection,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            long expectedRevision,
            Optional<MainMinecraftAccount> replacementMain,
            String operationKey,
            Instant unlinkedAt
    ) throws SQLException {
        VersionedLink operationReplay = linkByUnlinkOperation(connection, operationKey, true);
        if (operationReplay != null) {
            return requireMatchingUnlinkReplay(operationReplay, discordUserId, minecraftPlayerId, expectedRevision);
        }
        VersionedLink current = requireCurrentUnlink(
                connection, discordUserId, minecraftPlayerId, expectedRevision, unlinkedAt);
        ModerationSubjectId sharedSubjectId = current.subjectId();
        prepareRemainingMain(connection, sharedSubjectId, minecraftPlayerId, replacementMain, unlinkedAt);
        closeLink(connection, current.linkId(), expectedRevision, operationKey, unlinkedAt);
        detachMinecraftIdentity(connection, sharedSubjectId, minecraftPlayerId, unlinkedAt);
        bumpSubject(connection, sharedSubjectId, unlinkedAt);
        return unlinkedVersion(current, discordUserId, minecraftPlayerId, expectedRevision, unlinkedAt);
    }

    private static VersionedLink requireCurrentUnlink(
            Connection connection,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            long expectedRevision,
            Instant unlinkedAt
    ) throws SQLException {
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
        return current;
    }

    private static void closeLink(
            Connection connection,
            UUID linkId,
            long expectedRevision,
            String operationKey,
            Instant unlinkedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_minecraft_links
                SET unlinked_at = ?, unlink_operation_key = ?, revision = revision + 1
                WHERE link_id = ? AND revision = ? AND unlinked_at IS NULL
                """)) {
            statement.setTimestamp(1, Timestamp.from(unlinkedAt));
            statement.setString(2, operationKey);
            statement.setBytes(3, UuidBytes.toBytes(linkId));
            statement.setLong(4, expectedRevision);
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "Discord unlink lost revision race");
        }
    }

    private static void detachMinecraftIdentity(
            Connection connection,
            ModerationSubjectId sharedSubjectId,
            UUID minecraftPlayerId,
            Instant unlinkedAt
    ) throws SQLException {
        deleteMainIfPlayer(connection, sharedSubjectId, minecraftPlayerId);
        ModerationSubjectId standaloneSubject = insertFreshSubject(connection, unlinkedAt);
        moveMinecraftIdentity(connection, minecraftPlayerId, standaloneSubject, unlinkedAt);
        ensureMainAccount(connection, standaloneSubject, minecraftPlayerId, unlinkedAt);
    }

    private static VersionedLink unlinkedVersion(
            VersionedLink current,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            long expectedRevision,
            Instant unlinkedAt
    ) {
        return new VersionedLink(
                current.linkId(),
                current.subjectId(),
                new DiscordMinecraftLink(
                        discordUserId, minecraftPlayerId, current.link().linkedAt(),
                        Optional.of(unlinkedAt), current.link().source()),
                expectedRevision + 1,
                false
        );
    }

    private static VersionedLink reassign(
            Connection connection,
            DiscordUserId newDiscordUserId,
            UUID minecraftPlayerId,
            Optional<MainMinecraftAccount> previousSubjectReplacementMain,
            String operationKey,
            Instant changedAt
    ) throws SQLException {
        String linkOperationKey = operationKey + ":link";
        String unlinkOperationKey = operationKey + ":unlink";
        VersionedLink replay = linkByOperation(connection, linkOperationKey, true);
        if (replay != null) {
            return requireMatchingReplay(
                    replay, newDiscordUserId, minecraftPlayerId, DiscordMinecraftLinkSource.STAFF_RECOVERY);
        }
        VersionedLink current = currentLink(connection, minecraftPlayerId, true);
        if (current != null && current.link().discordUserId().equals(newDiscordUserId)) {
            return replay(current);
        }
        if (current != null) {
            unlink(
                    connection, current.link().discordUserId(), minecraftPlayerId, current.revision(),
                    previousSubjectReplacementMain, unlinkOperationKey, changedAt);
        }
        return link(
                connection, newDiscordUserId, minecraftPlayerId, DiscordMinecraftLinkSource.STAFF_RECOVERY,
                linkOperationKey, changedAt);
    }

    private static void prepareRemainingMain(
            Connection connection,
            ModerationSubjectId subjectId,
            UUID removingPlayerId,
            Optional<MainMinecraftAccount> replacementMain,
            Instant selectedAt
    ) throws SQLException {
        Set<UUID> remainingPlayers = remainingPlayers(connection, subjectId, removingPlayerId);
        UUID currentMain = currentMainPlayer(connection, subjectId);
        if (remainingPlayers.isEmpty()) {
            requireNoReplacement(replacementMain);
            return;
        }
        if (currentMain != null && remainingPlayers.contains(currentMain)) {
            return;
        }
        MainMinecraftAccount replacement = requireValidReplacement(remainingPlayers, replacementMain);
        writeReplacementMain(connection, subjectId, replacement, selectedAt, currentMain != null);
    }

    private static Set<UUID> remainingPlayers(
            Connection connection,
            ModerationSubjectId subjectId,
            UUID removingPlayerId
    ) throws SQLException {
        Set<UUID> remainingPlayers = new HashSet<>();
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
                        remainingPlayers.add(playerId);
                    }
                }
            }
        }
        return remainingPlayers;
    }

    private static UUID currentMainPlayer(Connection connection, ModerationSubjectId subjectId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT player_id
                FROM moderation_subject_main_accounts
                WHERE subject_id = ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? UuidBytes.fromBytes(result.getBytes("player_id")) : null;
            }
        }
    }

    private static void requireNoReplacement(Optional<MainMinecraftAccount> replacementMain) throws SQLException {
        if (replacementMain.isPresent()) {
            throw new SQLException("unlink replacement became stale because no linked accounts remain");
        }
    }

    private static MainMinecraftAccount requireValidReplacement(
            Set<UUID> remainingPlayers,
            Optional<MainMinecraftAccount> replacementMain
    ) throws SQLException {
        MainMinecraftAccount replacement = replacementMain.orElseThrow(() -> new SQLException(
                "unlink would leave linked Minecraft accounts without a valid replacement main account"));
        if (replacement.source() != MainAccountSelectionSource.AUTOMATIC) {
            throw new SQLException("unlink replacement main must use automatic selection");
        }
        if (!remainingPlayers.contains(replacement.playerId())) {
            throw new SQLException("unlink replacement main is not a remaining linked Minecraft account");
        }
        return replacement;
    }

    private static void writeReplacementMain(
            Connection connection,
            ModerationSubjectId subjectId,
            MainMinecraftAccount replacement,
            Instant selectedAt,
            boolean rowExists
    ) throws SQLException {
        if (rowExists) {
            updateReplacementMain(connection, subjectId, replacement, selectedAt);
        } else {
            insertReplacementMain(connection, subjectId, replacement, selectedAt);
        }
    }

    private static void updateReplacementMain(
            Connection connection,
            ModerationSubjectId subjectId,
            MainMinecraftAccount replacement,
            Instant selectedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE moderation_subject_main_accounts
                SET player_id = ?, selection_source = ?, selected_at = ?, revision = revision + 1
                WHERE subject_id = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(replacement.playerId()));
            statement.setString(2, replacement.source().name());
            statement.setTimestamp(3, Timestamp.from(selectedAt));
            statement.setBytes(4, UuidBytes.toBytes(subjectId.value()));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(), "replacement main account was not updated");
        }
    }

    private static void insertReplacementMain(
            Connection connection,
            ModerationSubjectId subjectId,
            MainMinecraftAccount replacement,
            Instant selectedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO moderation_subject_main_accounts(
                    subject_id, player_id, selection_source, selected_at, revision
                ) VALUES (?, ?, ?, ?, 0)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            statement.setBytes(2, UuidBytes.toBytes(replacement.playerId()));
            statement.setString(3, replacement.source().name());
            statement.setTimestamp(4, Timestamp.from(selectedAt));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(), "replacement main account was not inserted");
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
        insertMinecraftIdentity(connection, playerId, subjectId, now);
        ensureMainAccount(connection, subjectId, playerId, now);
        return subjectId;
    }

    private static void insertMinecraftIdentity(
            Connection connection,
            UUID playerId,
            ModerationSubjectId subjectId,
            Instant linkedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO moderation_subject_minecraft_identities(player_id, subject_id, linked_at)
                VALUES (?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(playerId));
            statement.setBytes(2, UuidBytes.toBytes(subjectId.value()));
            statement.setTimestamp(3, Timestamp.from(linkedAt));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(), "Minecraft identity mapping was not inserted");
        }
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
        requireSingleMinecraftIdentity(connection, subjectId, expectedPlayerId);
        requireNoDiscordIdentity(connection, subjectId);
    }

    private static void requireSingleMinecraftIdentity(
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
                    expectedPlayerPresent |= expectedPlayerId.equals(
                            UuidBytes.fromBytes(result.getBytes("player_id")));
                }
            }
        }
        if (minecraftCount != 1 || !expectedPlayerPresent) {
            throw new SQLException("Minecraft subject cannot be merged because it is not a standalone identity");
        }
    }

    private static void requireNoDiscordIdentity(Connection connection, ModerationSubjectId subjectId)
            throws SQLException {
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
        moveEnforcementTargets(connection, sourceId, targetId, now);
        moveEvidenceMetadata(connection, sourceId, targetId);
        moveSecurityLocks(connection, sourceId, targetId);
        moveLinkHistory(connection, sourceId, targetId);
    }

    private static void moveEnforcementTargets(
            Connection connection, byte[] sourceId, byte[] targetId, Instant now) throws SQLException {
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
    }

    private static void moveEvidenceMetadata(Connection connection, byte[] sourceId, byte[] targetId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_evidence_metadata
                SET subject_id = ?, revision = revision + 1
                WHERE subject_id = ?
                """)) {
            statement.setBytes(1, targetId);
            statement.setBytes(2, sourceId);
            statement.executeUpdate();
        }
    }

    private static void moveSecurityLocks(Connection connection, byte[] sourceId, byte[] targetId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_security_locks
                SET subject_id = ?, revision = revision + 1
                WHERE subject_id = ?
                """)) {
            statement.setBytes(1, targetId);
            statement.setBytes(2, sourceId);
            statement.executeUpdate();
        }
    }

    private static void moveLinkHistory(Connection connection, byte[] sourceId, byte[] targetId)
            throws SQLException {
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
        String sql = """
                SELECT link_id, subject_id, discord_user_id, minecraft_player_id,
                       linked_at, unlinked_at, source, revision
                FROM discord_minecraft_links
                WHERE operation_key = ?
                """ + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationKey);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readLink(result, false) : null;
            }
        }
    }

    private static VersionedLink linkByUnlinkOperation(Connection connection, String operationKey, boolean lock)
            throws SQLException {
        String sql = """
                SELECT link_id, subject_id, discord_user_id, minecraft_player_id,
                       linked_at, unlinked_at, source, revision
                FROM discord_minecraft_links
                WHERE unlink_operation_key = ?
                """ + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationKey);
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
            JdbcTransactionSupport.requireOptionalSingleUpdate(
                    statement.executeUpdate(), "unexpected main-account insert count");
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
            JdbcTransactionSupport.requireOptionalSingleUpdate(
                    statement.executeUpdate(), "unexpected main-account delete count");
        }
    }

    private static void deleteSubject(Connection connection, ModerationSubjectId subjectId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM moderation_subjects WHERE subject_id = ?")) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(), "merged moderation subject was not deleted");
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
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(), "moderation subject was not revised");
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
        for (int attempt = 0; attempt < SUBJECT_ALLOCATION_ATTEMPTS; attempt++) {
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
                    statement.executeUpdate(), "moderation subject was not inserted");
        }
    }

    private static BigDecimal discordId(DiscordUserId userId) {
        return new BigDecimal(userId.value());
    }

    private static DiscordUserId discordUserId(BigDecimal value) {
        return new DiscordUserId(value.toBigIntegerExact().toString());
    }

    private static void validateLink(
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
    }

    private static void validateUnlink(
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            long expectedRevision,
            Optional<MainMinecraftAccount> replacementMain,
            String operationKey,
            Instant unlinkedAt
    ) {
        requirePresent(discordUserId, "discordUserId");
        requirePresent(minecraftPlayerId, "minecraftPlayerId");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision must not be negative");
        }
        requirePresent(replacementMain, "replacementMain");
        requireKey(operationKey, "operationKey");
        requirePresent(unlinkedAt, "unlinkedAt");
    }

    private static void validateReassign(
            DiscordUserId newDiscordUserId,
            UUID minecraftPlayerId,
            Optional<MainMinecraftAccount> previousSubjectReplacementMain,
            String operationKey,
            Instant changedAt
    ) {
        requirePresent(newDiscordUserId, "newDiscordUserId");
        requirePresent(minecraftPlayerId, "minecraftPlayerId");
        requirePresent(previousSubjectReplacementMain, "previousSubjectReplacementMain");
        requireBaseKey(operationKey, "operationKey");
        requirePresent(changedAt, "changedAt");
    }

    private static void requirePresent(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must be present");
        }
    }

    private static void requireKey(String value, String name) {
        if (value == null || value.isBlank() || value.length() > MAX_OPERATION_KEY_LENGTH) {
            throw new IllegalArgumentException(name + " must be nonblank and at most 128 characters");
        }
    }

    private static void requireBaseKey(String value, String name) {
        if (value == null || value.isBlank() || value.length() > MAX_BASE_OPERATION_KEY_LENGTH) {
            throw new IllegalArgumentException(name + " must be nonblank and at most 116 characters");
        }
    }

    private record LinkSubjects(ModerationSubjectId canonicalSubjectId) {
    }
}
