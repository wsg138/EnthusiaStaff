package net.enthusia.staff.persistence;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore;

/** Owns the isolated D09 write/read pool without taking Flyway ownership from deployment. */
public final class DiscordInvestigationPersistenceRuntime implements AutoCloseable {
    private final HikariDataSource dataSource;
    private final JdbcDiscordInvestigationStore investigations;
    private final JdbcDiscordModerationPersistenceStore identities;

    private DiscordInvestigationPersistenceRuntime(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        this.investigations = new JdbcDiscordInvestigationStore(dataSource);
        this.identities = new JdbcDiscordModerationPersistenceStore(dataSource);
    }

    public static DiscordInvestigationPersistenceRuntime open(DatabaseConfig database) {
        if (database == null) {
            throw new IllegalArgumentException("database must be present");
        }
        return new DiscordInvestigationPersistenceRuntime(MariaDb.open(database));
    }

    public DiscordInvestigationStore investigations() {
        return investigations;
    }

    public ModerationSubjectId ensureDiscordSubject(DiscordUserId userId, Instant now) {
        return identities.ensureDiscordSubject(userId, now).subject().subjectId();
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
