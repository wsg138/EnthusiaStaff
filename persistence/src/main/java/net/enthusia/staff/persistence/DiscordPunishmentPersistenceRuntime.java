package net.enthusia.staff.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.CrossPlatformIdentityLookup;
import net.enthusia.staff.domain.ports.CrossPlatformPunishmentStore;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository;
import net.enthusia.staff.persistence.migration.FencedCrossPlatformPunishmentStore;

/**
 * Narrow read/write D07 runtime for the isolated staff bot.
 *
 * <p>This opens the configured pool but deliberately does not run Flyway. Schema ownership remains
 * with the normal EnthusiaStaff deployment lifecycle; the staff bot fails if V19 is unavailable.</p>
 */
public final class DiscordPunishmentPersistenceRuntime implements AutoCloseable {
    private final HikariDataSource dataSource;
    private final JdbcDiscordPunishmentRepository punishments;
    private final JdbcDiscordModerationPersistenceStore identities;
    private final CrossPlatformPunishmentStore crossPlatformPunishments;
    private final CrossPlatformIdentityLookup crossPlatformIdentities;

    private DiscordPunishmentPersistenceRuntime(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        ObjectMapper json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.punishments = new JdbcDiscordPunishmentRepository(dataSource);
        this.identities = new JdbcDiscordModerationPersistenceStore(dataSource);
        JdbcModerationStore moderation = new JdbcModerationStore(dataSource, json);
        this.crossPlatformPunishments = new FencedCrossPlatformPunishmentStore(
                dataSource,
                new JdbcCrossPlatformPunishmentStore(dataSource, moderation, punishments)
        );
        this.crossPlatformIdentities = new DiscordCrossPlatformIdentityLookup(identities);
    }

    public static DiscordPunishmentPersistenceRuntime open(DatabaseConfig database) {
        if (database == null) {
            throw new IllegalArgumentException("database must be present");
        }
        return new DiscordPunishmentPersistenceRuntime(MariaDb.open(database));
    }

    public DiscordPunishmentRepository punishments() {
        return punishments;
    }

    public ModerationSubjectId ensureDiscordSubject(DiscordUserId userId, Instant now) {
        return identities.ensureDiscordSubject(userId, now).subject().subjectId();
    }

    public CrossPlatformPunishmentStore crossPlatformPunishments() {
        return crossPlatformPunishments;
    }

    public CrossPlatformIdentityLookup crossPlatformIdentities() {
        return crossPlatformIdentities;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
