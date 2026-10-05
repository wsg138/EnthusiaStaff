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
    private Optional<Binding> statusBinding = Optional.empty();
    private Optional<UntagBinding> untagBinding = Optional.empty();
    private boolean combatPluginPresent;

    public CombatStatusAdapter(JavaPlugin owner) {
        this.owner = owner;
        refresh();
    }

    public void refresh() {
        statusBinding = Optional.empty();
        untagBinding = Optional.empty();
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
            Method query = findStatusMethod(manager.getClass());
            if (query != null) {
                statusBinding = Optional.of(new Binding(manager, query, query.getParameterTypes()[0]));
            }
            Method untag = findUntagMethod(manager.getClass());
            if (untag != null) {
                untagBinding = Optional.of(new UntagBinding(manager, untag, untag.getParameterTypes()));
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            owner.getLogger().warning("CombatLogX is present but its combat API is unavailable");
        }
    }

    public Status status(Player player) {
        if (!combatPluginPresent) {
            return Status.CLEAR;
        }
        if (statusBinding.isEmpty()) {
            refresh();
            if (statusBinding.isEmpty()) {
                return Status.UNAVAILABLE;
            }
        }
        Binding active = statusBinding.orElseThrow();
        try {
            Object result = active.method().invoke(active.receiver(), playerArgument(player, active.parameterType()));
            return result instanceof Boolean tagged
                    ? tagged ? Status.TAGGED : Status.CLEAR
                    : Status.UNAVAILABLE;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            return Status.UNAVAILABLE;
        }
    }

    public boolean availableWhenRequired() {
        return !combatPluginPresent || statusBinding.isPresent();
    }

    /**
     * Best-effort removal of a CombatLogX tag. The integration stays reflection-based because
     * CombatLogX is a soft dependency and multiple live backends may expose slightly different
     * API signatures.
     */
    public boolean untag(Player player) {
        if (!combatPluginPresent) {
            return true;
        }
        if (untagBinding.isEmpty()) {
            refresh();
            if (untagBinding.isEmpty()) {
                return false;
            }
        }
        UntagBinding active = untagBinding.orElseThrow();
        try {
            Class<?>[] parameters = active.parameterTypes();
            Object playerArgument = playerArgument(player, parameters[0]);
            if (parameters.length == 1) {
                active.method().invoke(active.receiver(), playerArgument);
            } else {
                Object reason = untagReason(parameters[1]);
                if (reason == UnusableReason.INSTANCE) {
                    return false;
                }
                active.method().invoke(active.receiver(), playerArgument, reason);
            }
            return true;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            owner.getLogger().fine("CombatLogX untag failed: " + exception.getMessage());
            return false;
        }
    }

    private static Method findStatusMethod(Class<?> managerType) {
        for (String name : new String[]{"isInCombat", "isTagged"}) {
            for (Class<?> parameter : new Class<?>[]{Player.class, Entity.class, UUID.class}) {
                try {
                    return managerType.getMethod(name, parameter);
                } catch (NoSuchMethodException ignored) {
                    // Try the next supported signature.
                }
            }
        }
        return null;
    }

    private static Method findUntagMethod(Class<?> managerType) {
        for (String name : new String[]{"untag", "remove", "untagPlayer"}) {
            for (Method method : managerType.getMethods()) {
                if (!method.getName().equals(name)) {
                    continue;
                }
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length < 1 || parameters.length > 2) {
                    continue;
                }
                if (acceptsPlayer(parameters[0])) {
                    return method;
                }
            }
        }
        return null;
    }

    private static boolean acceptsPlayer(Class<?> type) {
        return type == UUID.class
                || type.isAssignableFrom(Player.class)
                || type.isAssignableFrom(Entity.class);
    }

    private static Object playerArgument(Player player, Class<?> parameterType) {
        return parameterType == UUID.class ? player.getUniqueId() : player;
    }

    private static Object untagReason(Class<?> reasonType) {
        if (!reasonType.isEnum()) {
            return reasonType.isPrimitive() ? UnusableReason.INSTANCE : null;
        }
        Object[] constants = reasonType.getEnumConstants();
        for (Object constant : constants) {
            String name = ((Enum<?>) constant).name();
            if (name.equalsIgnoreCase("EXPIRE")
                    || name.equalsIgnoreCase("PLUGIN")
                    || name.equalsIgnoreCase("CUSTOM")
                    || name.equalsIgnoreCase("UNKNOWN")
                    || name.equalsIgnoreCase("FORCE")) {
                return constant;
            }
        }
        return constants.length == 0 ? UnusableReason.INSTANCE : constants[0];
    }

    private record Binding(Object receiver, Method method, Class<?> parameterType) {
    }

    private record UntagBinding(Object receiver, Method method, Class<?>[] parameterTypes) {
        private UntagBinding {
            parameterTypes = parameterTypes.clone();
        }

        @Override
        public Class<?>[] parameterTypes() {
            return parameterTypes.clone();
        }
    }

    private enum UnusableReason {
        INSTANCE
    }
}
