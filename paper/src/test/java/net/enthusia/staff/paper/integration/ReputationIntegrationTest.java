package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import org.enthusia.rep.api.ReputationBlacklist;
import org.enthusia.rep.api.ReputationModerationApi;
import org.enthusia.rep.api.ReputationMutationResult;
import org.enthusia.rep.api.ReputationStateSnapshot;
import org.junit.jupiter.api.Test;

class ReputationIntegrationTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000041");
    private static final Actor MOD = new Actor(
            UUID.fromString("00000000-0000-0000-0000-000000000042"), "Moderator", StaffRank.MOD);
    private static final Instant NOW = Instant.parse("2026-08-24T02:00:00Z");
    private static final String CHECKSUM = "a".repeat(64);
    private static final ReputationStateSnapshot SNAPSHOT =
            new ReputationStateSnapshot(PLAYER, 12, List.of(), CHECKSUM);

    @Test
    void authorizedApplyUsesExactObservedRevisionAndReputationChecksum() {
        RecordingApi api = new RecordingApi();
        api.current = Optional.of(new ReputationBlacklist(
                PLAYER,
                NOW.minusSeconds(60),
                Optional.empty(),
                "CASE-OLD",
                "CASE-OLD",
                ReputationBlacklist.Status.ACTIVE,
                7L,
                NOW.minusSeconds(60)
        ));
        ReputationIntegration integration = available(api, true);
        Instant expiresAt = NOW.plusSeconds(3600);

        ReputationBlacklist applied = integration.apply(
                MOD, PLAYER, Optional.of(expiresAt), "CASE-NEW");

        assertEquals(8L, applied.revision());
        assertEquals(7L, api.expectedRevision);
        assertEquals(CHECKSUM, api.expectedChecksum);
        assertEquals(Optional.of(expiresAt), api.requestedExpiration);
        assertEquals("CASE-NEW", api.requestedCase);
        assertNotNull(api.operationId);
    }

    @Test
    void unauthorizedMutationIsRejectedBeforeProviderCall() {
        RecordingApi api = new RecordingApi();
        ReputationIntegration integration = available(api, false);

        assertThrows(SecurityException.class,
                () -> integration.apply(MOD, PLAYER, Optional.empty(), "CASE-X"));
        assertEquals(0, api.applyCalls);
        assertEquals(0, api.removeCalls);
    }

    @Test
    void missingProviderFailsSafe() {
        ReputationIntegration integration = new ReputationIntegration(
                IntegrationAvailability.INCOMPATIBLE,
                "provider mismatch",
                (actor, action) -> true,
                null
        );

        assertThrows(IllegalStateException.class,
                () -> integration.snapshot(PLAYER));
        assertEquals(IntegrationAvailability.INCOMPATIBLE, integration.availability());
    }

    @Test
    void reconciliationPreservesProviderStaleStateResult() {
        RecordingApi api = new RecordingApi();
        api.nextStatus = ReputationMutationResult.Status.STALE_BLACKLIST;
        ReputationIntegration integration = available(api, true);
        UUID operation = UUID.fromString("00000000-0000-0000-0000-000000000043");

        ReputationMutationResult result = integration.reconcileApply(
                operation, PLAYER, Optional.empty(), "CASE-R", 4L);

        assertEquals(ReputationMutationResult.Status.STALE_BLACKLIST, result.status());
        assertEquals(operation, api.operationId);
        assertEquals(4L, api.expectedRevision);
        assertEquals(CHECKSUM, api.expectedChecksum);
    }

    private static ReputationIntegration available(RecordingApi api, boolean authorized) {
        return new ReputationIntegration(
                IntegrationAvailability.AVAILABLE,
                "",
                (actor, action) -> authorized,
                api
        );
    }

    private static final class RecordingApi implements ReputationModerationApi {
        private Optional<ReputationBlacklist> current = Optional.empty();
        private ReputationMutationResult.Status nextStatus = ReputationMutationResult.Status.APPLIED;
        private UUID operationId;
        private long expectedRevision = -1L;
        private String expectedChecksum;
        private Optional<Instant> requestedExpiration;
        private String requestedCase;
        private int applyCalls;
        private int removeCalls;

        @Override
        public int apiVersion() {
            return API_VERSION;
        }

        @Override
        public boolean isReputationBlacklisted(UUID playerId) {
            return current.map(value -> value.activeAt(NOW)).orElse(false);
        }

        @Override
        public ReputationBlacklist blacklist(UUID playerId, Instant expirationAt, String caseId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ReputationBlacklist blacklistPermanently(UUID playerId, String caseId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeBlacklist(UUID playerId, String caseId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean canGiveReputation(UUID playerId) {
            return !isReputationBlacklisted(playerId);
        }

        @Override
        public Optional<ReputationBlacklist> getBlacklist(UUID playerId) {
            return current;
        }

        @Override
        public ReputationStateSnapshot snapshot(UUID playerId) {
            return SNAPSHOT;
        }

        @Override
        public ReputationMutationResult applyBlacklist(
                UUID operationId,
                UUID playerId,
                Optional<Instant> expirationAt,
                String caseId,
                long expectedBlacklistRevision,
                String expectedReputationChecksum
        ) {
            applyCalls++;
            this.operationId = operationId;
            this.expectedRevision = expectedBlacklistRevision;
            this.expectedChecksum = expectedReputationChecksum;
            this.requestedExpiration = expirationAt;
            this.requestedCase = caseId;
            ReputationBlacklist resultBlacklist = new ReputationBlacklist(
                    playerId,
                    NOW,
                    expirationAt,
                    caseId,
                    caseId,
                    ReputationBlacklist.Status.ACTIVE,
                    expectedBlacklistRevision + 1L,
                    NOW
            );
            return new ReputationMutationResult(
                    nextStatus,
                    nextStatus == ReputationMutationResult.Status.APPLIED
                            ? Optional.of(resultBlacklist) : Optional.empty(),
                    SNAPSHOT,
                    SNAPSHOT,
                    "test"
            );
        }

        @Override
        public ReputationMutationResult removeBlacklist(
                UUID operationId,
                UUID playerId,
                String caseId,
                long expectedBlacklistRevision,
                String expectedReputationChecksum
        ) {
            removeCalls++;
            this.operationId = operationId;
            this.expectedRevision = expectedBlacklistRevision;
            this.expectedChecksum = expectedReputationChecksum;
            this.requestedCase = caseId;
            return new ReputationMutationResult(
                    ReputationMutationResult.Status.REMOVED,
                    current,
                    SNAPSHOT,
                    SNAPSHOT,
                    "test"
            );
        }
    }
}
