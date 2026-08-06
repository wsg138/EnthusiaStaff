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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.SanctionChangeService;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.ports.SanctionMutationStore;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeRequest;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeResult;
import net.enthusia.staff.domain.sanction.SanctionActionLimits;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;
import net.enthusia.staff.domain.sanction.SanctionChangeRequest;
import net.enthusia.staff.domain.sanction.SanctionChangeResult;
import net.enthusia.staff.domain.sanction.SanctionStatus;
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
    private static final long REVISION = 7L;
    private static final String PENDING = "APPLIED:MUTATION_PENDING_R7";
    private static final String STALE_SANCTION_STATE = "STALE_SANCTION_STATE";

    @Test
    void appliesAuthorizedAppealToOnlyTheExactPunishment() {
        AppealStore store = new AppealStore();
        RecordingMutationStore mutations = new RecordingMutationStore(applied(false));
        WebsiteAppealEndpoint endpoint = endpoint(store, mutations, OperationalMode.ACTIVE);

        Map<?, ?> response = assertInstanceOf(Map.class, endpoint.accept(headers(), input("MOD")));

        assertEquals(1, store.preparations);
        assertEquals(List.of(PENDING, "APPLIED:APPLIED"), store.completions);
        assertEquals(1, response.get("affectedSanctions"));
        assertTrue(assertInstanceOf(Boolean.class, response.get("applied")));
        assertFalse(assertInstanceOf(Boolean.class, response.get("replayed")));
        assertEquals(PUNISHMENT_ID, mutations.request.sanctionId());
        assertEquals(REVISION, mutations.request.expectedRevision());
        assertEquals(SanctionChangeAction.FULL_OVERTURN, mutations.request.action());
        assertEquals(APPEAL_ID, mutations.request.linkedAppealId().orElseThrow());
        assertTrue(mutations.request.idempotencyKey().value().startsWith("website-appeal:"));
        assertEquals("VELOCITY_WEBSITE", mutations.request.originRuntime());
    }

    @Test
    void repeatedAcceptanceReturnsReplayWithoutChangingTheDurableOutcome() {
        AppealStore store = new AppealStore();
        store.preparation = new AppealAcceptancePreparation.Ready(true);
        RecordingMutationStore mutations = new RecordingMutationStore(applied(true));
        WebsiteAppealEndpoint endpoint = endpoint(store, mutations, OperationalMode.ACTIVE);

        Map<?, ?> response = assertInstanceOf(Map.class, endpoint.accept(headers(), input("ADMIN")));

        assertTrue(assertInstanceOf(Boolean.class, response.get("replayed")));
        assertEquals(List.of(PENDING, "APPLIED:APPLIED"), store.completions);
    }

    @Test
    void pendingRetryUsesPersistedRevisionInsteadOfNewLiveRevision() {
        AppealStore store = new AppealStore();
        store.preparation = new AppealAcceptancePreparation.Ready(
                true,
                OptionalLong.of(REVISION)
        );
        RecordingMutationStore mutations = new RecordingMutationStore(
                new ExactSanctionChangeResult.Rejected(
                        STALE_SANCTION_STATE,
                        "The sanction changed after acceptance began"
                )
        );
        mutations.revision = OptionalLong.of(REVISION + 4);
        WebsiteAppealEndpoint endpoint = endpoint(store, mutations, OperationalMode.ACTIVE);

        WebsiteApiException error = assertThrows(
                WebsiteApiException.class,
                () -> endpoint.accept(headers(), input("MOD"))
        );

        assertEquals(STALE_SANCTION_STATE, error.code());
        assertEquals(REVISION, mutations.request.expectedRevision());
        assertEquals(List.of(PENDING, "REJECTED:" + STALE_SANCTION_STATE), store.completions);
    }

    @Test
    void rejectsReadOnlyReviewerBeforePreparingMutation() {
        AppealStore store = new AppealStore();
        RecordingMutationStore mutations = new RecordingMutationStore(applied(false));
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
        RecordingMutationStore mutations = new RecordingMutationStore(applied(false));
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
        assertTrue(store.completions.isEmpty());
        assertNull(mutations.request);
    }

    @Test
    void persistsStaleExactDecisionForDeterministicReplay() {
        AppealStore store = new AppealStore();
        RecordingMutationStore mutations = new RecordingMutationStore(
                new ExactSanctionChangeResult.Rejected(
                        STALE_SANCTION_STATE,
                        "The sanction changed"
                )
        );
        WebsiteAppealEndpoint endpoint = endpoint(store, mutations, OperationalMode.ACTIVE);

        WebsiteApiException error = assertThrows(
                WebsiteApiException.class,
                () -> endpoint.accept(headers(), input("ADMIN"))
        );

        assertEquals(409, error.status());
        assertEquals(STALE_SANCTION_STATE, error.code());
        assertEquals(
                List.of(PENDING, "REJECTED:" + STALE_SANCTION_STATE),
                store.completions
        );
    }

    @Test
    void returnsDurablePreparationRejectionWithoutCallingMutationService() {
        AppealStore store = new AppealStore();
        store.preparation = new AppealAcceptancePreparation.Rejected(
                "PUNISHMENT_NOT_FOUND",
                "The punishment could not be found"
        );
        RecordingMutationStore mutations = new RecordingMutationStore(applied(false));
        WebsiteAppealEndpoint endpoint = endpoint(store, mutations, OperationalMode.ACTIVE);

        WebsiteApiException error = assertThrows(
                WebsiteApiException.class,
                () -> endpoint.accept(headers(), input("MOD"))
        );

        assertEquals(404, error.status());
        assertEquals("PUNISHMENT_NOT_FOUND", error.code());
        assertNull(mutations.request);
        assertTrue(store.completions.isEmpty());
    }

    @Test
    void missingExactPunishmentIsRejectedBeforeAppealPreparation() {
        AppealStore store = new AppealStore();
        RecordingMutationStore mutations = new RecordingMutationStore(applied(false));
        mutations.revision = OptionalLong.empty();
        WebsiteAppealEndpoint endpoint = endpoint(store, mutations, OperationalMode.ACTIVE);

        WebsiteApiException error = assertThrows(
                WebsiteApiException.class,
                () -> endpoint.accept(headers(), input("MOD"))
        );

        assertEquals(404, error.status());
        assertEquals("PUNISHMENT_NOT_FOUND", error.code());
        assertEquals(0, store.preparations);
        assertNull(mutations.request);
    }

    @Test
    void exactMutationCapabilityIsRequiredFailClosed() {
        AppealStore store = new AppealStore();
        RecordingMutationStore mutations = new RecordingMutationStore(applied(false));
        mutations.exactSupported = false;
        WebsiteAppealEndpoint endpoint = endpoint(store, mutations, OperationalMode.ACTIVE);

        WebsiteApiException error = assertThrows(
                WebsiteApiException.class,
                () -> endpoint.accept(headers(), input("FOUNDER"))
        );

        assertEquals(503, error.status());
        assertEquals("EXACT_MUTATION_UNAVAILABLE", error.code());
        assertEquals(0, store.preparations);
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
                new SanctionChangeService(authorization, mutations),
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

    private static ExactSanctionChangeResult.Applied applied(boolean replayed) {
        return new ExactSanctionChangeResult.Applied(
                CASE_ID,
                PUNISHMENT_ID,
                PLAYER_ACCOUNT_ID,
                SanctionChangeAction.FULL_OVERTURN,
                SanctionStatus.ACTIVE,
                SanctionStatus.OVERTURNED,
                Optional.of(NOW.plusSeconds(3_600)),
                Optional.of(NOW.plusSeconds(3_600)),
                NOW,
                Optional.of(APPEAL_ID),
                Optional.empty(),
                replayed
        );
    }

    private static final class AppealStore extends WebsiteModerationStoreStub {
        private AppealAcceptancePreparation preparation =
                new AppealAcceptancePreparation.Ready(false);
        private int preparations;
        private final List<String> completions = new ArrayList<>();

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
            completions.add(state + ":" + outcomeCode);
        }
    }

    private static final class RecordingMutationStore implements SanctionMutationStore {
        private final ExactSanctionChangeResult result;
        private boolean exactSupported = true;
        private OptionalLong revision = OptionalLong.of(REVISION);
        private ExactSanctionChangeRequest request;

        private RecordingMutationStore(ExactSanctionChangeResult result) {
            this.result = result;
        }

        @Override
        public SanctionChangeResult apply(SanctionChangeRequest requested) {
            throw new AssertionError("case-wide mutation path was called");
        }

        @Override
        public boolean supportsExactChanges() {
            return exactSupported;
        }

        @Override
        public OptionalLong exactRevision(UUID sanctionId) {
            assertEquals(PUNISHMENT_ID, sanctionId);
            return revision;
        }

        @Override
        public ExactSanctionChangeResult applyExact(
                ExactSanctionChangeRequest requested,
                SanctionActionLimits limits
        ) {
            request = requested;
            return result;
        }
    }
}
