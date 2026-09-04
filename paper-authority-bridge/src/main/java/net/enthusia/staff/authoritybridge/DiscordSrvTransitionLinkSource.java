package net.enthusia.staff.authoritybridge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Read-only access to DiscordSRV's inspected AccountLinkManager snapshot API. */
final class DiscordSrvTransitionLinkSource {
    static final int MAX_LINKS = 5_000;
    private static final Pattern DISCORD_ID = Pattern.compile("[1-9][0-9]{0,19}");
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
            String discordId = entry.getKey() instanceof String text ? text : null;
            UUID playerId = entry.getValue() instanceof UUID uuid ? uuid : null;
            if (discordId == null || !DISCORD_ID.matcher(discordId).matches() || playerId == null) {
                throw new IllegalStateException("DiscordSRV linked-account snapshot contains invalid data");
            }
            result.put(discordId, playerId);
        }
        return Map.copyOf(result);
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
