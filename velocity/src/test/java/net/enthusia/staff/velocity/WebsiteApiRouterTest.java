package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.Headers;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.SanctionChangeService;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.website.PublicPunishment;
import net.enthusia.staff.domain.website.PublicPunishmentFilter;
import net.enthusia.staff.domain.website.PublicPunishmentPage;
import net.enthusia.staff.domain.website.PublicPunishmentState;
import net.enthusia.staff.domain.website.PunishmentCodeBinding;
import org.junit.jupiter.api.Test;

final class WebsiteApiRouterTest {
    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String PLAYER_NAME = "ExamplePlayer";
    private static final CaseId CASE_ID = new CaseId("0123456789ABCDEF");
    private static final UUID PUNISHMENT_ID =
            UUID.fromString("65c17015-160d-4e15-a589-495898782ba2");
    private static final UUID ACCOUNT_ID =
            UUID.fromString("d1791091-1832-4f69-bc14-25ef525f268d");

    @Test
    void routesPublicListWithBoundedFilterCursorAndLimit() {
        RecordingStore store = new RecordingStore();
        WebsiteApiRouter router = router(store);

        Object response = router.route(
                GET,
                URI.create("/v1/public/punishments?type=ban&cursor=next-page&limit=12"),
                new Headers(),
                new byte[0]
        );

        Map<?, ?> body = assertInstanceOf(Map.class, response);
        assertEquals(PublicPunishmentFilter.BAN, store.filter);
        assertEquals(Optional.of("next-page"), store.cursor);
        assertEquals(12, store.limit);
        assertEquals(NOW, store.now);
        assertEquals("last-page", body.get("nextCursor"));
        assertEquals(1, assertInstanceOf(List.class, body.get("items")).size());
    }

    @Test
    void routesExactCaseAndSearchWithoutExposingPrivateData() {
        RecordingStore store = new RecordingStore();
        WebsiteApiRouter router = router(store);

        Map<?, ?> exact = assertInstanceOf(Map.class, router.route(
                GET,
                URI.create("/v1/public/cases/0123456789ABCDEF"),
                new Headers(),
                new byte[0]
        ));
        Map<?, ?> search = assertInstanceOf(Map.class, router.route(
                GET,
                URI.create("/v1/public/search?q=" + PLAYER_NAME),
                new Headers(),
                new byte[0]
        ));

        assertEquals(CASE_ID, store.caseId);
        assertEquals(PLAYER_NAME, store.search);
        assertEquals(PLAYER_NAME, exact.get("player"));
        assertEquals(1, assertInstanceOf(List.class, search.get("items")).size());
        assertFalse(exact.containsKey("targetId"));
    }

    @Test
    void routesClaimAndRevalidationThroughStrictJsonContracts() {
        RecordingStore store = new RecordingStore();
        WebsiteApiRouter router = router(store);
        Headers headers = jsonHeaders();

        Map<?, ?> claim = assertInstanceOf(Map.class, router.route(
                POST,
                URI.create("/v1/website/punishment-codes/claim"),
                headers,
                bytes("{\"accountId\":\"" + ACCOUNT_ID
                        + "\",\"username\":\"" + PLAYER_NAME + "\",\"punishmentCode\":\"CODE-1\"}")
        ));
        Map<?, ?> revalidation = assertInstanceOf(Map.class, router.route(
                POST,
                URI.create("/v1/website/punishment-codes/revalidate"),
                headers,
                bytes("{\"accountId\":\"" + ACCOUNT_ID + "\",\"punishmentId\":\""
                        + PUNISHMENT_ID + "\",\"codeGeneration\":2}")
        ));

        assertEquals("CODE-1", store.code);
        assertEquals(PLAYER_NAME, store.username);
        assertEquals(PUNISHMENT_ID, store.punishmentId);
        assertEquals(2, store.generation);
        assertEquals(PLAYER_NAME, claim.get("boundUsername"));
        assertEquals("ELIGIBLE", revalidation.get("eligibilityState"));
    }

