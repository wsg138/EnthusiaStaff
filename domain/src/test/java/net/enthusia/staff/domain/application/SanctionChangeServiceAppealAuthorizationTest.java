package net.enthusia.staff.domain.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.ModerationAction;
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

final class SanctionChangeServiceAppealAuthorizationTest {
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID SANCTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final UUID SUBJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000013");
    private static final UUID APPEAL_ID = UUID.fromString("00000000-0000-0000-0000-000000000014");
    private static final Actor MODERATOR = new Actor(ACTOR_ID, "Moderator", StaffRank.MOD);

    @Test
    void linkedAppealUsesAppealAuthorizationWithoutGrantingGeneralOverturnAuthority() {
        AtomicReference<ModerationAction> checked = new AtomicReference<>();
        AtomicBoolean called = new AtomicBoolean();
        SanctionChangeService service = new SanctionChangeService(
                (actor, action) -> {
                    checked.set(action);
                    return action == ModerationAction.ACCEPT_APPEAL;
                },
                new ExactStore(called)
        );

        ExactSanctionChangeResult linked = service.applyExact(
                request(Optional.of(APPEAL_ID)),
                OperationalMode.ACTIVE,
                SanctionActionLimits.defaults()
        );

        assertInstanceOf(ExactSanctionChangeResult.Applied.class, linked);
        assertEquals(ModerationAction.ACCEPT_APPEAL, checked.get());
        assertTrue(called.get());

        called.set(false);
        ExactSanctionChangeResult unlinked = service.applyExact(
                request(Optional.empty()),
                OperationalMode.ACTIVE,
                SanctionActionLimits.defaults()
        );

        assertEquals(
                "FORBIDDEN",
                assertInstanceOf(ExactSanctionChangeResult.Rejected.class, unlinked).code()
        );
        assertEquals(ModerationAction.FULL_OVERTURN, checked.get());
        org.junit.jupiter.api.Assertions.assertFalse(called.get());
    }

    @Test
    void nonReviewerCannotConstructAnAppealHierarchyBypass() {
        Actor helper = new Actor(ACTOR_ID, "Helper", StaffRank.HELPER);

        assertThrows(
                IllegalArgumentException.class,
                () -> request(helper, Optional.of(APPEAL_ID), true)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> request(MODERATOR, Optional.empty(), true)
        );
    }

    private static ExactSanctionChangeRequest request(Optional<UUID> appealId) {
        return request(MODERATOR, appealId, appealId.isPresent());
    }

    private static ExactSanctionChangeRequest request(
            Actor actor,
            Optional<UUID> appealId,
            boolean bypassHierarchy
    ) {
        return new ExactSanctionChangeRequest(
                new IdempotencyKey("appeal-authorization-test-" + actor.rank() + '-' + appealId.isPresent()),
                SANCTION_ID,
                0L,
                actor,
                SanctionChangeAction.FULL_OVERTURN,
                Optional.empty(),
                "Accepted appeal exact sanction overturn",
                appealId,
                Optional.empty(),
                "VELOCITY_WEBSITE",
                bypassHierarchy
        );
    }

    private static final class ExactStore implements SanctionMutationStore {
        private final AtomicBoolean called;

        private ExactStore(AtomicBoolean called) {
            this.called = called;
        }

        @Override
        public SanctionChangeResult apply(SanctionChangeRequest request) {
            throw new AssertionError("case-wide mutation path was called");
        }

        @Override
        public ExactSanctionChangeResult applyExact(
                ExactSanctionChangeRequest request,
                SanctionActionLimits limits
        ) {
            called.set(true);
            return new ExactSanctionChangeResult.Applied(
                    new CaseId("CASE000000000011"),
                    SANCTION_ID,
                    SUBJECT_ID,
                    SanctionChangeAction.FULL_OVERTURN,
                    SanctionStatus.ACTIVE,
                    SanctionStatus.OVERTURNED,
                    Optional.empty(),
                    Optional.empty(),
                    java.time.Instant.parse("2026-08-05T20:00:00Z"),
                    request.linkedAppealId(),
                    Optional.empty(),
                    false
            );
        }
    }
}
