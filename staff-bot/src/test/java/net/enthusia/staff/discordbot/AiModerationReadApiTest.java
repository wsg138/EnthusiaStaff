package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
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
import net.enthusia.staff.domain.casefile.CaseState;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.history.HistoryQueryOptions;
import net.enthusia.staff.domain.history.ModerationHistoryPage;
import net.enthusia.staff.domain.moderation.MinecraftIdentityRef;
import net.enthusia.staff.domain.moderation.ModerationSubject;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.player.PlayerResolution;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedSubject;
import net.enthusia.staff.domain.ports.StaffNoteStore.StaffNote;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class AiModerationReadApiTest {
    private static final Instant NOW = Instant.parse("2026-10-06T20:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID PLAYER_ID =
            UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String TOKEN =
            "0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void authenticatorRequiresExactBearerToken() {
        AiModerationReadAuthenticator authenticator =
                new AiModerationReadAuthenticator(TOKEN);

        assertTrue(authenticator.accepts("Bearer " + TOKEN));
        assertFalse(authenticator.accepts(TOKEN));
        assertFalse(authenticator.accepts("Bearer wrong"));
        assertFalse(authenticator.accepts(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AiModerationReadAuthenticator("too-short")
        );
    }

    @Test
    void endpointUsesFixedLoopbackPortAndStrictTargetRequest() throws Exception {
        assertEquals(
                "127.0.0.1",
                AiModerationReadApiServer.bindAddress().getAddress().getHostAddress()
        );
        assertEquals(8767, AiModerationReadApiServer.bindAddress().getPort());

        var json = ModerationReadApiServer.jsonMapper();
        assertEquals(
                "Valid_Name",
                AiModerationReadApiServer.parseRequest(
                        json,
                        "{"target":"Valid_Name"}".getBytes(StandardCharsets.UTF_8)
                ).target()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> AiModerationReadApiServer.parseRequest(
                        json,
                        "{"target":"../bad"}".getBytes(StandardCharsets.UTF_8)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> AiModerationReadApiServer.parseRequest(
                        json,
                        "{"target":"Valid_Name","mutation":"ban"}".getBytes(StandardCharsets.UTF_8)
                )
        );
    }

    @Test
    void projectionIncludesCurrentStateButNeverPrivateCaseFields() throws Exception {
        FakeReadData data = new FakeReadData();
        PlayerIdentity identity = new PlayerIdentity(
                PLAYER_ID,
                Optional.of("Bad_Player"),
                PlayerPlatform.JAVA,
                NOW.minusSeconds(3600),
                NOW
        );
        data.resolution = new PlayerResolution.Resolved(
                identity,
                PlayerResolution.MatchKind.CURRENT_USERNAME
        );
        data.subject = Optional.of(new VersionedSubject(
                new ModerationSubject(
                        new ModerationSubjectId(UUID.randomUUID()),
                        Set.of(new MinecraftIdentityRef(PLAYER_ID)),
                        Optional.empty()
                ),
                4L
        ));
        data.identity = Optional.of(identity);
        data.activeSanctions = List.of(new ActiveSanction(
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                new CaseId("0123456789ABCDEF"),
                PLAYER_ID,
                SanctionType.MUTE,
                "Public mute reason",
                NOW.minusSeconds(120),
                Optional.of(NOW.plusSeconds(3600)),
                Optional.empty()
        ));
        data.recentCases = List.of(caseReview());

        StaffModerationReadService reads = new StaffModerationReadService(data, CLOCK);
        AiModerationReadApiService service = new AiModerationReadApiService(reads, CLOCK);
        AiModerationReadApiModel.Response response =
                service.read(new AiModerationReadApiModel.Request("Bad_Player"));

        assertEquals("enthusia-staff", response.service());
        assertEquals("ai-moderation-state", response.api());
        assertEquals("v1", response.contractVersion());
        assertEquals(PLAYER_ID.toString(), response.target().playerId());
        assertEquals("chat.harassment", response.recentCases().getFirst().exactReasonId());
        assertEquals("MUTE", response.activeSanctions().getFirst().type());

        String serialized = ModerationReadApiServer.jsonMapper().writeValueAsString(response);
        assertFalse(serialized.contains("Internal investigation detail"));
        assertFalse(serialized.contains("ModeratorSecret"));
        assertFalse(serialized.contains("ADMIN"));
        assertTrue(serialized.contains("chat.harassment"));
        assertTrue(serialized.contains("Public mute reason"));
    }

    @Test
    void missingAndAmbiguousTargetsFailClosed() {
        FakeReadData data = new FakeReadData();
        StaffModerationReadService reads = new StaffModerationReadService(data, CLOCK);
        AiModerationReadApiService service = new AiModerationReadApiService(reads, CLOCK);

        assertThrows(
                AiModerationReadApiService.MissingTargetException.class,
                () -> service.read(new AiModerationReadApiModel.Request("Missing"))
        );

        PlayerIdentity first = identity(UUID.randomUUID(), "SharedName");
        PlayerIdentity second = identity(UUID.randomUUID(), "SharedName");
        data.resolution = new PlayerResolution.Ambiguous(List.of(first, second));
        assertThrows(
                AiModerationReadApiService.AmbiguousTargetException.class,
                () -> service.read(new AiModerationReadApiModel.Request("SharedName"))
        );
    }

    private static PlayerIdentity identity(UUID id, String name) {
        return new PlayerIdentity(
                id,
                Optional.of(name),
                PlayerPlatform.JAVA,
                NOW.minusSeconds(60),
                NOW
        );
    }

    private static CaseReview caseReview() {
        return new CaseReview(
                new CaseId("0123456789ABCDEF"),
                PLAYER_ID,
                UUID.fromString("99999999-8888-7777-6666-555555555555"),
                "ModeratorSecret",
                "ADMIN",
                "Public case reason",
                "chat.harassment",
                "MUTE",
                "Internal investigation detail",
                "rules-2026.10",
                CaseVisibility.PUBLIC,
                CaseState.OPEN,
                NOW.minusSeconds(120),
                1L,
                Optional.empty(),
                List.of(),
                Optional.empty()
        );
    }

    private static final class FakeReadData implements StaffModerationReadService.ReadData {
        private PlayerResolution resolution = new PlayerResolution.Missing();
        private Optional<VersionedSubject> subject = Optional.empty();
        private Optional<PlayerIdentity> identity = Optional.empty();
        private List<ActiveSanction> activeSanctions = List.of();
        private List<CaseReview> recentCases = List.of();

        @Override
        public Optional<VersionedSubject> subjectForDiscord(
                net.enthusia.staff.domain.moderation.DiscordUserId userId
        ) {
            return Optional.empty();
        }

        @Override
        public Optional<VersionedSubject> subjectForMinecraft(UUID playerId) {
            return subject;
        }

        @Override
        public PlayerResolution resolvePlayer(String uuidOrUsername) {
            return resolution;
        }

        @Override
        public Optional<PlayerIdentity> player(UUID playerId) {
            return identity;
        }

        @Override
        public long linkHistoryCountForDiscord(
                net.enthusia.staff.domain.moderation.DiscordUserId userId
        ) {
            return 0L;
        }

        @Override
        public ModerationHistoryPage historyPage(
                UUID targetId,
                int page,
                int pageSize,
                HistoryQueryOptions options
        ) {
            return new ModerationHistoryPage(
                    targetId,
                    page,
                    pageSize,
                    0,
                    0,
                    List.of()
            );
        }

        @Override
        public List<CaseReview> recentCases(UUID targetId, int limit) {
            return recentCases;
        }

        @Override
        public Optional<CaseReview> caseReview(CaseId caseId) {
            return Optional.empty();
        }

        @Override
        public List<ActiveSanction> activeSanctions(
                UUID targetId,
                Instant now
        ) {
            return activeSanctions;
        }

        @Override
        public List<StaffNote> recentNotes(UUID targetId, int limit) {
            return List.of();
        }
    }
}
