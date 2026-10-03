package net.enthusia.staff.paper.staff;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
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
    private Optional<Binding> binding = Optional.empty();
    private boolean combatPluginPresent;

    public CombatStatusAdapter(JavaPlugin owner) {
        this.owner = owner;
        refresh();
    }

    public void refresh() {
        binding = Optional.empty();
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
                binding = Optional.of(new Binding(manager, discovered, discovered.getParameterTypes()[0]));
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            owner.getLogger().warning("CombatLogX is present but its combat-query API is unavailable");
        }
    }

    public Status status(Player player) {
        if (!combatPluginPresent) {
            return Status.CLEAR;
        }
        if (binding.isEmpty()) {
            refresh();
            if (binding.isEmpty()) {
                return Status.UNAVAILABLE;
            }
        }
        Binding active = binding.orElseThrow();
        try {
            Object argument = active.parameterType() == UUID.class ? player.getUniqueId() : player;
            Object result = active.method().invoke(active.receiver(), argument);
            return result instanceof Boolean tagged
                    ? tagged ? Status.TAGGED : Status.CLEAR
                    : Status.UNAVAILABLE;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            return Status.UNAVAILABLE;
        }
    }

    public boolean availableWhenRequired() {
        return !combatPluginPresent || binding.isPresent();
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

    private record Binding(Object receiver, Method method, Class<?> parameterType) {
    }

    /**
     * Removes any CombatLogX combat tag from the player. Used to grant staff
     * combat bypass while on duty or vanished — no staff member should ever be
     * put in combat in those states.
     *
     * @return true if the untag was attempted (CombatLogX present and API available)
     */
    public boolean untag(Player player) {
        if (!combatPluginPresent) {
            return false;
        }
        try {
            Method managerAccessor = owner.getServer().getPluginManager()
                    .getPlugin("CombatLogX").getClass().getMethod("getCombatManager");
            Object manager = managerAccessor.invoke(
                    owner.getServer().getPluginManager().getPlugin("CombatLogX"));
            if (manager == null) {
                return false;
            }
            Method untag = findUntagMethod(manager.getClass());
            if (untag == null) {
                return false;
            }
            Class<?>[] params = untag.getParameterTypes();
            if (params.length == 1) {
                untag.invoke(manager, player);
            } else if (params.length == 2) {
                // Second param is typically UntagReason; pass null and let CombatLogX default it,
                // or find the enum constant. Try null first.
                try {
                    untag.invoke(manager, player, (Object) null);
                } catch (InvocationTargetException | IllegalArgumentException e) {
                    // Null reason rejected; try to find a suitable enum constant.
                    Object reason = findUntagReason(params[1]);
                    if (reason == null) {
                        return false;
                    }
                    untag.invoke(manager, player, reason);
                }
            } else {
                return false;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            owner.getLogger().fine("CombatLogX untag failed: " + exception.getMessage());
            return false;
        }
    }

    private static Method findUntagMethod(Class<?> managerType) {
        for (String name : new String[]{"untag", "remove", "untagPlayer"}) {
            for (Method method : managerType.getMethods()) {
                if (!method.getName().equals(name)) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length >= 1 && params.length <= 2
                        && params[0].isAssignableFrom(Player.class)) {
                    return method;
                }
            }
        }
        return null;
    }

    private static Object findUntagReason(Class<?> reasonType) {
        if (!reasonType.isEnum()) {
            return null;
        }
        for (Object constant : reasonType.getEnumConstants()) {
            String name = ((Enum<?>) constant).name();
            if (name.equalsIgnoreCase("PLUGIN") || name.equalsIgnoreCase("CUSTOM")
                    || name.equalsIgnoreCase("UNKNOWN")) {
                return constant;
            }
        }
        // Fall back to the first constant.
        Object[] constants = reasonType.getEnumConstants();
        return constants.length > 0 ? constants[0] : null;
    }
}
