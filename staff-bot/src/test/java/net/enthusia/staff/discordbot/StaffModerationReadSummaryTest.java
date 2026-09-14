package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.history.HistoryQueryOptions;
import net.enthusia.staff.domain.history.ModerationHistoryPage;
import net.enthusia.staff.domain.moderation.DiscordIdentityRef;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.MinecraftIdentityRef;
import net.enthusia.staff.domain.moderation.ModerationIdentity;
import net.enthusia.staff.domain.moderation.ModerationSubject;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerResolution;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedSubject;
import net.enthusia.staff.domain.ports.StaffNoteStore.StaffNote;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import org.junit.jupiter.api.Test;

class StaffModerationReadSummaryTest {
    private static final Instant NOW = Instant.parse("2026-08-31T20:00:00Z");
    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final DiscordUserId DISCORD = new DiscordUserId("123456789012345678");

    @Test
    void snapshotKeepsBoundedRowsButReportsAuthoritativeTotals() {
        StaffModerationReadService service = new StaffModerationReadService(
                new SummaryData(), Clock.fixed(NOW, ZoneOffset.UTC));

        StaffModerationReadService.Snapshot snapshot = service.snapshot(service.discordTarget(DISCORD));

        assertEquals(13L, snapshot.totalHistoryCount());
        assertEquals(Map.of("spam", 4L, "harassment", 1L), snapshot.relevantHistoryCounts());
        assertEquals(List.of(), snapshot.recentHistory());
    }

    private static final class SummaryData implements StaffModerationReadService.ReadData {
        private final VersionedSubject subject = subject();

        @Override
        public Optional<VersionedSubject> subjectForDiscord(DiscordUserId userId) {
            return Optional.of(subject);
        }

        @Override
        public Optional<VersionedSubject> subjectForMinecraft(UUID playerId) {
            return Optional.of(subject);
        }

        @Override
        public PlayerResolution resolvePlayer(String uuidOrUsername) {
            return new PlayerResolution.Missing();
        }

        @Override
        public Optional<PlayerIdentity> player(UUID playerId) {
            return Optional.empty();
        }

        @Override
        public long linkHistoryCountForDiscord(DiscordUserId userId) {
            return 0;
        }

        @Override
        public ModerationHistoryPage historyPage(
                UUID targetId, int page, int pageSize, HistoryQueryOptions options
        ) {
            return new ModerationHistoryPage(targetId, 1, pageSize, 13L, 2, List.of());
        }

        @Override
        public Map<String, Long> relevantCaseCounts(UUID targetId) {
            return Map.of("spam", 4L, "harassment", 1L);
        }

        @Override
        public List<CaseReview> recentCases(UUID targetId, int limit) {
            return List.of();
        }

        @Override
        public Optional<CaseReview> caseReview(CaseId caseId) {
            return Optional.empty();
        }

        @Override
        public List<ActiveSanction> activeSanctions(UUID targetId, Instant now) {
            return List.of();
        }

        @Override
        public List<StaffNote> recentNotes(UUID targetId, int limit) {
            return List.of();
        }
    }

    private static VersionedSubject subject() {
        Set<ModerationIdentity> identities = Set.of(
                new DiscordIdentityRef(DISCORD),
                new MinecraftIdentityRef(PLAYER));
        ModerationSubject subject = new ModerationSubject(
                new ModerationSubjectId(UUID.fromString("11111111-2222-3333-4444-555555555555")),
                identities,
                Optional.empty());
        return new VersionedSubject(subject, 1L);
    }
}
