package net.enthusia.staff.paper.enforcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MuteCommandFallbackListenerTest {
    @Test
    void privateMessageAliasesAndNamespacedAliasesAreRecognized() {
        assertTrue(MuteCommandFallbackListener.isPrivateMessageCommand("/msg Player hello"));
        assertTrue(MuteCommandFallbackListener.isPrivateMessageCommand("/minecraft:tell Player hello"));
        assertTrue(MuteCommandFallbackListener.isPrivateMessageCommand("/R hello"));
        assertFalse(MuteCommandFallbackListener.isPrivateMessageCommand("/spawn"));
        assertFalse(MuteCommandFallbackListener.isPrivateMessageCommand("hello"));
    }

    @Test
    void cachedMuteStatusMapsToFailClosedCommandDecision() {
        assertEquals(
                MuteCommandFallbackListener.Decision.ALLOW,
                MuteCommandFallbackListener.decision(MuteEnforcementListener.CachedMuteStatus.CLEAR)
        );
        assertEquals(
                MuteCommandFallbackListener.Decision.MUTED,
                MuteCommandFallbackListener.decision(MuteEnforcementListener.CachedMuteStatus.MUTED)
        );
        assertEquals(
                MuteCommandFallbackListener.Decision.VERIFY,
                MuteCommandFallbackListener.decision(MuteEnforcementListener.CachedMuteStatus.UNVERIFIED)
        );
    }
}
