package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
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
import net.enthusia.staff.domain.history.HistoryQueryOptions;
import net.enthusia.staff.domain.history.ModerationHistoryPage;
import net.enthusia.staff.domain.moderation.DiscordIdentityRef;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.MinecraftIdentityRef;
import net.enthusia.staff.domain.moderation.ModerationSubject;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.player.PlayerResolution;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedSubject;
import net.enthusia.staff.domain.ports.StaffNoteStore.StaffNote;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import org.junit.jupiter.api.Test;

class StaffModerationAmbiguityLimitTest {
    private static final Instant NOW = Instant.parse("2026-08-27T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final long INVOKER_ID = 123456789012345678L;
    private static final UUID INVOKER_PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void ambiguitySelectorNeverExceedsDiscordOptionLimit() {
        FakeReadData data = new FakeReadData();
        data.discordSubjects.put(new DiscordUserId(Long.toString(INVOKER_ID)), invokerSubject());
        data.resolution = new PlayerResolution.Ambiguous(identities(30), false);
        StaffModerationController controller = controller(data);

        StaffModerationController.Response response = controller.moderateMinecraft(INVOKER_ID, "helper", "SharedName");

        assertEquals(25, response.choices().size());
        assertTrue(response.content().contains("More matches exist; narrow the username."));
    }

    private static StaffModerationController controller(FakeReadData data) {
        StaffModerationReadService reads = new StaffModerationReadService(data, CLOCK);
        LinkedStaffActorResolver actors = new LinkedStaffActorResolver(
                userId -> reads.discordTarget(userId).subject(),
                playerId -> playerId.equals(INVOKER_PLAYER) ? Optional.of(StaffRank.HELPER) : Optional.empty()
        );
        SignedComponentCodec components = new SignedComponentCodec(
                CLOCK,
                Duration.ofMinutes(5),
                Character.toString('k').repeat(48),
                new SecureRandom(),
                new InteractionReplayGuard(64, Duration.ofMinutes(10))
        );
        return new StaffModerationController(reads, actors, new StaffReadAuthorization(), components);
    }

    private static VersionedSubject invokerSubject() {
        DiscordUserId discord = new DiscordUserId(Long.toString(INVOKER_ID));
        return new VersionedSubject(new ModerationSubject(
                new ModerationSubjectId(UUID.randomUUID()),
                Set.of(new DiscordIdentityRef(discord), new MinecraftIdentityRef(INVOKER_PLAYER)),
                Optional.empty()
        ), 1L);
    }

    private static List<PlayerIdentity> identities(int count) {
        List<PlayerIdentity> identities = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            identities.add(new PlayerIdentity(
                    UUID.nameUUIDFromBytes(("ambiguous-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    Optional.of("SharedName"),
                    PlayerPlatform.JAVA,
                    NOW.minusSeconds(60),
                    NOW
            ));
        }
        return identities;
    }

    private static final class FakeReadData implements StaffModerationReadService.ReadData {
        private final Map<DiscordUserId, VersionedSubject> discordSubjects = new ConcurrentHashMap<>();
        private PlayerResolution resolution = new PlayerResolution.Missing();

        @Override public Optional<VersionedSubject> subjectForDiscord(DiscordUserId userId) { return Optional.ofNullable(discordSubjects.get(userId)); }
        @Override public Optional<VersionedSubject> subjectForMinecraft(UUID playerId) { return Optional.empty(); }
        @Override public PlayerResolution resolvePlayer(String uuidOrUsername) { return resolution; }
        @Override public Optional<PlayerIdentity> player(UUID playerId) { return Optional.empty(); }
        @Override public long linkHistoryCountForDiscord(DiscordUserId userId) { return 0L; }
        @Override public ModerationHistoryPage historyPage(UUID targetId, int page, int pageSize, HistoryQueryOptions options) { return new ModerationHistoryPage(targetId, page, pageSize, 0, 0, List.of()); }
        @Override public List<CaseReview> recentCases(UUID targetId, int limit) { return List.of(); }
        @Override public Optional<CaseReview> caseReview(CaseId caseId) { return Optional.empty(); }
        @Override public List<ActiveSanction> activeSanctions(UUID targetId, Instant now) { return List.of(); }
        @Override public List<StaffNote> recentNotes(UUID targetId, int limit) { return List.of(); }
    }
}
