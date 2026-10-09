package net.enthusia.staff.paper.integration;

import dev.rosewood.rosechat.api.staff.BridgeRegistration;
import dev.rosewood.rosechat.api.staff.PresenceContext;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/** Optional presence rendering is separate from the established moderation bridge. */
final class RoseChatPresenceRenderer {
    private final BridgeRegistration registration;
    private final AtomicBoolean unsupported = new AtomicBoolean();

    RoseChatPresenceRenderer(BridgeRegistration registration) {
        this.registration = Objects.requireNonNull(registration, "registration");
    }

    boolean render(PresenceContext context) {
        if (unsupported.get() || !registration.isActive()) {
            return false;
        }
        try {
            return registration.renderPresence(context);
        } catch (NoSuchMethodError | AbstractMethodError exception) {
            if (unsupported.compareAndSet(false, true)) {
                Logger.getLogger(RoseChatPresenceRenderer.class.getName()).warning(
                        "RoseChat does not expose optional presence rendering; the existing moderation bridge remains active");
            }
            return false;
        }
    }
}
