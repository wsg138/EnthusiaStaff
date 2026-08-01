package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.Headers;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.SanctionChangeService;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.sanction.SanctionChangeRequest;
import net.enthusia.staff.domain.sanction.SanctionChangeResult;
import net.enthusia.staff.domain.website.AppealAcceptancePreparation;
import org.junit.jupiter.api.Test;

final class WebsiteAppealEndpointTest {
    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID APPEAL_ID =
            UUID.fromString("76366ccf-6887-448d-90a3-a8a32a042fd4");
    private static final UUID PUNISHMENT_ID =
            UUID.fromString("d01be102-0274-4fc9-8f60-dbc49e082d85");
    private static final UUID PLAYER_ACCOUNT_ID =
            UUID.fromString("38fc4e68-c443-482a-9257-16a75518810a");
    private static final UUID REVIEWER_ID =
            UUID.fromString("744cc693-a8a5-4ec9-8bb0-8074c951ff49");
    private static final CaseId CASE_ID = new CaseId("0123456789ABCDEF");

    @Test
    void appliesAuthorizedAppealAndCompletesDurablePreparation() {
        AppealStore store = new AppealStore();
        RecordingMutationStore mutations = new RecordingMutationStore(
                new SanctionChangeResult.Applied(2, false)
        );
        WebsiteAppealEndpoint endpoint = endpoint(store, mutations, OperationalMode.ACTIVE);

        Map<?, ?> response = assertInstanceOf(Map.class, endpoint.accept(headers(), input("MOD")));

        assertEquals(1, store.preparations);
        assertEquals(APPEAL_ID, store.completedAppealId);
        assertEquals("APPLIED", store.completedState);
        assertEquals("APPLIED", store.completedOutcome);
        assertEquals(2, response.get("affectedSanctions"));
        assertTrue(assertInstanceOf(Boolean.class, response.get("applied")));
        assertFalse(assertInstanceOf(Boolean.class, response.get("replayed")));
        assertEquals(CASE_ID, mutations.request.caseId());
        assertTrue(mutations.request.idempotencyKey().value().startsWith("website-appeal:"));
    }

    @Test
    void rejectsReadOnlyReviewerBeforePreparingMutation() {
        AppealStore store = new AppealStore();
        RecordingMutationStore mutations = new RecordingMutationStore(
                new SanctionChangeResult.Applied(1, false)
        );
        WebsiteAppealEndpoint endpoint = endpoint(store, mutations, OperationalMode.ACTIVE);

        WebsiteApiException error = assertThrows(
                WebsiteApiException.class,
                () -> endpoint.accept(headers(), input("DEVELOPER"))
        );

        assertEquals(403, error.status());
        assertEquals("APPEAL_MUTATION_FORBIDDEN", error.code());
        assertEquals(0, store.preparations);
        assertNull(mutations.request);
    }

    @Test
    void leavesPreparedRequestReplayableWhenAuthorityModeIsBlocked() {
        AppealStore store = new AppealStore();
        RecordingMutationStore mutations = new RecordingMutationStore(
                new SanctionChangeResult.Applied(1, false)
        );
        WebsiteAppealEndpoint endpoint = endpoint(
                store,
                mutations,
                OperationalMode.SHADOW_MIGRATION
        );

        WebsiteApiException error = assertThrows(
                WebsiteApiException.class,
                () -> endpoint.accept(headers(), input("MOD"))
        );

        assertEquals(503, error.status());
        assertEquals("AUTHORITY_NOT_ACTIVE", error.code());
        assertEquals(1, store.preparations);
        assertNull(store.completedState);
        assertNull(mutations.request);
    }

    @Test
    void persistsNonModeMutationRejectionForDeterministicReplay() {
        AppealStore store = new AppealStore();
        RecordingMutationStore mutations = new RecordingMutationStore(
                new SanctionChangeResult.Rejected("STALE_STATE", "The sanction changed")
        );
        WebsiteAppealEndpoint endpoint = endpoint(store, mutations, OperationalMode.ACTIVE);

        WebsiteApiException error = assertThrows(
                WebsiteApiException.class,
                () -> endpoint.accept(headers(), input("ADMIN"))
        );

        assertEquals(409, error.status());
        assertEquals("STALE_STATE", error.code());
        assertEquals("REJECTED", store.completedState);
        assertEquals("STALE_STATE", store.completedOutcome);
    }

    @Test
    void returnsDurablePreparationRejectionWithoutCallingMutationService() {
        AppealStore store = new AppealStore();
        store.preparation = new AppealAcceptancePreparation.Rejected(
                "PUNISHMENT_NOT_FOUND",
                "The punishment could not be found"
        );
        RecordingMutationStore mutations = new RecordingMutationStore(
                new SanctionChangeResult.Applied(1, false)
        );
        WebsiteAppealEndpoint endpoint = endpoint(store, mutations, OperationalMode.ACTIVE);

        WebsiteApiException error = assertThrows(
                WebsiteApiException.class,
                () -> endpoint.accept(headers(), input("MOD"))
        );

        assertEquals(404, error.status());
        assertEquals("PUNISHMENT_NOT_FOUND", error.code());
        assertNull(mutations.request);
        assertNull(store.completedState);
    }

    private static WebsiteAppealEndpoint endpoint(
            AppealStore store,
            RecordingMutationStore mutations,
            OperationalMode mode
    ) {
        DefaultAuthorizationPolicy authorization = new DefaultAuthorizationPolicy();
        return new WebsiteAppealEndpoint(
                store,
                authorization,
                new SanctionChangeService(authorization, mutations::apply),
                () -> mode,
                CLOCK,
                new WebsiteApiRequestDecoder()
        );
    }

    private static Headers headers() {
        Headers headers = new Headers();
        headers.set("idempotency-key", "appeal-request-001");
        return headers;
    }

    private static ObjectNode input(String rank) {
        ObjectNode input = new ObjectMapper().createObjectNode();
        input.put("appealId", APPEAL_ID.toString());
        input.put("punishmentId", PUNISHMENT_ID.toString());
        input.put("caseId", CASE_ID.value());
        input.put("playerAccountId", PLAYER_ACCOUNT_ID.toString());
        input.put("actorAccountId", REVIEWER_ID.toString());
        input.put("actorRank", rank);
        input.put("reason", "The evidence supports accepting this appeal.");
        return input;
    }

    private static final class AppealStore extends WebsiteModerationStoreStub {
        private AppealAcceptancePreparation preparation =
                new AppealAcceptancePreparation.Ready(false);
        private int preparations;
        private UUID completedAppealId;
        private String completedState;
        private String completedOutcome;

        @Override
        public AppealAcceptancePreparation prepareAppealAcceptance(
                UUID appealId,
                UUID punishmentId,
                CaseId caseId,
                String accountId,
                String idempotencyKey,
                Instant now
        ) {
            preparations++;
            return preparation;
        }

        @Override
        public void completeAppealAcceptance(
                UUID appealId,
                String state,
                String outcomeCode,
                Instant now
        ) {
            completedAppealId = appealId;
            completedState = state;
            completedOutcome = outcomeCode;
        }
    }

    private static final class RecordingMutationStore {
        private final SanctionChangeResult result;
        private SanctionChangeRequest request;

        private RecordingMutationStore(SanctionChangeResult result) {
            this.result = result;
        }

        private SanctionChangeResult apply(SanctionChangeRequest requested) {
            request = requested;
            return result;
        }
    }
}
