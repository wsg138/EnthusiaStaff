package net.enthusia.staff.paper.integration;

import dev.rosewood.rosechat.api.staff.ModerationDecision;
import dev.rosewood.rosechat.api.staff.PrivateMessageContext;
import java.util.Objects;
import java.util.Optional;
import net.enthusia.staff.paper.api.StaffVisibilityService;

final class RoseChatPrivateMessageVisibility {
    private static final String INVALID_PLAYER_PREFIX = "Invalid Player: '";

    private final StaffVisibilityService visibility;

    RoseChatPrivateMessageVisibility(StaffVisibilityService visibility) {
        this.visibility = Objects.requireNonNull(visibility, "visibility");
    }

    Optional<ModerationDecision> evaluate(PrivateMessageContext context) {
        Objects.requireNonNull(context, "context");
        if (context.recipientId().isEmpty()) {
            return Optional.of(ModerationDecision.block(
                    INVALID_PLAYER_PREFIX + context.recipientName() + "'"
            ));
        }
        return context.recipientId()
                .filter(recipientId -> !visibility.canSee(context.senderId(), recipientId))
                .map(ignored -> ModerationDecision.block(
                        INVALID_PLAYER_PREFIX + context.recipientName() + "'"
                ));
    }
}
