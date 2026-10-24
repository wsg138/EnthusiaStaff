package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.rosewood.rosechat.api.staff.MessageSurface;
import dev.rosewood.rosechat.api.staff.ModerationDecision;
import dev.rosewood.rosechat.api.staff.StaffChannelConfiguration;
import dev.rosewood.rosechat.api.staff.TransmissionContext;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.paper.enforcement.MuteEnforcementListener.CachedMuteStatus;
import org.junit.jupiter.api.Test;

class RoseChatDiscordMutePolicyTest {
    private static final StaffChannelConfiguration CHANNELS =
            new StaffChannelConfiguration("staff", "global", Set.of("reports"));

    @Test
    void publicMuteStillBlocksOfflineDiscordGlobalButNotStaffOrPrivate() {
        assertEquals(ModerationDecision.Action.BLOCK, decision("global", CachedMuteStatus.PUBLIC_MUTED));
        assertEquals(ModerationDecision.Action.ALLOW, decision("STAFF", CachedMuteStatus.PUBLIC_MUTED));
        assertEquals(ModerationDecision.Action.ALLOW, decision("REPORTS", CachedMuteStatus.PUBLIC_MUTED));
    }

    @Test
    void muteAndUnverifiedNeverAllowAnyChannel() {
        for (String channel : new String[]{"global", "staff", "reports"}) {
            assertEquals(ModerationDecision.Action.BLOCK, decision(channel, CachedMuteStatus.MUTED));
            assertEquals(ModerationDecision.Action.BLOCK, decision(channel, CachedMuteStatus.UNVERIFIED));
            assertEquals(ModerationDecision.Action.ALLOW, decision(channel, CachedMuteStatus.CLEAR));
        }
    }

    private static ModerationDecision.Action decision(String channel, CachedMuteStatus status) {
        return RoseChatIntegration.discordMuteDecision(CHANNELS, new TransmissionContext(
                new UUID(0, 42), "Offline", MessageSurface.CHANNEL, channel, "hello"), status).action();
    }
}
