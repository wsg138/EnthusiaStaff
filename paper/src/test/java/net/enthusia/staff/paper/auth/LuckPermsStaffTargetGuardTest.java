package net.enthusia.staff.paper.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

class LuckPermsStaffTargetGuardTest {
    private static final UUID ACTOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Logger LOGGER = Logger.getLogger(LuckPermsStaffTargetGuardTest.class.getName());

    @Test
    void ordinaryTargetsAreAllowedAndEqualOrHigherStaffAreProtected() {
        Actor mod = new Actor(ACTOR_ID, "Mod", StaffRank.MOD);
        StaffTargetGuard ordinary = guard(Optional.empty());
        StaffTargetGuard equal = guard(Optional.of(StaffRank.MOD));
        StaffTargetGuard higher = guard(Optional.of(StaffRank.ADMIN));

        assertTrue(ordinary.check(mod, TARGET_ID, false).allowed());
        assertFalse(equal.check(mod, TARGET_ID, false).allowed());
        assertFalse(higher.check(mod, TARGET_ID, false).allowed());
    }

    @Test
    void lookupFailureFailsClosed() {
        StaffTargetGuard guard = LuckPermsStaffTargetGuard.forTesting(
                ignored -> {
                    throw new IllegalStateException("unavailable");
                },
                LOGGER
        );

        StaffTargetGuard.Result result = guard.check(
                new Actor(ACTOR_ID, "Admin", StaffRank.ADMIN),
                TARGET_ID,
                false
        );

        assertFalse(result.allowed());
    }

    @Test
    void explicitSystemActorBypassesLookup() {
        AtomicBoolean lookedUp = new AtomicBoolean();
        StaffTargetGuard guard = LuckPermsStaffTargetGuard.forTesting(
                ignored -> {
                    lookedUp.set(true);
                    return Optional.of(StaffRank.FOUNDER);
                },
                LOGGER
        );

        StaffTargetGuard.Result result = guard.check(
                new Actor(ACTOR_ID, "Console", StaffRank.FOUNDER),
                TARGET_ID,
                true
        );

        assertTrue(result.allowed());
        assertFalse(lookedUp.get());
    }

    private static StaffTargetGuard guard(Optional<StaffRank> rank) {
        return LuckPermsStaffTargetGuard.forTesting(ignored -> rank, LOGGER);
    }
}
