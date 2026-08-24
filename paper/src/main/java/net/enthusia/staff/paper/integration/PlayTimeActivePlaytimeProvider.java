package net.enthusia.staff.paper.integration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.logging.Level;
import net.enthusia.staff.domain.application.ActivePlaytimeProvider;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Optional adapter over PlayTimePlugin's public PlaytimeService#getLifetime contract. */
public final class PlayTimeActivePlaytimeProvider implements ActivePlaytimeProvider {
    private final Object service;
    private final Method getLifetime;

    private PlayTimeActivePlaytimeProvider(Object service, Method getLifetime) {
        this.service = service;
        this.getLifetime = getLifetime;
    }

    public static ActivePlaytimeProvider discover(JavaPlugin owner) {
        Plugin provider = owner.getServer().getPluginManager().getPlugin("PlayTimePlugin");
        ActivePlaytimeProvider resolved = fromPlugin(provider);
        if (provider != null && resolved instanceof MissingProvider) {
            owner.getLogger().warning("PlayTimePlugin is present but its public PlaytimeService API is unavailable; automatic main-account selection will preserve the current main.");
        }
        return resolved;
    }

    static ActivePlaytimeProvider fromPlugin(Object providerPlugin) {
        if (providerPlugin == null) {
            return MissingProvider.INSTANCE;
        }
        try {
            Method serviceMethod = providerPlugin.getClass().getMethod("getPlaytimeService");
            Object service = serviceMethod.invoke(providerPlugin);
            if (service == null) {
                return MissingProvider.INSTANCE;
            }
            Method getLifetime = service.getClass().getMethod("getLifetime", UUID.class);
            return new PlayTimeActivePlaytimeProvider(service, getLifetime);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return MissingProvider.INSTANCE;
        }
    }

    @Override
    public OptionalLong lifetimeActiveMinutes(UUID minecraftPlayerId) {
        if (minecraftPlayerId == null) {
            return OptionalLong.empty();
        }
        try {
            Object result = getLifetime.invoke(service, minecraftPlayerId);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) {
                return OptionalLong.empty();
            }
            Object snapshot = optional.orElseThrow();
            Field activeMinutes = snapshot.getClass().getField("activeMinutes");
            Object value = activeMinutes.get(snapshot);
            if (!(value instanceof Number number)) {
                return OptionalLong.empty();
            }
            long minutes = number.longValue();
            return minutes < 0L ? OptionalLong.empty() : OptionalLong.of(minutes);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return OptionalLong.empty();
        }
    }

    private enum MissingProvider implements ActivePlaytimeProvider {
        INSTANCE;

        @Override
        public OptionalLong lifetimeActiveMinutes(UUID minecraftPlayerId) {
            return OptionalLong.empty();
        }
    }
}
