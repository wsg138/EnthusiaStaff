package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.history.HistoryQueryOptions;
import net.enthusia.staff.domain.history.ModerationHistoryPage;
import net.enthusia.staff.domain.moderation.DiscordIdentityRef;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.MainAccountSelectionSource;
import net.enthusia.staff.domain.moderation.MainMinecraftAccount;
import net.enthusia.staff.domain.moderation.MinecraftIdentityRef;
import net.enthusia.staff.domain.moderation.ModerationSubject;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.player.PlayerResolution;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedLink;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedSubject;
import net.enthusia.staff.domain.ports.StaffNoteStore.StaffNote;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import org.junit.jupiter.api.Test;

class StaffModerationControllerTest {
    private static final Instant NOW = Instant.parse("2026-08-27T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final long INVOKER_DISCORD_ID = 123456789012345678L;
    private static final long TARGET_DISCORD_ID = 223456789012345678L;
    private static final UUID INVOKER_PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID TARGET_PLAYER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Test
    void roleOnlyDiscordUserCannotReadPrivateTargetLinks() {
        FakeReadData data = linkedData();
        StaffModerationController controller = controller(data, ignored -> Optional.empty());

        StaffModerationController.Response response = controller.moderateDiscord(
                INVOKER_DISCORD_ID,
                "role-only-user",
                TARGET_DISCORD_ID
        );

        assertTrue(response.content().contains("not linked to a current Enthusia staff identity"));
        assertFalse(response.content().contains("PrivateBedrockAlt"));
        assertTrue(response.buttons().isEmpty());
        assertTrue(response.choices().isEmpty());
        assertEquals(0, data.playerReads.get(), "private target data must not be expanded before authorization");
    }

    @Test
    void roleOnlyDiscordUserCannotProbeAmbiguousMinecraftIdentities() {
        FakeReadData data = linkedData();
        data.resolution = new PlayerResolution.Ambiguous(List.of(
                identity(TARGET_PLAYER, "PrivateBedrockAlt", PlayerPlatform.BEDROCK),
                identity(UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff"), "PrivateBedrockAlt", PlayerPlatform.JAVA)
        ));
        StaffModerationController controller = controller(data, ignored -> Optional.empty());

        StaffModerationController.Response response = controller.moderateMinecraft(
                INVOKER_DISCORD_ID,
                "role-only-user",
                "PrivateBedrockAlt"
        );

        assertTrue(response.content().contains("not linked to a current Enthusia staff identity"));
        assertFalse(response.content().contains("Multiple Minecraft identities"));
        assertEquals(0, data.resolutionReads.get(), "target resolution must not occur before current staff authorization");
    }

    @Test
    void roleOnlyDiscordUserCannotProbeCaseExistence() {
        FakeReadData data = linkedData();
        StaffModerationController controller = controller(data, ignored -> Optional.empty());

        StaffModerationController.Response response = controller.caseView(
                INVOKER_DISCORD_ID,
                "role-only-user",
                "0123456789ABCDEF"
        );

        assertTrue(response.content().contains("not linked to a current Enthusia staff identity"));
        assertFalse(response.content().contains("No case exists"));
        assertEquals(0, data.caseReads.get(), "case existence must not be queried before current staff authorization");
    }

    @Test
    void authorizedLinkedHelperCanReadBedrockProfileAndGetsSignedPrivateControls() {
        FakeReadData data = linkedData();
        StaffModerationController controller = controller(
                data,
                playerId -> playerId.equals(INVOKER_PLAYER) ? Optional.of(StaffRank.HELPER) : Optional.empty()
        );

        StaffModerationController.Response response = controller.moderateDiscord(
                INVOKER_DISCORD_ID,
                "linked-helper",
                TARGET_DISCORD_ID
        );

        assertTrue(response.content().contains("PrivateBedrockAlt"));
        assertTrue(response.content().contains("BEDROCK"));
        assertFalse(response.content().contains("<@"), "read-only profiles must not mention/ping the target");
        assertEquals(5, response.buttons().size());
        assertTrue(response.buttons().stream().allMatch(button -> button.customId().length() <= 100));
        assertTrue(data.playerReads.get() > 0);
    }

    private static StaffModerationController controller(FakeReadData data, StaffAuthorityClient authority) {
        StaffModerationReadService reads = new StaffModerationReadService(data, CLOCK);
        LinkedStaffActorResolver actors = new LinkedStaffActorResolver(
                userId -> reads.discordTarget(userId).subject(),
                authority
        );
        SignedComponentCodec components = new SignedComponentCodec(
                CLOCK,
                Duration.ofMinutes(5),
                "controller-test-component-secret-0123456789",
                new SecureRandom(),
                new InteractionReplayGuard(64, Duration.ofMinutes(10))
        );
        return new StaffModerationController(reads, actors, new StaffReadAuthorization(), components);
    }

    private static FakeReadData linkedData() {
        DiscordUserId invokerDiscord = new DiscordUserId(Long.toString(INVOKER_DISCORD_ID));
        DiscordUserId targetDiscord = new DiscordUserId(Long.toString(TARGET_DISCORD_ID));
        VersionedSubject invoker = subject(invokerDiscord, INVOKER_PLAYER);
        VersionedSubject target = subject(targetDiscord, TARGET_PLAYER);

        FakeReadData data = new FakeReadData();
        data.discordSubjects.put(invokerDiscord, invoker);
        data.discordSubjects.put(targetDiscord, target);
        data.minecraftSubjects.put(INVOKER_PLAYER, invoker);
        data.minecraftSubjects.put(TARGET_PLAYER, target);
        data.players.put(INVOKER_PLAYER, identity(INVOKER_PLAYER, "InvokerJava", PlayerPlatform.JAVA));
        data.players.put(TARGET_PLAYER, identity(TARGET_PLAYER, "PrivateBedrockAlt", PlayerPlatform.BEDROCK));
        return data;
    }

    private static VersionedSubject subject(DiscordUserId discord, UUID playerId) {
        return new VersionedSubject(new ModerationSubject(
                new ModerationSubjectId(UUID.randomUUID()),
                Set.of(new DiscordIdentityRef(discord), new MinecraftIdentityRef(playerId)),
                Optional.of(new MainMinecraftAccount(playerId, MainAccountSelectionSource.AUTOMATIC))
        ), 1L);
    }

    private static PlayerIdentity identity(UUID playerId, String username, PlayerPlatform platform) {
        return new PlayerIdentity(playerId, Optional.of(username), platform, NOW.minusSeconds(3_600), NOW);
    }

    private static final class FakeReadData implements StaffModerationReadService.ReadData {
        private final Map<DiscordUserId, VersionedSubject> discordSubjects = new HashMap<>();
        private final Map<UUID, VersionedSubject> minecraftSubjects = new HashMap<>();
        private final Map<UUID, PlayerIdentity> players = new HashMap<>();
        private final AtomicInteger playerReads = new AtomicInteger();
        private final AtomicInteger resolutionReads = new AtomicInteger();
        private final AtomicInteger caseReads = new AtomicInteger();
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
        public PlayerResolution resolvePlayer(String uuidOrUsername) {
            resolutionReads.incrementAndGet();
            return resolution;
        }

        @Override
        public Optional<PlayerIdentity> player(UUID playerId) {
            playerReads.incrementAndGet();
            return Optional.ofNullable(players.get(playerId));
        }

        @Override
        public List<VersionedLink> linkHistoryForDiscord(DiscordUserId userId) {
            return List.of();
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
            caseReads.incrementAndGet();
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
}
