package net.enthusia.staff.paper;

import java.util.List;
import java.util.Objects;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

/** Grants console only the permission nodes declared by this plugin so command executors can apply SYSTEM semantics. */
final class ConsoleCommandAuthority implements AutoCloseable {
    private final PermissionAttachment attachment;

    private ConsoleCommandAuthority(PermissionAttachment attachment) {
        this.attachment = Objects.requireNonNull(attachment, "attachment");
    }

    static ConsoleCommandAuthority install(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        PermissionAttachment attachment = plugin.getServer().getConsoleSender().addAttachment(plugin);
        permissionNames(plugin.getPluginMeta().getPermissions())
                .forEach(permission -> attachment.setPermission(permission, true));
        return new ConsoleCommandAuthority(attachment);
    }

    static List<String> permissionNames(List<Permission> permissions) {
        return permissions.stream()
                .map(Permission::getName)
                .filter(name -> name.startsWith("enthusiastaff."))
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public void close() {
        attachment.remove();
    }
}
