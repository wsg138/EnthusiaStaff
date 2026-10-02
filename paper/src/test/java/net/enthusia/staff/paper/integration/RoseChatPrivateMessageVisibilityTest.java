package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rosewood.rosechat.api.staff.ModerationDecision;
import dev.rosewood.rosechat.api.staff.PrivateMessageContext;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.paper.api.StaffVisibilityService;
import org.junit.jupiter.api.Test;

class RoseChatPrivateMessageVisibilityTest {
    private static final UUID SENDER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RECIPIENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test
    void hiddenRecipientLooksUnavailableToSender() {
        RoseChatPrivateMessageVisibility policy = new RoseChatPrivateMessageVisibility(visibility(false));

        ModerationDecision decision = policy.evaluate(context(Optional.of(RECIPIENT_ID))).orElseThrow();

        assertEquals(ModerationDecision.Action.BLOCK, decision.action());
        assertEquals("Invalid Player: 'HiddenPlayer'", decision.feedback());
    }

    @Test
    void visibleRecipientContinuesThroughNormalModeration() {
        RoseChatPrivateMessageVisibility policy = new RoseChatPrivateMessageVisibility(visibility(true));

        assertTrue(policy.evaluate(context(Optional.of(RECIPIENT_ID))).isEmpty());
    }

    @Test
    void unresolvedRemoteRecipientFailsClosedIncludingReplyRoutes() {
        RoseChatPrivateMessageVisibility policy = new RoseChatPrivateMessageVisibility(visibility(false));

        ModerationDecision decision = policy.evaluate(context(Optional.empty())).orElseThrow();
        assertEquals(ModerationDecision.Action.BLOCK, decision.action());
        assertEquals("Invalid Player: 'HiddenPlayer'", decision.feedback());
    }

    private static PrivateMessageContext context(Optional<UUID> recipientId) {
        return new PrivateMessageContext(
                UUID.fromString("30000000-0000-0000-0000-000000000003"),
                SENDER_ID,
                "Sender",
                recipientId,
                "HiddenPlayer",
                "hello"
        );
    }

    private static StaffVisibilityService visibility(boolean canSee) {
        return new StaffVisibilityService() {
            @Override
            public boolean isVanished(UUID playerId) {
                return false;
            }

            @Override
            public boolean canSee(UUID viewerId, UUID targetId) {
                assertEquals(SENDER_ID, viewerId);
                assertEquals(RECIPIENT_ID, targetId);
                return canSee;
            }
        };
    }
}
