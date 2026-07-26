package net.enthusia.staff.domain.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultAuthorizationPolicyTest {
    private final DefaultAuthorizationPolicy policy = new DefaultAuthorizationPolicy();

    @Test
    void modCannotRaiseOrOverturnDirectly() {
        Actor mod = new Actor(UUID.randomUUID(), "Moderator", StaffRank.MOD);
        assertFalse(policy.permits(mod, ModerationAction.RAISE_RECOMMENDATION));
        assertFalse(policy.permits(mod, ModerationAction.FULL_OVERTURN));
        assertTrue(policy.permits(mod, ModerationAction.REQUEST_FULL_OVERTURN));
    }

    @Test
    void onlyFounderCanUseArbitraryCombinations() {
        Actor admin = new Actor(UUID.randomUUID(), "Administrator", StaffRank.ADMIN);
        Actor founder = new Actor(UUID.randomUUID(), "Owner", StaffRank.FOUNDER);
        assertFalse(policy.permits(admin, ModerationAction.USE_CUSTOM_COMBINATION));
        assertTrue(policy.permits(founder, ModerationAction.USE_CUSTOM_COMBINATION));
    }
}
