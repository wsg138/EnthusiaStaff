package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.Headers;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.SanctionChangeService;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.website.WebsiteAppealMutation;
import net.enthusia.staff.domain.website.WebsiteAppealView;
import org.junit.jupiter.api.Test;

final class WebsiteAppealLifecycleRouterTest {
    private static final Instant NOW = Instant.parse("2026-09-28T20:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID APPEAL_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PUNISHMENT_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ACCOUNT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID REVIEWER_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID ADMIN_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID DEVELOPER_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final CaseId CASE_ID = new CaseId("0123456789ABCDEF");

    @Test
    void routesEditClaimAndReopenUsingCurrentAuthorityRatherThanClaimedRank() {
        RecordingStore store = new RecordingStore();
        WebsiteApiRouter router = router(store);

        Map<?, ?> edit = response(router, "/v1/website/appeals/" + APPEAL_ID + "/edit", """
                {"accountId":"%s","expectedVersion":3,"reason":"Updated appeal reason for review.","idempotencyKey":"edit-request-1"}
                """.formatted(ACCOUNT_ID));
        assertEquals("edit", store.operation);
        assertEquals(ACCOUNT_ID.toString(), store.accountId);
        assertFalse((Boolean) edit.get("claimed"));

        Map<?, ?> claim = response(router, "/v1/website/appeals/reviewer/" + APPEAL_ID + "/claim", """
                {"actorAccountId":"%s","actorRank":"FOUNDER","expectedVersion":3,"idempotencyKey":"claim-request-1"}
                """.formatted(REVIEWER_ID));
        assertEquals("claim", store.operation);
        assertEquals(REVIEWER_ID, store.reviewerId);
        assertEquals("MOD", store.reviewerRank);
        assertTrue((Boolean) claim.get("claimed"));

        Map<?, ?> reopen = response(router, "/v1/website/appeals/reviewer/" + APPEAL_ID + "/reopen", """
                {"actorAccountId":"%s","actorRank":"MOD","expectedVersion":3,"note":"Senior review found new evidence.","idempotencyKey":"reopen-request-1"}
                """.formatted(ADMIN_ID));
        assertEquals("reopen", store.operation);
        assertEquals(ADMIN_ID, store.reviewerId);
        assertEquals("ADMIN", store.reviewerRank);
        assertFalse((Boolean) reopen.get("claimed"));
    }

    @Test
    void currentModeratorCannotReopenAndUnknownFieldsAreRejectedBeforeMutation() {
        RecordingStore store = new RecordingStore();
        WebsiteApiRouter router = router(store);

        WebsiteApiException forbidden = assertThrows(WebsiteApiException.class, () -> response(
                router,
                "/v1/website/appeals/reviewer/" + APPEAL_ID + "/reopen",
                """
                {"actorAccountId":"%s","actorRank":"ADMIN","expectedVersion":3,"note":"Moderator reopen attempt.","idempotencyKey":"reopen-request-2"}
                """.formatted(REVIEWER_ID)
        ));
        assertEquals("APPEAL_REOPEN_FORBIDDEN", forbidden.code());
        assertEquals(0, store.mutations);

        WebsiteApiException unknown = assertThrows(WebsiteApiException.class, () -> response(
                router,
                "/v1/website/appeals/" + APPEAL_ID + "/edit",
                """
                {"accountId":"%s","expectedVersion":3,"reason":"Updated appeal reason for review.","idempotencyKey":"edit-request-2","actorRank":"ADMIN"}
                """.formatted(ACCOUNT_ID)
        ));
        assertEquals("UNKNOWN_FIELD", unknown.code());
        assertEquals(0, store.mutations);
    }

    @Test
    void currentDeveloperCannotClaimAndMalformedAppealIdsAreRejected() {
        RecordingStore store = new RecordingStore();
        WebsiteApiRouter router = router(store);

        WebsiteApiException forbidden = assertThrows(WebsiteApiException.class, () -> response(
                router,
                "/v1/website/appeals/reviewer/" + APPEAL_ID + "/claim",
                """
                {"actorAccountId":"%s","actorRank":"MOD","expectedVersion":3,"idempotencyKey":"claim-request-2"}
                """.formatted(DEVELOPER_ID)
        ));
        assertEquals("APPEAL_REVIEW_FORBIDDEN", forbidden.code());

        WebsiteApiException malformed = assertThrows(WebsiteApiException.class, () -> response(
                router,
                "/v1/website/appeals/not-a-uuid/edit",
                """
                {"accountId":"%s","expectedVersion":3,"reason":"Updated appeal reason for review.","idempotencyKey":"edit-request-3"}
                """.formatted(ACCOUNT_ID)
        ));
        assertEquals("INVALID_APPEAL_ID", malformed.code());
        assertEquals(0, store.mutations);
    }

    private static WebsiteApiRouter router(RecordingStore store) {
        AuthorizationPolicy authorization = (actor, action) -> actor.rank() != StaffRank.DEVELOPER;
        WebsiteReviewerAuthority reviewerAuthority = new WebsiteReviewerAuthority(playerId -> {
            if (ADMIN_ID.equals(playerId)) return Optional.of(StaffRank.ADMIN);
            if (DEVELOPER_ID.equals(playerId)) return Optional.of(StaffRank.DEVELOPER);
            if (REVIEWER_ID.equals(playerId)) return Optional.of(StaffRank.MOD);
            return Optional.empty();
        });
        return new WebsiteApiRouter(
                store,
                authorization,
                new SanctionChangeService(
                        authorization,
                        request -> new net.enthusia.staff.domain.sanction.SanctionChangeResult.Applied(1, false)
                ),
                () -> OperationalMode.ACTIVE,
                CLOCK,
                reviewerAuthority
        );
    }

    private static Map<?, ?> response(WebsiteApiRouter router, String path, String body) {
        Object value = router.route(
                "POST",
                URI.create(path),
                jsonHeaders(),
                body.getBytes(StandardCharsets.UTF_8)
        );
        return assertInstanceOf(Map.class, value);
    }

    private static Headers jsonHeaders() {
        Headers headers = new Headers();
        headers.set("content-type", "application/json; charset=utf-8");
        return headers;
    }

    private static WebsiteAppealMutation mutation(boolean claimed) {
        WebsiteAppealView appeal = new WebsiteAppealView(
                APPEAL_ID,
                PUNISHMENT_ID,
                CASE_ID,
                "BAN",
                "AppealPlayer",
                "Updated appeal reason for review.",
                "OPEN",
                4,
                null,
                null,
                NOW.minusSeconds(60),
                NOW
        );
        return new WebsiteAppealMutation(appeal, false, claimed);
    }

    private static final class RecordingStore extends WebsiteModerationStoreStub {
        private String operation;
        private String accountId;
        private UUID reviewerId;
        private String reviewerRank;
        private int mutations;

        @Override
        public WebsiteAppealMutation editAppeal(
                UUID appealId,
                long expectedVersion,
                String requestedAccountId,
                String reason,
                String idempotencyKey,
                Instant now
        ) {
            mutations++;
            operation = "edit";
            accountId = requestedAccountId;
            assertEquals(APPEAL_ID, appealId);
            assertEquals(3, expectedVersion);
            return mutation(false);
        }

        @Override
        public WebsiteAppealMutation claimAppeal(
                UUID appealId,
                long expectedVersion,
                UUID requestedReviewerId,
                String requestedReviewerRank,
                String idempotencyKey,
                Instant now
        ) {
            mutations++;
            operation = "claim";
            reviewerId = requestedReviewerId;
            reviewerRank = requestedReviewerRank;
            assertEquals(APPEAL_ID, appealId);
            assertEquals(3, expectedVersion);
            return mutation(true);
        }

        @Override
        public WebsiteAppealMutation reopenAppeal(
                UUID appealId,
                long expectedVersion,
                UUID requestedReviewerId,
                String requestedReviewerRank,
                String note,
                String idempotencyKey,
                Instant now
        ) {
            mutations++;
            operation = "reopen";
            reviewerId = requestedReviewerId;
            reviewerRank = requestedReviewerRank;
            assertEquals(APPEAL_ID, appealId);
            assertEquals(3, expectedVersion);
            return mutation(false);
        }
    }
}
