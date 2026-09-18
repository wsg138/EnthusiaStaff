package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.casefile.CaseState;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.history.HistoryQueryOptions;
import net.enthusia.staff.domain.history.ModerationHistoryPage;
import net.enthusia.staff.domain.investigation.InvestigationNote;
import net.enthusia.staff.domain.moderation.DiscordIdentityRef;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.MainAccountSelectionSource;
import net.enthusia.staff.domain.moderation.MainMinecraftAccount;
import net.enthusia.staff.domain.moderation.MinecraftIdentityRef;
import net.enthusia.staff.domain.moderation.ModerationIdentity;
import net.enthusia.staff.domain.moderation.ModerationSubject;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.player.PlayerResolution;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedSubject;
import net.enthusia.staff.domain.ports.StaffNoteStore.StaffNote;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import org.junit.jupiter.api.Test;

class StaffModerationReadServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-27T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void discordOnlySubjectStaysDiscordOnlyWithoutInventingMinecraftIdentity() {
        DiscordUserId discord = new DiscordUserId("123456789012345678");
        FakeReadData data = new FakeReadData();
        data.discordSubjects.put(discord, subject(Set.of(new DiscordIdentityRef(discord)), Optional.empty()));

        StaffModerationReadService service = new StaffModerationReadService(data, CLOCK);
        StaffModerationReadService.Snapshot snapshot = service.snapshot(service.discordTarget(discord));

