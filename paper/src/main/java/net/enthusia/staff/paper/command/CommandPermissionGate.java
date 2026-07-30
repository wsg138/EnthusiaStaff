package net.enthusia.staff.paper.command;

import java.util.Objects;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

final class CommandPermissionGate {
    private CommandPermissionGate() {
    }

    static boolean require(CommandSender sender, String permission, String denialMessage) {
        Objects.requireNonNull(sender, "sender");
        if (allows(sender::hasPermission, permission)) {
            return true;
        }
        sender.sendMessage(Component.text(denialMessage));
        return false;
    }

    static boolean allows(Predicate<String> hasPermission, String permission) {
        Objects.requireNonNull(hasPermission, "hasPermission");
        if (permission == null || permission.isBlank()) {
            return false;
        }
        return hasPermission.test(permission);
    }
}
