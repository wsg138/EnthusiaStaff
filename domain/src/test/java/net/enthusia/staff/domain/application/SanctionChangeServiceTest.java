package net.enthusia.staff.domain.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;
import net.enthusia.staff.domain.sanction.SanctionChangeRequest;
import net.enthusia.staff.domain.sanction.SanctionChangeResult;
import org.junit.jupiter.api.Test;

class SanctionChangeServiceTest {
    @Test
    void modCanRequestButCannotDirectlyOverturn() {
        AtomicInteger writes = new AtomicInteger();
        SanctionChangeService service = new SanctionChangeService(
                new DefaultAuthorizationPolicy(),
                request -> {
                    writes.incrementAndGet();
                    return new SanctionChangeResult.Applied(0, false);
                }
        );
        Actor mod = new Actor(UUID.randomUUID(), "Moderator", StaffRank.MOD);

        assertInstanceOf(SanctionChangeResult.Rejected.class,
                service.apply(request(mod, SanctionChangeAction.FULL_OVERTURN), OperationalMode.ACTIVE));
        assertInstanceOf(SanctionChangeResult.Applied.class,
                service.apply(request(mod, SanctionChangeAction.REQUEST_FULL_OVERTURN), OperationalMode.ACTIVE));
        assertEquals(1, writes.get());
    }

    @Test
    void allChangesAreBlockedBeforeValidatedCutover() {
        SanctionChangeService service = new SanctionChangeService(
                new DefaultAuthorizationPolicy(),
                request -> new SanctionChangeResult.Applied(1, false)
        );
        Actor admin = new Actor(UUID.randomUUID(), "Admin", StaffRank.ADMIN);

        SanctionChangeResult.Rejected result = assertInstanceOf(
                SanctionChangeResult.Rejected.class,
                service.apply(request(admin, SanctionChangeAction.FULL_OVERTURN), OperationalMode.SHADOW_MIGRATION)
        );
        assertEquals("MODE_BLOCKED", result.code());
    }

    @Test
    void developerCannotReachTheMutationStoreForAnyChange() {
        AtomicInteger writes = new AtomicInteger();
        SanctionChangeService service = new SanctionChangeService(
                new DefaultAuthorizationPolicy(),
                request -> {
                    writes.incrementAndGet();
                    return new SanctionChangeResult.Applied(1, false);
                }
        );
        Actor developer = new Actor(UUID.randomUUID(), "Developer", StaffRank.DEVELOPER);

        for (SanctionChangeAction action : SanctionChangeAction.values()) {
            assertInstanceOf(
                    SanctionChangeResult.Rejected.class,
                    service.apply(request(developer, action), OperationalMode.ACTIVE),
                    action.name()
            );
        }
        assertEquals(0, writes.get());
    }

    @Test
    void modMayOnlyLowerEndRevokeOrRequestOverturn() {
        AtomicInteger writes = new AtomicInteger();
        SanctionChangeService service = new SanctionChangeService(
                new DefaultAuthorizationPolicy(),
                request -> {
                    writes.incrementAndGet();
                    return new SanctionChangeResult.Applied(1, false);
                }
        );
        Actor mod = new Actor(UUID.randomUUID(), "Moderator", StaffRank.MOD);
        Set<SanctionChangeAction> allowed = Set.of(
                SanctionChangeAction.END_EARLY,
                SanctionChangeAction.REDUCE_DURATION,
                SanctionChangeAction.REVOKE,
                SanctionChangeAction.REMOVE_ESCALATION_CONTRIBUTION,
                SanctionChangeAction.REQUEST_FULL_OVERTURN
        );

        for (SanctionChangeAction action : SanctionChangeAction.values()) {
            SanctionChangeResult result = service.apply(request(mod, action), OperationalMode.ACTIVE);
            assertEquals(allowed.contains(action), result instanceof SanctionChangeResult.Applied, action.name());
        }
        assertEquals(allowed.size(), writes.get());
    }

    private static SanctionChangeRequest request(Actor actor, SanctionChangeAction action) {
        Optional<java.time.Instant> replacement = switch (action) {
            case REDUCE_DURATION, REPLACE_EXPIRATION -> Optional.of(java.time.Instant.parse("2030-01-01T00:00:00Z"));
            default -> Optional.empty();
        };
        return new SanctionChangeRequest(
                new IdempotencyKey("test:" + UUID.randomUUID()),
                new CaseId("0123456789ABCDEF"),
                actor,
                action,
                replacement,
                "Test reason"
        );
    }
}
