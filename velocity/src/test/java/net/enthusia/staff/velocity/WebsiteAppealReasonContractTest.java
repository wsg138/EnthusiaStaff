package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.Headers;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

final class WebsiteAppealReasonContractTest {
    private static final Instant NOW = Instant.parse("2026-08-05T20:00:00Z");
    private static final UUID APPEAL_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID SANCTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final UUID REVIEWER_ID = UUID.fromString("00000000-0000-0000-0000-000000000104");
    private static final CaseId CASE_ID = new CaseId("CASE000000000101");

    @Test
    void preservesTheExistingOneThousandCharacterReasonContract() {
        RecordingStore mutationStore = new RecordingStore();
        DefaultAuthorizationPolicy authorization = new DefaultAuthorizationPolicy();
        WebsiteAppealEndpoint endpoint = new WebsiteAppealEndpoint(
                new PreparedAppealStore(),
                authorization,
                new SanctionChangeService(authorization, mutationStore),
                () -> OperationalMode.ACTIVE,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new WebsiteApiRequestDecoder()
        );
        Headers headers = new Headers();
        headers.set("idempotency-key", "appeal-reason-contract");
        ObjectNode input = new ObjectMapper().createObjectNode();
        input.put("appealId", APPEAL_ID.toString());
        input.put("punishmentId", SANCTION_ID.toString());
        input.put("caseId", CASE_ID.value());
        input.put("playerAccountId", ACCOUNT_ID.toString());
        input.put("actorAccountId", REVIEWER_ID.toString());
        input.put("actorRank", "MOD");
        input.put("reason", "x".repeat(1_000));

        Map<?, ?> response = assertInstanceOf(Map.class, endpoint.accept(headers, input));

        assertEquals(1, response.get("affectedSanctions"));
        assertEquals(1_000, mutationStore.request.reason().length());
    }

    private static final class PreparedAppealStore extends WebsiteModerationStoreStub {
        @Override
        public AppealAcceptancePreparation prepareAppealAcceptance(
                UUID appealId,
                UUID punishmentId,
                CaseId caseId,
                String accountId,
                String idempotencyKey,
                Instant now
        ) {
            return new AppealAcceptancePreparation.Ready(false);
        }

        @Override
        public void completeAppealAcceptance(
                UUID appealId,
                String state,
                String outcomeCode,
                Instant now
        ) {
        }
    }

    private static final class RecordingStore implements SanctionMutationStore {
        private ExactSanctionChangeRequest request;

        @Override
        public SanctionChangeResult apply(SanctionChangeRequest request) {
            throw new AssertionError("case-wide mutation path was called");
        }

        @Override
        public boolean supportsExactChanges() {
            return true;
        }

        @Override
        public OptionalLong exactRevision(UUID sanctionId) {
            return OptionalLong.of(0L);
        }

        @Override
        public ExactSanctionChangeResult applyExact(
                ExactSanctionChangeRequest exactRequest,
                SanctionActionLimits limits
        ) {
            request = exactRequest;
            return new ExactSanctionChangeResult.Applied(
                    CASE_ID,
                    SANCTION_ID,
                    ACCOUNT_ID,
                    SanctionChangeAction.FULL_OVERTURN,
                    SanctionStatus.ACTIVE,
                    SanctionStatus.OVERTURNED,
                    Optional.empty(),
                    Optional.empty(),
                    NOW,
                    Optional.of(APPEAL_ID),
                    Optional.empty(),
                    false
            );
        }
    }
}
