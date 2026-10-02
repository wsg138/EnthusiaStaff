package net.enthusia.staff.paper.visibility;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Keeps private-message targets local until remote visibility can be verified. */
public final class PrivateMessagePresenceListener implements Listener {
    private static final int COMMAND_WITH_TARGET_PARTS = 2;
    private static final Set<String> COMMANDS = Set.of("msg", "message", "m", "pm", "whisper", "w", "tell", "t");
    private final Map<UUID, String> localNames = new ConcurrentHashMap<>();
    private final BiPredicate<UUID, UUID> canSee;

    public PrivateMessagePresenceListener(JavaPlugin plugin, VanishManager vanish) {
        this.canSee = vanish::canSee;
        plugin.getServer().getOnlinePlayers().forEach(player -> localNames.put(player.getUniqueId(), player.getName()));
    }

    PrivateMessagePresenceListener(BiPredicate<UUID, UUID> canSee, Map<UUID, String> names) {
        this.canSee = canSee;
        localNames.putAll(names);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        localNames.put(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        localNames.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncCompletion(AsyncTabCompleteEvent event) {
        if (event.isCommand() && event.getSender() instanceof Player player) {
            suggestions(player.getUniqueId(), event.getBuffer()).ifPresent(names -> {
                event.setCompletions(names);
                event.setHandled(true);
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCompletion(TabCompleteEvent event) {
        if (event.getSender() instanceof Player player) {
            suggestions(player.getUniqueId(), event.getBuffer()).ifPresent(event::setCompletions);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String[] parts = messageParts(event.getMessage());
        if (parts.length >= COMMAND_WITH_TARGET_PARTS && !parts[1].isEmpty() && !targetAllowed(event.getPlayer().getUniqueId(), parts[1])) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text(
                    "That player is not available for private messages on this server.", NamedTextColor.RED));
        }
    }

    Optional<List<String>> suggestions(UUID viewer, String buffer) {
        String[] parts = messageParts(buffer);
        if (parts.length != COMMAND_WITH_TARGET_PARTS) {
            return Optional.empty();
        }
        String prefix = parts[1].toLowerCase(Locale.ROOT);
        return Optional.of(localNames.entrySet().stream()
                .filter(entry -> entry.getValue().toLowerCase(Locale.ROOT).startsWith(prefix))
                .filter(entry -> canSee.test(viewer, entry.getKey()))
                .map(Map.Entry::getValue).sorted(String.CASE_INSENSITIVE_ORDER).toList());
    }

    boolean targetAllowed(UUID viewer, String target) {
        return localNames.entrySet().stream().anyMatch(entry -> entry.getValue().equalsIgnoreCase(target)
                && canSee.test(viewer, entry.getKey()));
    }

    private static String[] messageParts(String buffer) {
        if (!buffer.startsWith("/")) {
            return new String[0];
        }
        String[] parts = buffer.substring(1).split("\\s+", -1);
        String command = parts[0].toLowerCase(Locale.ROOT);
        command = command.substring(command.lastIndexOf(':') + 1);
        return parts.length >= COMMAND_WITH_TARGET_PARTS && COMMANDS.contains(command) ? parts : new String[0];
    }
}
