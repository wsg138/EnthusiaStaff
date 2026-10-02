package net.enthusia.staff.paper.command;

import java.util.function.Supplier;
import net.enthusia.staff.paper.integration.RoseChatIntegration;
import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class StaffChatCommand implements CommandExecutor {
    private static final String PERMISSION = "enthusiastaff.staffchat";
    private static final String ROSECHAT_STAFF_PERMISSION = "rosechat.channel.staff";

    private final Supplier<RoseChatIntegration> integration;

    public StaffChatCommand(Supplier<RoseChatIntegration> integration) {
        this.integration = java.util.Objects.requireNonNull(integration, "integration");
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] arguments
    ) {
        if (!CommandPermissionGate.require(sender, PERMISSION, "You do not have permission to use staff chat.")) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(StaffMessageStyle.warning("RoseChat channel state belongs to an online player."));
            return true;
        }
        if (arguments.length != 0) {
            player.sendMessage(StaffMessageStyle.usage("Usage: /" + label));
            return true;
        }
        if (!CommandPermissionGate.require(
                player,
                ROSECHAT_STAFF_PERMISSION,
                "You do not have permission to use the RoseChat staff channel."
        )) {
            return true;
        }
        RoseChatIntegration loaded = integration.get();
        if (loaded == null || !loaded.bridgeActive()) {
            player.sendMessage(StaffMessageStyle.warning(
                    "RoseChat staff-channel integration is unavailable."
            ));
            return true;
        }
        if (!loaded.toggleStaffChannel(player.getUniqueId())) {
            player.sendMessage(StaffMessageStyle.warning("RoseChat has no configured staff channel."));
            return true;
        }
        String channel = loaded.currentChannel(player.getUniqueId()).orElse("unknown");
        player.sendMessage(channelChanged(channel));
        return true;
    }

    private static Component channelChanged(String channel) {
        return Component.text("STAFF CHAT", NamedTextColor.AQUA, TextDecoration.BOLD)
                .append(Component.text("  •  ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Now speaking in ", NamedTextColor.GRAY))
                .append(Component.text(channel, NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text("  •  ", NamedTextColor.DARK_GRAY))
                .append(Component.text("/staffchat", NamedTextColor.AQUA))
                .append(Component.text(" to switch again", NamedTextColor.GRAY));
    }
}
