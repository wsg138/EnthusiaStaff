package net.enthusia.staff.authoritybridge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Read-only access to DiscordSRV's inspected AccountLinkManager snapshot API. */
final class DiscordSrvTransitionLinkSource {
    static final int MAX_LINKS = 5_000;
    private final Object discordSrvPlugin;

    private DiscordSrvTransitionLinkSource(Object discordSrvPlugin) {
        this.discordSrvPlugin = discordSrvPlugin;
    }

    static Optional<DiscordSrvTransitionLinkSource> discover(JavaPlugin owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner plugin must be present");
        }
        Plugin plugin = owner.getServer().getPluginManager().getPlugin("DiscordSRV");
        return fromPlugin(plugin);
    }

    static Optional<DiscordSrvTransitionLinkSource> fromPlugin(Object plugin) {
        return plugin == null ? Optional.empty() : Optional.of(new DiscordSrvTransitionLinkSource(plugin));
    }

    Map<String, UUID> snapshotLinks() {
        Object manager = invoke(discordSrvPlugin, "getAccountLinkManager");
        Object value = invoke(manager, "getLinkedAccounts");
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalStateException("DiscordSRV linked-account snapshot is unavailable");
        }
        if (raw.size() > MAX_LINKS) {
            throw new IllegalStateException("DiscordSRV linked-account snapshot exceeds the safe bound");
        }
        Map<String, UUID> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String discordId = normalizedDiscordId(entry.getKey());
            UUID playerId = entry.getValue() instanceof UUID uuid ? uuid : null;
            if (discordId == null || playerId == null) {
                throw new IllegalStateException("DiscordSRV linked-account snapshot contains invalid data");
            }
            result.put(discordId, playerId);
        }
        return Map.copyOf(result);
    }

    private static String normalizedDiscordId(Object value) {
        if (!(value instanceof String text)) {
            return null;
        }
        try {
            return new DiscordUserId(text).value();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Object invoke(Object target, String methodName) {
        if (target == null) {
            throw new IllegalStateException("DiscordSRV account-link API is unavailable");
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("DiscordSRV account-link API is unavailable", exception);
        }
    }
}
