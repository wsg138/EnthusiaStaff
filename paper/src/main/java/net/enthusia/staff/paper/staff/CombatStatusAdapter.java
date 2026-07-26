package net.enthusia.staff.paper.staff;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class CombatStatusAdapter {
    public enum Status {
        CLEAR,
        TAGGED,
        UNAVAILABLE
    }

    private final JavaPlugin owner;
    private Object receiver;
    private Method method;
    private Class<?> parameterType;
    private boolean combatPluginPresent;

    public CombatStatusAdapter(JavaPlugin owner) {
        this.owner = owner;
        refresh();
    }

    public void refresh() {
        receiver = null;
        method = null;
        parameterType = null;
        Plugin plugin = owner.getServer().getPluginManager().getPlugin("CombatLogX");
        combatPluginPresent = plugin != null && plugin.isEnabled();
        if (!combatPluginPresent) {
            return;
        }
        try {
            Method managerAccessor = plugin.getClass().getMethod("getCombatManager");
            Object manager = managerAccessor.invoke(plugin);
            if (manager == null) {
                return;
            }
            Method discovered = findMethod(manager.getClass());
            if (discovered != null) {
                receiver = manager;
                method = discovered;
                parameterType = discovered.getParameterTypes()[0];
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            owner.getLogger().warning("CombatLogX is present but its combat-query API is unavailable");
        }
    }

    public Status status(Player player) {
        if (!combatPluginPresent) {
            return Status.CLEAR;
        }
        if (method == null || receiver == null) {
            refresh();
            if (method == null || receiver == null) {
                return Status.UNAVAILABLE;
            }
        }
        try {
            Object argument = parameterType == UUID.class ? player.getUniqueId() : player;
            Object result = method.invoke(receiver, argument);
            return result instanceof Boolean tagged
                    ? tagged ? Status.TAGGED : Status.CLEAR
                    : Status.UNAVAILABLE;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            return Status.UNAVAILABLE;
        }
    }

    public boolean availableWhenRequired() {
        return !combatPluginPresent || method != null;
    }

    private static Method findMethod(Class<?> managerType) {
        for (String name : new String[]{"isInCombat", "isTagged"}) {
            for (Class<?> parameter : new Class<?>[]{Player.class, Entity.class, UUID.class}) {
                try {
                    return managerType.getMethod(name, parameter);
                } catch (NoSuchMethodException ignored) {
                    // Continue through the explicitly supported signatures.
                }
            }
        }
        return null;
    }
}
