package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import dev.rosewood.rosechat.api.staff.ChannelClassification;
import dev.rosewood.rosechat.api.staff.ChannelRecipientContext;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.paper.api.StaffVisibilityService;
import org.junit.jupiter.api.Test;

class RoseChatStaffChannelVisibilityTest {
    private static final UUID SENDER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RECIPIENT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final StaffVisibilityService HIDDEN = new StaffVisibilityService() {
        @Override public boolean isVanished(UUID playerId) { return true; }
        @Override public boolean canSee(UUID viewerId, UUID targetId) { return false; }
    };

    @Test void authorizedStaffChannelMessagesDoNotInheritWorldVanishFiltering() {
        assertTrue(RoseChatIntegration.canReceiveChannelMessage(
                HIDDEN, context(ChannelClassification.STAFF)));
    }

    @Test void publicChatStillRespectsWorldVanishFiltering() {
        assertFalse(RoseChatIntegration.canReceiveChannelMessage(
                HIDDEN, context(ChannelClassification.PUBLIC)));
    }

    private static ChannelRecipientContext context(ChannelClassification classification) {
        return new ChannelRecipientContext(UUID.randomUUID(), Optional.of(SENDER),
                RECIPIENT, classification == ChannelClassification.STAFF ? "staff" : "global", classification);
    }
}
