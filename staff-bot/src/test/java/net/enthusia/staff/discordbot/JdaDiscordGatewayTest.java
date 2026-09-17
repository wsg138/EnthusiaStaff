package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.junit.jupiter.api.Test;

class JdaDiscordGatewayTest {
    @Test
    void restrictionRuntimeEnablesOnlyMemberOverrideCache() {
        assertEquals(Set.of(CacheFlag.MEMBER_OVERRIDES), JdaDiscordGateway.requiredCacheFlags());
    }

    @Test
    void staleIdentityCallbacksAreRejectedAfterDisconnectOrNewResolution() {
        JdaDiscordGateway.CallbackFence fence = new JdaDiscordGateway.CallbackFence();
        AtomicBoolean invoked = new AtomicBoolean();

        long disconnectedSession = fence.beginResolution();
        fence.invalidate();
        assertFalse(fence.runIfCurrent(disconnectedSession, () -> invoked.set(true)));
        assertFalse(invoked.get());

        long supersededSession = fence.beginResolution();
        long currentSession = fence.beginResolution();
        assertFalse(fence.runIfCurrent(supersededSession, () -> invoked.set(true)));
        assertFalse(invoked.get());
        assertTrue(fence.runIfCurrent(currentSession, () -> invoked.set(true)));
        assertTrue(invoked.get());
    }
}
