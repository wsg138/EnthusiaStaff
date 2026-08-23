package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;
import net.enthusia.staff.domain.moderation.MainMinecraftAccount;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedSubject;

final class JdbcDiscordMainAccountRepository {
    private final DataSource dataSource;
    private final JdbcDiscordIdentityRepository identities;

    JdbcDiscordMainAccountRepository(DataSource dataSource, JdbcDiscordIdentityRepository identities) {
        this.dataSource = dataSource;
        this.identities = identities;
    }

    VersionedSubject setMainMinecraftAccount(
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
        JdbcTransactionSupport.execute(dataSource, "Unable to update main Minecraft account", connection -> {
            long currentRevision = lockSubjectRevision(connection, subjectId);
            if (currentRevision != expectedSubjectRevision) {
                throw new SQLException("moderation subject revision changed before main-account update");
            }
            if (!minecraftIdentityBelongsTo(connection, subjectId, mainAccount.playerId())) {
                throw new SQLException("main Minecraft account does not belong to moderation subject");
            }
            int updated;
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE moderation_subject_main_accounts
                    SET player_id = ?, selection_source = ?, selected_at = ?, revision = revision + 1
                    WHERE subject_id = ?
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(mainAccount.playerId()));
                statement.setString(2, mainAccount.source().name());
                statement.setTimestamp(3, Timestamp.from(selectedAt));
                statement.setBytes(4, UuidBytes.toBytes(subjectId.value()));
                updated = statement.executeUpdate();
                JdbcTransactionSupport.requireOptionalSingleUpdate(updated, "unexpected main-account update count");
            }
            if (updated == 0) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO moderation_subject_main_accounts(
                            subject_id, player_id, selection_source, selected_at, revision
                        ) VALUES (?, ?, ?, ?, 0)
                        """)) {
                    statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
                    statement.setBytes(2, UuidBytes.toBytes(mainAccount.playerId()));
                    statement.setString(3, mainAccount.source().name());
                    statement.setTimestamp(4, Timestamp.from(selectedAt));
                    JdbcTransactionSupport.requireSingleUpdate(
                            statement.executeUpdate(),
                            "main-account insert was not applied"
                    );
                }
            }
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
            return null;
        });
        return identities.subjectForMinecraft(mainAccount.playerId())
                .filter(value -> value.subject().subjectId().equals(subjectId))
                .orElseThrow(() -> new ModerationPersistenceException(
                        "Main-account update committed but subject could not be reloaded",
                        new SQLException("moderation subject reload failed")
                ));
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
}
