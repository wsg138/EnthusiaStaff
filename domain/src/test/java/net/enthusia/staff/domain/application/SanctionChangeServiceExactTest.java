package net.enthusia.staff.domain.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.SanctionMutationStore;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeRequest;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeResult;
import net.enthusia.staff.domain.sanction.SanctionActionLimits;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;
import net.enthusia.staff.domain.sanction.SanctionChangeRequest;
import net.enthusia.staff.domain.sanction.SanctionChangeResult;
import net.enthusia.staff.domain.sanction.SanctionStatus;
import org.junit.jupiter.api.Test;

final class SanctionChangeServiceExactTest {
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SANCTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SUBJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void operationalModeBlocksBeforeStoreAccess() {
        AtomicBoolean called = new AtomicBoolean();
        SanctionChangeService service = new SanctionChangeService(
                (actor, action) -> true,
                new StubStore(called)
        );

        ExactSanctionChangeResult result = service.applyExact(
                request("valid reason"),
                OperationalMode.SHADOW_MIGRATION,
                SanctionActionLimits.defaults()
        );

        ExactSanctionChangeResult.Rejected rejected = assertInstanceOf(
                ExactSanctionChangeResult.Rejected.class,
                result
        );
        assertEquals("MODE_BLOCKED", rejected.code());
        assertFalse(called.get());
    }

    @Test
    void authorizationIsCheckedAtTheServiceBoundary() {
        AtomicBoolean called = new AtomicBoolean();
        SanctionChangeService service = new SanctionChangeService(
                (actor, action) -> false,
                new StubStore(called)
        );

        ExactSanctionChangeResult result = service.applyExact(
                request("valid reason"),
                OperationalMode.ACTIVE,
                SanctionActionLimits.defaults()
        );

        assertEquals(
                "FORBIDDEN",
                assertInstanceOf(ExactSanctionChangeResult.Rejected.class, result).code()
        );
        assertFalse(called.get());
    }

    @Test
    void configuredReasonLimitsAreEnforcedBeforePersistence() {
        AtomicBoolean called = new AtomicBoolean();
        SanctionChangeService service = new SanctionChangeService(
                (actor, action) -> true,
                new StubStore(called)
        );

        ExactSanctionChangeResult result = service.applyExact(
                request("no"),
                OperationalMode.ACTIVE,
                new SanctionActionLimits(3, 20, true)
        );

        assertEquals(
                "INVALID_REASON",
                assertInstanceOf(ExactSanctionChangeResult.Rejected.class, result).code()
        );
        assertFalse(called.get());
    }

    @Test
    void validRequestDelegatesToTheFencedStoreContract() {
        AtomicBoolean called = new AtomicBoolean();
        StubStore store = new StubStore(called);
        SanctionChangeService service = new SanctionChangeService(
                (actor, action) -> true,
                store
        );
        ExactSanctionChangeRequest request = request("valid reason");

        ExactSanctionChangeResult result = service.applyExact(
                request,
                OperationalMode.ACTIVE,
                SanctionActionLimits.defaults()
        );

        assertSame(store.result, result);
        assertTrue(called.get());
    }

    private static ExactSanctionChangeRequest request(String reason) {
        return new ExactSanctionChangeRequest(
                new IdempotencyKey("test-exact-sanction-change"),
                SANCTION_ID,
                0L,
                new Actor(ACTOR_ID, "Moderator", StaffRank.MOD),
                SanctionChangeAction.END_EARLY,
                Optional.empty(),
                reason,
                Optional.empty(),
                Optional.empty(),
                "SMP",
                false
        );
    }

    private static final class StubStore implements SanctionMutationStore {
        private final AtomicBoolean called;
        private final ExactSanctionChangeResult.Applied result = new ExactSanctionChangeResult.Applied(
                new CaseId("CASE000000000001"),
                SANCTION_ID,
                SUBJECT_ID,
                SanctionChangeAction.END_EARLY,
                SanctionStatus.ACTIVE,
                SanctionStatus.ENDED_EARLY,
                Optional.of(Instant.parse("2026-08-08T00:00:00Z")),
                Optional.of(Instant.parse("2026-08-08T00:00:00Z")),
                Instant.parse("2026-08-02T00:00:00Z"),
                Optional.empty(),
                Optional.empty(),
                false
        );

        private StubStore(AtomicBoolean called) {
            this.called = called;
        }

        @Override
        public SanctionChangeResult apply(SanctionChangeRequest request) {
            throw new AssertionError("legacy mutation path was called");
        }

        @Override
        public java.util.OptionalLong exactRevision(UUID sanctionId) {
            return java.util.OptionalLong.of(0L);
        }

        @Override
        public ExactSanctionChangeResult applyExact(
                ExactSanctionChangeRequest request,
                SanctionActionLimits limits
        ) {
            called.set(true);
            return result;
        }
    }
}
