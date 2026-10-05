package net.enthusia.staff.paper.staff;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.paper.auth.PaperStaffRankResolver;
import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Enforces the overnight permission model's core principle: staff hold staff <em>command</em>
 * permissions only while IN staff mode. Off duty they keep default-rank permissions, their staff
 * tag, and staff notifications — but staff-authority commands are rejected until /staff.
 *
 * <p>This is defense-in-depth alongside the LuckPerms {@code staff-duty} context
 * (LuckPermsStaffDutyContext): it applies even when the permission backend is not
 * context-configured. Non-staff players are never affected; their normal permission checks run
 * untouched.
 */
public final class StaffDutyCommandGate implements Listener {
    /** Staff-authority commands that require an active staff-mode session. */
    private static final Set<String> ON_DUTY_COMMANDS = Set.of(
            "punish", "ban", "mute", "warn", "kick", "ipban",
            "removepunishment", "unban", "unmute", "removewarning", "unwarn",
            "reports", "aireview", "inspect", "invsee", "endersee",
            "alts", "alt", "client", "freeze", "unfreeze",
            "case", "fakebase", "cheattester", "stafftools", "staffwho");

    /** Commands that always work off duty: going on/off duty, vanish, and staff chat. */
    private static final String DUTY_COMMAND = "staff";
    private static final String VANISH_COMMAND = "vanish";
    private static final String STAFF_CHAT_COMMAND = "staffchat";
    private static final String ESTAFF_COMMAND = "estaff";

    private final StaffModeManager staffMode;

    public StaffDutyCommandGate(StaffModeManager staffMode) {
        this.staffMode = Objects.requireNonNull(staffMode, "staffMode");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        StaffRank rank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
        if (rank == null) {
            return;
        }
        String message = event.getMessage();
        String name = commandName(message);
        if (isAlwaysAllowed(name)) {
            return; // going on/off duty, vanish, and staff chat work off duty
        }
        if (requiresOnDuty(name, message, player)) {
            deny(event, player);
        }
    }

    private static boolean isAlwaysAllowed(String name) {
        return DUTY_COMMAND.equals(name) || VANISH_COMMAND.equals(name) || STAFF_CHAT_COMMAND.equals(name);
    }

    private boolean requiresOnDuty(String name, String message, Player player) {
        if (ESTAFF_COMMAND.equals(name)) {
            return isReloadSubcommand(message) && !staffMode.authorityActive(player.getUniqueId());
        }
        return ON_DUTY_COMMANDS.contains(name) && !staffMode.authorityActive(player.getUniqueId());
    }

    private static void deny(PlayerCommandPreprocessEvent event, Player player) {
        event.setCancelled(true);
        player.sendMessage(StaffMessageStyle.style(Component.text(
                "That command requires active staff mode. Use /staff to go on duty first.")));
    }

    private static boolean isReloadSubcommand(String message) {
        String[] parts = message.trim().split("\\s+");
        return parts.length > 1 && "reload".equalsIgnoreCase(parts[1]);
    }

    static String commandName(String message) {
        String stripped = message.startsWith("/") ? message.substring(1) : message;
        int space = stripped.indexOf(' ');
        String label = space < 0 ? stripped : stripped.substring(0, space);
        int namespaced = label.indexOf(':');
        String name = namespaced < 0 ? label : label.substring(namespaced + 1);
        return name.toLowerCase(Locale.ROOT);
    }
}
