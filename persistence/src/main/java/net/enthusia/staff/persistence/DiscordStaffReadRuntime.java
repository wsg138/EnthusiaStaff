package net.enthusia.staff.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.history.HistoryQueryOptions;
import net.enthusia.staff.domain.history.ModerationHistoryPage;
import net.enthusia.staff.domain.investigation.InvestigationNote;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerResolution;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.ReconciliationState;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedSubject;
import net.enthusia.staff.domain.ports.StaffNoteStore.StaffNote;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.enthusia.staff.domain.sanction.SanctionType;

/**
 * Narrow read-only database runtime for the isolated staff bot.
 *
 * <p>This deliberately opens the configured MariaDB pool without invoking Flyway. The bot therefore
 * cannot create or upgrade schema as a side effect of a read-only moderation interaction. The pool
 * is also marked JDBC read-only; deployments should still prefer a database principal whose grants
 * are read-only when the hosting provider permits one.</p>
 */
public final class DiscordStaffReadRuntime implements AutoCloseable {
    private final HikariDataSource dataSource;
    private final JdbcDiscordIdentityRepository identities;
    private final JdbcPlayerDirectory players;
    private final JdbcModerationHistoryStore history;
    private final JdbcCaseReviewStore cases;
    private final JdbcSanctionLookup sanctions;
    private final JdbcStaffNoteStore notes;
    private final JdbcDiscordInvestigationNoteStore investigationNotes;

    private DiscordStaffReadRuntime(HikariDataSource dataSource, Clock clock) {
        this.dataSource = dataSource;
        ObjectMapper json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.identities = new JdbcDiscordIdentityRepository(dataSource);
        this.players = new JdbcPlayerDirectory(dataSource);
        this.cases = new JdbcCaseReviewStore(dataSource, clock, json);
        this.history = new JdbcModerationHistoryStore(dataSource, cases);
        this.sanctions = new JdbcSanctionLookup(dataSource);
        this.notes = new JdbcStaffNoteStore(dataSource);
        this.investigationNotes = new JdbcDiscordInvestigationNoteStore(dataSource);
    }

    public static DiscordStaffReadRuntime open(DatabaseConfig database, Clock clock) {
        if (database == null || clock == null) {
            throw new IllegalArgumentException("database and clock must be present");
        }
        HikariDataSource dataSource = MariaDb.openReadOnly(database);
        return new DiscordStaffReadRuntime(dataSource, clock);
    }

    /** A bounded, read-only list for the bot-owned private review queue (no evidence body). */
    public record PendingReview(UUID requestId, UUID targetId, String reasonId, String requiredRank,
            UUID requesterId, Instant createdAt) { }

    /** Read-only dashboard pulse; recent alt alerts are not equivalent to unresolved cases. */
    public record ReviewPulse(long openReports, long claimedReports,
            long pendingPunishmentRequests, long altSignalsInLastDay) { }