    @Test
    void rejectsMalformedOrUnsupportedRequestShapes() {
        RecordingStore store = new RecordingStore();
        WebsiteApiRouter router = router(store);

        assertApiError(
                "INVALID_QUERY",
                () -> router.route(
                        GET,
                        URI.create("/v1/public/search?q=one&q=two"),
                        new Headers(),
                        new byte[0]
                )
        );
        assertApiError(
                "UNKNOWN_FIELD",
                () -> router.route(
                        POST,
                        URI.create("/v1/website/punishment-codes/claim"),
                        jsonHeaders(),
                        bytes("{\"accountId\":\"" + ACCOUNT_ID
                                + "\",\"username\":\"" + PLAYER_NAME + "\","
                                + "\"punishmentCode\":\"CODE-1\",\"extra\":true}")
                )
        );
        assertApiError(
                "NOT_FOUND",
                () -> router.route("DELETE", URI.create("/v1/public/search?q=name"),
                        new Headers(), new byte[0])
        );
        assertEquals(0, store.mutations);
    }

    private static WebsiteApiRouter router(RecordingStore store) {
        AuthorizationPolicy authorization = (actor, action) -> true;
        return new WebsiteApiRouter(
                store,
                authorization,
                new SanctionChangeService(
                        authorization,
                        request -> new net.enthusia.staff.domain.sanction.SanctionChangeResult.Applied(1, false)
                ),
                () -> OperationalMode.ACTIVE,
                CLOCK
        );
    }

    private static Headers jsonHeaders() {
        Headers headers = new Headers();
        headers.set("content-type", "application/json; charset=utf-8");
        return headers;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void assertApiError(String code, org.junit.jupiter.api.function.Executable action) {
        WebsiteApiException error = assertThrows(WebsiteApiException.class, action);
        assertEquals(code, error.code());
    }

    private static PublicPunishment punishment() {
        return new PublicPunishment(
                PLAYER_NAME,
                "BAN",
                "Rule violation",
                "Public rule violation",
                NOW.minusSeconds(60),
                Optional.of(NOW.plusSeconds(300)),
                OptionalLong.of(300),
                PublicPunishmentState.ACTIVE,
                CASE_ID,
                true
        );
    }

    private static PunishmentCodeBinding binding(int generation) {
        return new PunishmentCodeBinding(
                PUNISHMENT_ID,
                CASE_ID,
                generation,
                "BAN",
                PLAYER_NAME,
                true,
                "ELIGIBLE"
        );
    }

    private static final class RecordingStore extends WebsiteModerationStoreStub {
        private PublicPunishmentFilter filter;
        private Optional<String> cursor;
        private int limit;
        private Instant now;
        private CaseId caseId;
        private String search;
        private String code;
        private String username;
        private UUID punishmentId;
        private int generation;
        private int mutations;

        @Override
        public PublicPunishmentPage listPublic(
                PublicPunishmentFilter requestedFilter,
                Optional<String> requestedCursor,
                int requestedLimit,
                Instant requestedNow
        ) {
            filter = requestedFilter;
            cursor = requestedCursor;
            limit = requestedLimit;
            now = requestedNow;
            return new PublicPunishmentPage(List.of(punishment()), Optional.of("last-page"));
        }

        @Override
        public List<PublicPunishment> searchPublic(String query, int requestedLimit, Instant requestedNow) {
            search = query;
            limit = requestedLimit;
            now = requestedNow;
            return List.of(punishment());
        }

        @Override
        public Optional<PublicPunishment> publicCase(CaseId requestedCaseId, Instant requestedNow) {
            caseId = requestedCaseId;
            now = requestedNow;
            return Optional.of(punishment());
        }

        @Override
        public PunishmentCodeBinding claimCode(
                String requestedCode,
                String accountId,
                String requestedUsername,
                Instant requestedNow
        ) {
            mutations++;
            code = requestedCode;
            username = requestedUsername;
            now = requestedNow;
            return binding(1);
        }

        @Override
        public PunishmentCodeBinding revalidateCode(
                UUID requestedPunishmentId,
                int requestedGeneration,
                String accountId,
                Instant requestedNow
        ) {
            mutations++;
            punishmentId = requestedPunishmentId;
            generation = requestedGeneration;
            now = requestedNow;
            return binding(requestedGeneration);
        }
    }
}
