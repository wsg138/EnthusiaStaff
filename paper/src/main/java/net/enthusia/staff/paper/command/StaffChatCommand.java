package net.enthusia.staff.paper.command;

import java.util.function.Supplier;
import net.enthusia.staff.paper.integration.RoseChatIntegration;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class StaffChatCommand implements CommandExecutor {
    private static final String PERMISSION = "enthusiastaff.staffchat";

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
            sender.sendMessage("RoseChat channel state belongs to an online player.");
            return true;
        }
        if (arguments.length != 0) {
            player.sendMessage(Component.text("Usage: /" + label));
            return true;
        }
        RoseChatIntegration loaded = integration.get();
        if (loaded == null || !loaded.bridgeActive()) {
            player.sendMessage(Component.text("RoseChat staff-channel integration is unavailable."));
            return true;
        }
        if (!loaded.toggleStaffChannel(player.getUniqueId())) {
            player.sendMessage(Component.text("RoseChat has no configured staff channel."));
            return true;
        }
        String channel = loaded.currentChannel(player.getUniqueId()).orElse("unknown");
        player.sendMessage(Component.text("RoseChat channel switched to " + channel + '.'));
        return true;
    }
}
