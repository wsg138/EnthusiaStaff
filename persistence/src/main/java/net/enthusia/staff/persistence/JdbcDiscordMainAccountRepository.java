package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;
import net.enthusia.staff.domain.moderation.AccountLinkAudit;
import net.enthusia.staff.domain.moderation.MainMinecraftAccount;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedSubject;

final class JdbcDiscordMainAccountRepository {
    private final DataSource dataSource;
    private final JdbcDiscordIdentityRepository identities;
    private final JdbcDeadlockRetry deadlockRetry;

    JdbcDiscordMainAccountRepository(DataSource dataSource, JdbcDiscordIdentityRepository identities) {
        this.dataSource = dataSource;
        this.identities = identities;
        this.deadlockRetry = new JdbcDeadlockRetry();
    }

    VersionedSubject setMainMinecraftAccount(
            ModerationSubjectId subjectId,
            MainMinecraftAccount mainAccount,
            long expectedSubjectRevision,
            Instant selectedAt
    ) {
        validateMutation(subjectId, mainAccount, expectedSubjectRevision, selectedAt);
        deadlockRetry.execute(
                "Interrupted while retrying main-account update",
                () -> JdbcTransactionSupport.execute(
                        dataSource,
                        "Unable to update main Minecraft account",
                        connection -> {
                            applyMainAccount(
                                    connection, subjectId, mainAccount, expectedSubjectRevision, selectedAt);
                            return null;
                        }
                )
        );
        return reload(subjectId, mainAccount);
    }

    boolean setMainMinecraftAccountWithAudit(
            ModerationSubjectId subjectId,
            MainMinecraftAccount mainAccount,
            long expectedSubjectRevision,
            Instant selectedAt,
            AccountLinkAudit audit
    ) {
        validateMutation(subjectId, mainAccount, expectedSubjectRevision, selectedAt);
        if (audit == null) {
            throw new IllegalArgumentException("audit must be present");
        }
        return deadlockRetry.execute(
                "Interrupted while retrying audited main-account update",
                () -> JdbcTransactionSupport.execute(
                        dataSource,
                        "Unable to update main Minecraft account with audit",
                        connection -> applyAuditedMainAccount(
                                connection, subjectId, mainAccount, expectedSubjectRevision, selectedAt, audit)
                )
        );
    }

    private static boolean applyAuditedMainAccount(
            Connection connection,
            ModerationSubjectId subjectId,
            MainMinecraftAccount mainAccount,
            long expectedSubjectRevision,
            Instant selectedAt,
            AccountLinkAudit audit
    ) throws SQLException {
        boolean created = JdbcAccountLinkAuditStore.append(connection, audit);
        if (!created) {
            return false;
        }
        applyMainAccount(connection, subjectId, mainAccount, expectedSubjectRevision, selectedAt);
        return true;
    }

    private VersionedSubject reload(ModerationSubjectId subjectId, MainMinecraftAccount mainAccount) {
        return identities.subjectForMinecraft(mainAccount.playerId())
                .filter(value -> value.subject().subjectId().equals(subjectId))
                .orElseThrow(() -> new ModerationPersistenceException(
                        "Main-account update committed but subject could not be reloaded",
                        new SQLException("moderation subject reload failed")
                ));
    }

    private static void applyMainAccount(
            Connection connection,
            ModerationSubjectId subjectId,
            MainMinecraftAccount mainAccount,
            long expectedSubjectRevision,
            Instant selectedAt
    ) throws SQLException {
        requireExpectedRevision(connection, subjectId, expectedSubjectRevision);
        if (!minecraftIdentityBelongsTo(connection, subjectId, mainAccount.playerId())) {
            throw new SQLException("main Minecraft account does not belong to moderation subject");
        }
        upsertMainAccount(connection, subjectId, mainAccount, selectedAt);
        bumpSubjectRevision(connection, subjectId, expectedSubjectRevision, selectedAt);
    }

    private static void requireExpectedRevision(
            Connection connection,
            ModerationSubjectId subjectId,
            long expectedSubjectRevision
    ) throws SQLException {
        long currentRevision = lockSubjectRevision(connection, subjectId);
        if (currentRevision != expectedSubjectRevision) {
            throw new SQLException("moderation subject revision changed before main-account update");
        }
    }

    private static void upsertMainAccount(
            Connection connection,
            ModerationSubjectId subjectId,
            MainMinecraftAccount mainAccount,
            Instant selectedAt
    ) throws SQLException {
        int updated = updateMainAccount(connection, subjectId, mainAccount, selectedAt);
        if (updated == 0) {
            insertMainAccount(connection, subjectId, mainAccount, selectedAt);
        }
    }

    private static int updateMainAccount(
            Connection connection,
            ModerationSubjectId subjectId,
            MainMinecraftAccount mainAccount,
            Instant selectedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE moderation_subject_main_accounts
                SET player_id = ?, selection_source = ?, selected_at = ?, revision = revision + 1
                WHERE subject_id = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(mainAccount.playerId()));
            statement.setString(2, mainAccount.source().name());
            statement.setTimestamp(3, Timestamp.from(selectedAt));
            statement.setBytes(4, UuidBytes.toBytes(subjectId.value()));
            int updated = statement.executeUpdate();
            JdbcTransactionSupport.requireOptionalSingleUpdate(updated, "unexpected main-account update count");
            return updated;
        }
    }

    private static void insertMainAccount(
            Connection connection,
            ModerationSubjectId subjectId,
            MainMinecraftAccount mainAccount,
            Instant selectedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO moderation_subject_main_accounts(
                    subject_id, player_id, selection_source, selected_at, revision
                ) VALUES (?, ?, ?, ?, 0)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            statement.setBytes(2, UuidBytes.toBytes(mainAccount.playerId()));
            statement.setString(3, mainAccount.source().name());
            statement.setTimestamp(4, Timestamp.from(selectedAt));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "main-account insert was not applied");
        }
    }

    private static void bumpSubjectRevision(
            Connection connection,
            ModerationSubjectId subjectId,
            long expectedSubjectRevision,
            Instant selectedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE moderation_subjects
                SET revision = revision + 1, updated_at = ?
                WHERE subject_id = ? AND revision = ?
                """)) {
            statement.setTimestamp(1, Timestamp.from(selectedAt));
            statement.setBytes(2, UuidBytes.toBytes(subjectId.value()));
            statement.setLong(3, expectedSubjectRevision);
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "subject revision changed during main-account update"
            );
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

    private static boolean minecraftIdentityBelongsTo(
            Connection connection,
            ModerationSubjectId subjectId,
            java.util.UUID playerId
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

    private static void validateMutation(
            ModerationSubjectId subjectId,
            MainMinecraftAccount mainAccount,
            long expectedSubjectRevision,
            Instant selectedAt
    ) {
        if (subjectId == null || mainAccount == null || selectedAt == null) {
            throw new IllegalArgumentException("main-account mutation fields must be present");
        }
        if (expectedSubjectRevision < 0) {
            throw new IllegalArgumentException("expectedSubjectRevision must not be negative");
        }
    }
}
