package net.enthusia.staff.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.history.HistoryQueryOptions;
import net.enthusia.staff.domain.history.ModerationHistoryPage;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerResolution;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedLink;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedSubject;
import net.enthusia.staff.domain.ports.StaffNoteStore.StaffNote;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.enthusia.staff.domain.sanction.SanctionType;

/**
 * Narrow read-only database runtime for the isolated staff bot.
 *
 * <p>This deliberately opens the configured MariaDB pool without invoking Flyway. The bot therefore
 * cannot create or upgrade schema as a side effect of a read-only moderation interaction. Deployment
 * should use a database principal whose grants are read-only.</p>
 */
public final class DiscordStaffReadRuntime implements AutoCloseable {
    private final HikariDataSource dataSource;
    private final JdbcDiscordModerationPersistenceStore identities;
    private final JdbcAccountLinkingStore links;
    private final JdbcPlayerDirectory players;
    private final JdbcModerationHistoryStore history;
    private final JdbcCaseReviewStore cases;
    private final JdbcSanctionLookup sanctions;
    private final JdbcStaffNoteStore notes;

    private DiscordStaffReadRuntime(HikariDataSource dataSource, Clock clock) {
        this.dataSource = dataSource;
        ObjectMapper json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.identities = new JdbcDiscordModerationPersistenceStore(dataSource);
        this.links = new JdbcAccountLinkingStore(dataSource);
        this.players = new JdbcPlayerDirectory(dataSource);
        this.cases = new JdbcCaseReviewStore(dataSource, clock, json);
        this.history = new JdbcModerationHistoryStore(dataSource, cases);
        this.sanctions = new JdbcSanctionLookup(dataSource);
        this.notes = new JdbcStaffNoteStore(dataSource);
    }

    public static DiscordStaffReadRuntime open(DatabaseConfig database, Clock clock) {
        if (database == null || clock == null) {
            throw new IllegalArgumentException("database and clock must be present");
        }
        HikariDataSource dataSource = MariaDb.open(database);
        return new DiscordStaffReadRuntime(dataSource, clock);
    }

    public Optional<VersionedSubject> subjectForDiscord(DiscordUserId userId) {
        return identities.subjectForDiscord(userId);
    }

    public Optional<VersionedSubject> subjectForMinecraft(UUID playerId) {
        return identities.subjectForMinecraft(playerId);
    }

    public PlayerResolution resolvePlayer(String uuidOrUsername) {
        return players.resolve(uuidOrUsername);
    }

    public Optional<PlayerIdentity> player(UUID playerId) {
        return players.find(playerId.toString());
    }

    public List<VersionedLink> linkHistoryForDiscord(DiscordUserId userId) {
        return links.historyForDiscord(userId);
    }

    public ModerationHistoryPage historyPage(
            UUID targetId,
            int page,
            int pageSize,
            HistoryQueryOptions options
    ) {
        return history.page(targetId, page, pageSize, options);
    }

    public List<CaseReview> recentCases(UUID targetId, int limit) {
        return cases.recent(targetId, limit);
    }

    public Optional<CaseReview> caseReview(CaseId caseId) {
        return cases.find(caseId);
    }

    public List<ActiveSanction> activeSanctions(UUID targetId, Instant now) {
        return sanctions.activeFor(targetId, EnumSet.allOf(SanctionType.class), now);
    }

    public List<StaffNote> recentNotes(UUID targetId, int limit) {
        return notes.recent(targetId, limit);
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