        assertEquals(StaffModerationReadService.TargetKind.DISCORD, snapshot.target().kind());
        assertEquals(Optional.of(discord), snapshot.target().discordId());
        assertTrue(snapshot.target().minecraftId().isEmpty());
        assertTrue(snapshot.linkedMinecraft().isEmpty());
        assertTrue(snapshot.activeMinecraftSanctions().isEmpty());
        assertTrue(snapshot.recentHistory().isEmpty());
        assertTrue(snapshot.recentCases().isEmpty());
        assertTrue(snapshot.recentNotes().isEmpty());
    }

    @Test
    void bedrockLinkedSubjectPreservesPlatformAndMainAccount() {
        DiscordUserId discord = new DiscordUserId("223456789012345678");
        UUID player = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        VersionedSubject subject = subject(
                Set.of(new DiscordIdentityRef(discord), new MinecraftIdentityRef(player)),
                Optional.of(new MainMinecraftAccount(player, MainAccountSelectionSource.AUTOMATIC))
        );
        FakeReadData data = new FakeReadData();
        data.discordSubjects.put(discord, subject);
        data.minecraftSubjects.put(player, subject);
        data.players.put(player, new PlayerIdentity(
                player,
                Optional.of("BedrockLinked"),
                PlayerPlatform.BEDROCK,
                NOW.minusSeconds(3_600),
                NOW
        ));

        StaffModerationReadService service = new StaffModerationReadService(data, CLOCK);
        StaffModerationReadService.Snapshot snapshot = service.snapshot(service.discordTarget(discord));

        assertEquals(1, snapshot.linkedMinecraft().size());
        StaffModerationReadService.LinkedMinecraft linked = snapshot.linkedMinecraft().getFirst();
        assertEquals(player, linked.playerId());
        assertEquals(Optional.of("BedrockLinked"), linked.username());
        assertEquals(PlayerPlatform.BEDROCK, linked.platform());
        assertTrue(linked.main());
    }

    @Test
    void ambiguousMinecraftNameIsReturnedAsSelectorInputInsteadOfGuessed() {
        PlayerIdentity first = identity(
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                "SharedName",
                PlayerPlatform.JAVA
        );
        PlayerIdentity second = identity(
                UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa"),
                "SharedName",
                PlayerPlatform.BEDROCK
        );
        FakeReadData data = new FakeReadData();
        data.resolution = new PlayerResolution.Ambiguous(List.of(first, second), true);

        StaffModerationReadService service = new StaffModerationReadService(data, CLOCK);
        StaffModerationReadService.MinecraftResolution result = service.resolveMinecraft("SharedName");

        StaffModerationReadService.MinecraftResolution.Ambiguous ambiguous = assertInstanceOf(
                StaffModerationReadService.MinecraftResolution.Ambiguous.class,
                result
        );
        assertEquals(List.of(first, second), ambiguous.matches());
        assertTrue(ambiguous.truncated());
    }

    @Test
    void privateInvestigationNotesRespectManagementVisibility() {
        DiscordUserId discord = new DiscordUserId("323456789012345678");
        VersionedSubject subject = subject(Set.of(new DiscordIdentityRef(discord)), Optional.empty());
        ModerationSubjectId subjectId = subject.subject().subjectId();
        FakeReadData data = new FakeReadData();
        data.discordSubjects.put(discord, subject);
        data.subjects.put(subjectId, subject);
        data.investigationNotes = List.of(
                note(subjectId, InvestigationNote.Visibility.MANAGEMENT, "management only", 2),
                note(subjectId, InvestigationNote.Visibility.STAFF, "staff visible", 1)
        );
        StaffModerationReadService service = new StaffModerationReadService(data, CLOCK);
        StaffModerationReadService.Target target = service.discordTarget(discord);

        assertEquals(List.of("staff visible"), service.investigationNotes(target, StaffRank.MOD).stream()
                .map(InvestigationNote::text).toList());
        assertEquals(List.of("management only", "staff visible"), service.investigationNotes(target, StaffRank.ADMIN).stream()
                .map(InvestigationNote::text).toList());
    }

    @Test
    void nonManagementVisibilityIsFilteredBeforePanelLimit() {
        DiscordUserId discord = new DiscordUserId("333456789012345678");
        VersionedSubject subject = subject(Set.of(new DiscordIdentityRef(discord)), Optional.empty());
        ModerationSubjectId subjectId = subject.subject().subjectId();
        FakeReadData data = new FakeReadData();
        data.discordSubjects.put(discord, subject);
        List<InvestigationNote> notes = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            notes.add(note(subjectId, InvestigationNote.Visibility.MANAGEMENT, "hidden-" + index, 20 - index));
        }
        for (int index = 0; index < 8; index++) {
            notes.add(note(subjectId, InvestigationNote.Visibility.STAFF, "visible-" + index, 10 - index));
        }
        data.investigationNotes = List.copyOf(notes);
        StaffModerationReadService service = new StaffModerationReadService(data, CLOCK);

        List<InvestigationNote> visible = service.investigationNotes(service.discordTarget(discord), StaffRank.MOD);
        assertEquals(8, visible.size());
        assertTrue(visible.stream().allMatch(note -> note.visibility() == InvestigationNote.Visibility.STAFF));
    }

    @Test
    void discordOnlyCaseRoutesThroughAuthoritativeModerationSubject() {
        DiscordUserId discord = new DiscordUserId("423456789012345678");
        VersionedSubject subject = subject(Set.of(new DiscordIdentityRef(discord)), Optional.empty());
        ModerationSubjectId subjectId = subject.subject().subjectId();
        FakeReadData data = new FakeReadData();
        data.subjects.put(subjectId, subject);
        StaffModerationReadService service = new StaffModerationReadService(data, CLOCK);
        CaseReview review = new CaseReview(
                new CaseId("0123456789ABCDEF"), null, Optional.of(subjectId), UUID.randomUUID(), "Staff", "MOD",
                "Private Discord investigation", "DISCORD_INVESTIGATION", "DISCORD_INVESTIGATION", "summary",
                "discord-d09-v1", CaseVisibility.PRIVATE, CaseState.OPEN, NOW, 0,
                Optional.empty(), List.of(), Optional.empty()
        );

        StaffModerationReadService.Target target = service.caseTarget(review);
        assertEquals(StaffModerationReadService.TargetKind.DISCORD, target.kind());
        assertEquals(Optional.of(discord), target.discordId());
        assertTrue(target.minecraftId().isEmpty());
    }

    private static InvestigationNote note(
            ModerationSubjectId subjectId,
            InvestigationNote.Visibility visibility,
            String text,
            long revision
    ) {
        UUID actor = UUID.fromString("50000000-0000-0000-0000-000000000001");
        return new InvestigationNote(
                UUID.randomUUID(), subjectId,
                new InvestigationNote.Scope(InvestigationNote.ScopeType.SUBJECT, subjectId.value().toString()),
                visibility, text, actor, NOW.minusSeconds(revision), actor, NOW, revision, false
        );
    }

    private static VersionedSubject subject(
            Set<ModerationIdentity> identities,
            Optional<MainMinecraftAccount> main
    ) {
        return new VersionedSubject(new ModerationSubject(
                new ModerationSubjectId(UUID.randomUUID()),
                identities,
                main
        ), 1L);
    }

    private static PlayerIdentity identity(UUID playerId, String username, PlayerPlatform platform) {
        return new PlayerIdentity(playerId, Optional.of(username), platform, NOW.minusSeconds(60), NOW);
    }

    private static final class FakeReadData implements StaffModerationReadService.ReadData {
        private final Map<DiscordUserId, VersionedSubject> discordSubjects = new ConcurrentHashMap<>();
        private final Map<UUID, VersionedSubject> minecraftSubjects = new ConcurrentHashMap<>();
        private final Map<ModerationSubjectId, VersionedSubject> subjects = new ConcurrentHashMap<>();
        private final Map<UUID, PlayerIdentity> players = new ConcurrentHashMap<>();
        private List<InvestigationNote> investigationNotes = List.of();
        private PlayerResolution resolution = new PlayerResolution.Missing();

        @Override
        public Optional<VersionedSubject> subjectForDiscord(DiscordUserId userId) {
            return Optional.ofNullable(discordSubjects.get(userId));
        }

        @Override
        public Optional<VersionedSubject> subjectForMinecraft(UUID playerId) {
            return Optional.ofNullable(minecraftSubjects.get(playerId));
        }

        @Override
        public Optional<VersionedSubject> subject(ModerationSubjectId subjectId) {
            return Optional.ofNullable(subjects.get(subjectId));
        }

        @Override
        public PlayerResolution resolvePlayer(String uuidOrUsername) {
            return resolution;
        }

        @Override
        public Optional<PlayerIdentity> player(UUID playerId) {
            return Optional.ofNullable(players.get(playerId));
        }

        @Override
        public long linkHistoryCountForDiscord(DiscordUserId userId) {
            return 0L;
        }

        @Override
        public ModerationHistoryPage historyPage(
                UUID targetId,
                int page,
                int pageSize,
                HistoryQueryOptions options
        ) {
            return new ModerationHistoryPage(targetId, page, pageSize, 0, 0, List.of());
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

        @Override
        public List<InvestigationNote> recentInvestigationNotes(ModerationSubjectId subjectId, int limit) {
            return investigationNotes.stream().filter(note -> note.subjectId().equals(subjectId)).limit(limit).toList();
        }

        @Override
        public List<InvestigationNote> recentInvestigationNotes(
                ModerationSubjectId subjectId,
                Optional<InvestigationNote.Visibility> visibility,
                int limit
        ) {
            return investigationNotes.stream()
                    .filter(note -> note.subjectId().equals(subjectId))
                    .filter(note -> visibility.isEmpty() || note.visibility() == visibility.orElseThrow())
                    .limit(limit)
                    .toList();
        }
    }
}
