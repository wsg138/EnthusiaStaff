package net.enthusia.staff.paper.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

class ActiveDutyAuthorizationPolicyTest {
    private static final UUID STAFF_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final ModerationAction ACTION = ModerationAction.ISSUE_POLICY_SANCTION;

    @Test
    void activePlayerStillRequiresBaseAuthorization() {
        ActiveDutyAuthorizationPolicy allowed = new ActiveDutyAuthorizationPolicy((actor, action) -> true, STAFF_ID::equals);
        ActiveDutyAuthorizationPolicy denied = new ActiveDutyAuthorizationPolicy((actor, action) -> false, STAFF_ID::equals);
        Actor actor = new Actor(STAFF_ID, "Helper", StaffRank.HELPER);

        assertTrue(allowed.permits(actor, ACTION));
        assertFalse(denied.permits(actor, ACTION));
    }

    @Test
    void inactivePlayerIsDeniedEvenWhenBasePolicyAllowsAction() {
        ActiveDutyAuthorizationPolicy policy = new ActiveDutyAuthorizationPolicy((actor, action) -> true, ignored -> false);
        Actor actor = new Actor(STAFF_ID, "Mod", StaffRank.MOD);

        assertFalse(policy.permits(actor, ACTION));
    }

    @Test
    void consoleCanUseBaseAuthorizedRecoveryPathWithoutPlayerSession() {
        ActiveDutyAuthorizationPolicy policy = new ActiveDutyAuthorizationPolicy((actor, action) -> true, ignored -> false);
        Actor console = new Actor(new UUID(0L, 0L), "CONSOLE", StaffRank.FOUNDER);

        assertTrue(policy.permits(console, ModerationAction.OWNER_RECOVERY));
    }

    @Test
    void systemActorCanUseBaseAuthorizedAutomationPathWithoutPlayerSession() {
        ActiveDutyAuthorizationPolicy policy = new ActiveDutyAuthorizationPolicy((actor, action) -> true, ignored -> false);
        Actor system = new Actor(UUID.randomUUID(), "SYSTEM", StaffRank.SYSTEM);

        assertTrue(policy.permits(system, ACTION));
    }

    @Test
    void nullInputsFailClosed() {
        ActiveDutyAuthorizationPolicy policy = new ActiveDutyAuthorizationPolicy((actor, action) -> true, ignored -> true);
        Actor actor = new Actor(STAFF_ID, "Admin", StaffRank.ADMIN);

        assertFalse(policy.permits(null, ACTION));
        assertFalse(policy.permits(actor, null));
    }
}
