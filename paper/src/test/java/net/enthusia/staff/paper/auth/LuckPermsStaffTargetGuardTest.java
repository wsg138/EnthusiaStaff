package net.enthusia.staff.paper.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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

        assertTrue(guard(StaffRank.MOD, Optional.empty()).check(mod, TARGET_ID, false).allowed());
        assertFalse(guard(StaffRank.MOD, Optional.of(StaffRank.MOD)).check(mod, TARGET_ID, false).allowed());
        assertFalse(guard(StaffRank.MOD, Optional.of(StaffRank.ADMIN)).check(mod, TARGET_ID, false).allowed());
    }

    @Test
    void currentActorRankOverridesStaleCommandAuthority() {
        Actor staleAdmin = new Actor(ACTOR_ID, "FormerAdmin", StaffRank.ADMIN);
        StaffTargetGuard guard = guard(StaffRank.MOD, Optional.of(StaffRank.ADMIN));

        assertFalse(guard.check(staleAdmin, TARGET_ID, false).allowed());
    }

    @Test
    void downgradeCannotReuseStaleHigherRankAgainstOrdinaryPlayer() {
        Actor staleAdmin = new Actor(ACTOR_ID, "FormerAdmin", StaffRank.ADMIN);
        StaffTargetGuard guard = guard(StaffRank.MOD, Optional.empty());

        StaffTargetGuard.Result result = guard.check(staleAdmin, TARGET_ID, false);

        assertFalse(result.allowed(),
                "Current lower rank must not authorize a queued punishment using older admin actor");
    }

    @Test
    void promotionAlsoRequiresNewCommandAuthoritySnapshot() {
        Actor staleMod = new Actor(ACTOR_ID, "FormerMod", StaffRank.MOD);
        StaffTargetGuard guard = guard(StaffRank.ADMIN, Optional.empty());

        assertFalse(guard.check(staleMod, TARGET_ID, false).allowed(),
                "Rank drift in either direction must require a new authorization snapshot");
    }

    @Test
    void unchangedStaffRankRetainsNormalOrdinaryTargetAccess() {
        assertTrue(guard(StaffRank.MOD, Optional.empty()).check(
                new Actor(ACTOR_ID, "ActiveMod", StaffRank.MOD), TARGET_ID, false).allowed());
    }

    @Test
    void repeatedRankValidationRejectsMutationBetweenPreviewAndWrite() {
        AtomicReference<StaffRank> current = new AtomicReference<>(StaffRank.ADMIN);
        StaffTargetGuard guard = LuckPermsStaffTargetGuard.forTesting(
                id -> id.equals(ACTOR_ID) ? Optional.of(current.get()) : Optional.empty(),
                LOGGER
        );
        Actor queued = new Actor(ACTOR_ID, "AdminAtClick", StaffRank.ADMIN);
        assertTrue(guard.check(queued, TARGET_ID, false).allowed());
        current.set(StaffRank.MOD);
        assertFalse(guard.check(queued, TARGET_ID, false).allowed(),
                "Repeating the guard after a slow read must reject an actor demoted in the meantime");
    }

    @Test
    void actorThatNoLongerHasStaffRankFailsClosed() {
        Actor staleAdmin = new Actor(ACTOR_ID, "FormerAdmin", StaffRank.ADMIN);
        StaffTargetGuard guard = LuckPermsStaffTargetGuard.forTesting(
                playerId -> playerId.equals(ACTOR_ID) ? Optional.empty() : Optional.of(StaffRank.MOD),
                LOGGER
        );

        assertFalse(guard.check(staleAdmin, TARGET_ID, false).allowed());
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

    private static StaffTargetGuard guard(StaffRank actorRank, Optional<StaffRank> targetRank) {
        return LuckPermsStaffTargetGuard.forTesting(
                playerId -> playerId.equals(ACTOR_ID) ? Optional.of(actorRank) : targetRank,
                LOGGER
        );
    }
}
