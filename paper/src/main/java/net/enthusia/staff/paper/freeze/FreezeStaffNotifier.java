package net.enthusia.staff.paper.freeze;

import java.util.ArrayList;
import java.util.List;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.paper.auth.PaperStaffRankResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class FreezeStaffNotifier implements FreezeAlertSink {
    private final JavaPlugin plugin;

    public FreezeStaffNotifier(JavaPlugin plugin) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void frozen(PlayerIdentity target, Actor actor, String reason) {
        announce(target, actor, reason, true);
    }

    @Override
    public void unfrozen(PlayerIdentity target, Actor actor, String reason) {
        announce(target, actor, reason, false);
    }

    private void announce(PlayerIdentity target, Actor actor, String reason, boolean frozen) {
        Component message = render(target, actor, reason, frozen);
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            List<Player> online = new ArrayList<>(plugin.getServer().getOnlinePlayers());
            online.forEach(player -> scheduleRecipient(plugin, player, message));
        });
    }

    static Component render(PlayerIdentity target, Actor actor, String reason, boolean frozen) {
        String targetName = target.currentUsername().orElse(target.playerId().toString());
        Component message = Component.text("[Freeze] ", NamedTextColor.GOLD)
                .append(Component.text(targetName, NamedTextColor.YELLOW))
                .append(Component.text(frozen ? " frozen by " : " unfrozen by ", NamedTextColor.GRAY))
                .append(Component.text(actor.displayName(), NamedTextColor.AQUA))
                .append(Component.text(" — " + reason, NamedTextColor.GRAY));
        if (target.currentUsername().isEmpty()) {
            return message;
        }
        Component teleport = Component.text(" [Teleport]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/tp " + targetName))
                .hoverEvent(HoverEvent.showText(Component.text("Teleport to " + targetName)));
        return message.append(teleport);
    }

    static boolean scheduleRecipient(Plugin plugin, Player player, Component message) {
        try {
            return player.getScheduler().execute(plugin, () -> {
                if (isStaff(player)) {
                    player.sendMessage(message);
                }
            }, null, 1L);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean isStaff(Player player) {
        return PaperStaffRankResolver.resolve(player::hasPermission).isPresent();
    }
}
