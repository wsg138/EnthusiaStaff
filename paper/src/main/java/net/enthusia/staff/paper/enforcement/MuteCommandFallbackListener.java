package net.enthusia.staff.paper.enforcement;

import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Fail-closed private-message mute enforcement used only when RoseChat is absent.
 */
public final class MuteCommandFallbackListener implements Listener {
    private static final Set<String> PRIVATE_MESSAGE_COMMANDS = Set.of(
            "msg", "tell", "w", "whisper", "pm", "message", "reply", "r"
    );

    private final Supplier<MuteEnforcementListener> mutes;

    public MuteCommandFallbackListener(Supplier<MuteEnforcementListener> mutes) {
        this.mutes = java.util.Objects.requireNonNull(mutes, "mutes");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isPrivateMessageCommand(event.getMessage())) {
            return;
        }
        MuteEnforcementListener enforcement = mutes.get();
        if (enforcement == null) {
            block(event, "Private-message moderation is temporarily unavailable. Please try again shortly.");
            return;
        }
        switch (decision(enforcement.cachedStatus(event.getPlayer().getUniqueId()))) {
            case ALLOW -> {
            }
            case MUTED -> block(event, "You are muted and cannot send private messages.");
            case VERIFY -> {
                block(event, "Your mute status is still being verified. Please try again shortly.");
                enforcement.invalidate(event.getPlayer().getUniqueId());
            }
            default -> block(event, "Private-message moderation is temporarily unavailable. Please try again shortly.");
        }
    }

    static boolean isPrivateMessageCommand(String rawMessage) {
        String command = commandName(rawMessage);
        return command != null && PRIVATE_MESSAGE_COMMANDS.contains(command);
    }

    static Decision decision(MuteEnforcementListener.CachedMuteStatus status) {
        return switch (status) {
            case CLEAR -> Decision.ALLOW;
            case MUTED -> Decision.MUTED;
            case UNVERIFIED -> Decision.VERIFY;
        };
    }

    private static String commandName(String rawMessage) {
        if (rawMessage == null || rawMessage.length() < 2 || rawMessage.charAt(0) != '/') {
            return null;
        }
        int end = rawMessage.indexOf(' ');
        String token = (end < 0 ? rawMessage.substring(1) : rawMessage.substring(1, end))
                .toLowerCase(Locale.ROOT);
        int namespace = token.lastIndexOf(':');
        return namespace < 0 ? token : token.substring(namespace + 1);
    }

    private static void block(PlayerCommandPreprocessEvent event, String message) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        player.sendMessage(Component.text(message));
    }

    enum Decision {
        ALLOW,
        MUTED,
        VERIFY
    }
}
