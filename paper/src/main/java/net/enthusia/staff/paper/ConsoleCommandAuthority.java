package net.enthusia.staff.paper;

import java.util.List;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

/** Grants console only this plugin's declared permission nodes so executors can apply SYSTEM semantics. */
final class ConsoleCommandAuthority implements AutoCloseable, Listener {
    private final JavaPlugin plugin;
    private final PermissionAttachment attachment;
    private boolean closed;

    private ConsoleCommandAuthority(JavaPlugin plugin, PermissionAttachment attachment) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.attachment = Objects.requireNonNull(attachment, "attachment");
    }

    static ConsoleCommandAuthority install(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        PermissionAttachment attachment = plugin.getServer().getConsoleSender().addAttachment(plugin);
        permissionNames(plugin.getPluginMeta().getPermissions())
                .forEach(permission -> attachment.setPermission(permission, true));
        ConsoleCommandAuthority authority = new ConsoleCommandAuthority(plugin, attachment);
        plugin.getServer().getPluginManager().registerEvents(authority, plugin);
        return authority;
    }

    static List<String> permissionNames(List<Permission> permissions) {
        return permissions.stream()
                .map(Permission::getName)
                .filter(name -> name.startsWith("enthusiastaff."))
                .distinct()
                .sorted()
                .toList();
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            close();
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        attachment.remove();
        HandlerList.unregisterAll(this);
    }
}