    public ReviewPulse reviewPulse(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("review pulse instant is required");
        }
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                    SELECT
                        (SELECT COUNT(*) FROM reports
                          WHERE state IN ('OPEN', 'AWAITING_REVIEW')) AS open_reports,
                        (SELECT COUNT(*) FROM reports WHERE state = 'CLAIMED') AS claimed_reports,
                        (SELECT COUNT(*) FROM punishment_requests
                          WHERE status = 'PENDING' AND expires_at > ?) AS pending_punishments,
                        (SELECT COUNT(*) FROM discord_outbox
                          WHERE event_type LIKE 'ALT_%' AND created_at >= ?) AS alt_signals
                    """)) {
            statement.setTimestamp(1, java.sql.Timestamp.from(now));
            statement.setTimestamp(2, java.sql.Timestamp.from(now.minus(java.time.Duration.ofDays(1))));
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("review pulse query was empty");
                }
                return new ReviewPulse(result.getLong("open_reports"), result.getLong("claimed_reports"),
                        result.getLong("pending_punishments"), result.getLong("alt_signals"));
            }
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Unable to load review pulse", exception);
        }
    }

    public List<PendingReview> pendingReviews(Instant now, int limit) {
        if (now == null || limit < 1 || limit > 10) {
            throw new IllegalArgumentException("review list bound is invalid");
        }
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                    SELECT request_id, target_id, reason_id, required_rank, requester_id, created_at
                      FROM punishment_requests
                     WHERE status = 'PENDING' AND expires_at > ?
                     ORDER BY created_at DESC LIMIT ?
                    """)) {
            statement.setTimestamp(1, java.sql.Timestamp.from(now));
            statement.setInt(2, limit);
            try (var rows = statement.executeQuery()) {
                var pending = new java.util.ArrayList<PendingReview>();
                while (rows.next()) {
                    pending.add(new PendingReview(
                            UuidBytes.fromBytes(rows.getBytes("request_id")),
                            UuidBytes.fromBytes(rows.getBytes("target_id")),
                            rows.getString("reason_id"), rows.getString("required_rank"),
                            UuidBytes.fromBytes(rows.getBytes("requester_id")),
                            rows.getTimestamp("created_at").toInstant()));
                }
                return List.copyOf(pending);
            }
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Unable to read pending punishment reviews", exception);
        }
    }

    public List<InvestigationNote> recentInvestigationNotes(ModerationSubjectId subjectId, int limit) {
        return investigationNotes.recent(subjectId, limit);
    }

    public List<InvestigationNote> recentInvestigationNotes(
            ModerationSubjectId subjectId,
            Optional<InvestigationNote.Visibility> visibility,
            int limit
    ) {
        return investigationNotes.recent(subjectId, visibility, limit);
    }

    public Optional<VersionedSubject> subjectForDiscord(DiscordUserId userId) {
        return identities.subjectForDiscord(userId);
    }

    public Optional<VersionedSubject> subject(ModerationSubjectId subjectId) {
        return identities.subject(subjectId);
    }

    public Optional<VersionedSubject> subjectForMinecraft(UUID playerId) {
        return identities.subjectForMinecraft(playerId);
    }

    /**
     * Reads a bounded snapshot of provider-neutral managed-role claims for StaffBot shadow comparison.
     * The read-only runtime never mutates reconciliation rows.
     */
    public List<ReconciliationState> managedRoleStates(int limit) {
        if (limit < 1 || limit > 10_001) {
            throw new IllegalArgumentException("managed-role state limit is outside its safe range");
        }
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT reconciliation_key, resource_type, resource_id, desired_state_json,
                            observed_state_json, state, attempt_count, next_attempt_at,
                            last_error_code, revision
                     FROM discord_reconciliation_state
                     WHERE resource_type = 'MANAGED_ROLE'
                     ORDER BY reconciliation_key
                     LIMIT ?
                     """)) {
            statement.setInt(1, limit);
            try (var rows = statement.executeQuery()) {
                java.util.ArrayList<ReconciliationState> states = new java.util.ArrayList<>();
                while (rows.next()) {
                    var next = rows.getTimestamp("next_attempt_at");
                    states.add(new ReconciliationState(
                            rows.getString("reconciliation_key"),
                            rows.getString("resource_type"),
                            rows.getString("resource_id"),
                            rows.getString("desired_state_json"),
                            Optional.ofNullable(rows.getString("observed_state_json")),
                            rows.getString("state"),
                            rows.getInt("attempt_count"),
                            Optional.ofNullable(next).map(java.sql.Timestamp::toInstant),
                            Optional.ofNullable(rows.getString("last_error_code")),
                            rows.getLong("revision")
                    ));
                }
                return List.copyOf(states);
            }
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Unable to read managed-role shadow claims", exception);
        }
    }

    public PlayerResolution resolvePlayer(String uuidOrUsername) {
        return players.resolve(uuidOrUsername);
    }

    public Optional<PlayerIdentity> player(UUID playerId) {
        return players.find(playerId.toString());
    }

    public long linkHistoryCountForDiscord(DiscordUserId userId) {
        return JdbcAccountLinkHistoryCountReader.countForDiscord(dataSource, userId);
    }

    public ModerationHistoryPage historyPage(
            UUID targetId,
            int page,
            int pageSize,
            HistoryQueryOptions options
    ) {
        return history.page(targetId, page, pageSize, options);
    }

    public Map<String, Long> relevantCaseCounts(UUID targetId) {
        return JdbcModerationReadSummary.relevantCaseCounts(dataSource, targetId);
    }

    public List<CaseReview> recentCases(UUID targetId, int limit) {
        return cases.recent(targetId, limit);
    }

    public List<CaseReview> recentCases(ModerationSubjectId subjectId, int limit) {
        return cases.recentBySubject(subjectId, limit);
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

    public DiscordPunishmentHistoryReader.Page discordHistory(
            net.enthusia.staff.domain.moderation.DiscordGuildId guildId, DiscordUserId userId, int limit) {
        return new DiscordPunishmentHistoryReader(dataSource).recent(guildId, userId, limit);
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
