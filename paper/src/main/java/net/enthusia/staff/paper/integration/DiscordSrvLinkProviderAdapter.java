package net.enthusia.staff.paper.integration;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.application.DiscordSrvMigrationService;
import net.enthusia.staff.domain.application.DiscordSrvMigrationService.MirrorResult;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Adapter over DiscordSRV 1.30.5's public AccountLinkManager contract. */
public final class DiscordSrvLinkProviderAdapter implements DiscordSrvMigrationService.DiscordSrvLinkProvider {
    private final Object manager;
    private final Method getLinkedAccounts;
    private final Method link;
    private final Method unlinkDiscord;

    private DiscordSrvLinkProviderAdapter(
            Object manager,
            Method getLinkedAccounts,
            Method link,
            Method unlinkDiscord
    ) {
        this.manager = manager;
        this.getLinkedAccounts = getLinkedAccounts;
        this.link = link;
        this.unlinkDiscord = unlinkDiscord;
    }

    public static Optional<DiscordSrvLinkProviderAdapter> discover(JavaPlugin owner) {
        Plugin provider = owner.getServer().getPluginManager().getPlugin("DiscordSRV");
        Optional<DiscordSrvLinkProviderAdapter> resolved = fromPlugin(provider);
        if (provider != null && resolved.isEmpty()) {
            owner.getLogger().warning("DiscordSRV is present but its public AccountLinkManager API is unavailable; authoritative links remain enabled but legacy mirroring is unavailable.");
        }
        return resolved;
    }

    static Optional<DiscordSrvLinkProviderAdapter> fromPlugin(Object discordSrvPlugin) {
        if (discordSrvPlugin == null) {
            return Optional.empty();
        }
        try {
            Method getManager = discordSrvPlugin.getClass().getMethod("getAccountLinkManager");
            Object manager = getManager.invoke(discordSrvPlugin);
            if (manager == null) {
                return Optional.empty();
            }
            Method getLinkedAccounts = manager.getClass().getMethod("getLinkedAccounts");
            Method link = manager.getClass().getMethod("link", String.class, UUID.class);
            Method unlinkDiscord = manager.getClass().getMethod("unlink", String.class);
            return Optional.of(new DiscordSrvLinkProviderAdapter(manager, getLinkedAccounts, link, unlinkDiscord));
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return Optional.empty();
        }
    }

    @Override
    public Map<String, UUID> snapshotLinks() {
        try {
            Object value = getLinkedAccounts.invoke(manager);
            if (!(value instanceof Map<?, ?> raw)) {
                throw new IllegalStateException("DiscordSRV AccountLinkManager returned a non-map link snapshot");
            }
            Map<String, UUID> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (!(entry.getKey() instanceof String discordId) || !(entry.getValue() instanceof UUID uuid)) {
                    throw new IllegalStateException("DiscordSRV link snapshot contained an invalid entry");
                }
                copy.put(discordId, uuid);
            }
            return Map.copyOf(copy);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new IllegalStateException("DiscordSRV public link snapshot is unavailable", failure);
        }
    }

    @Override
    public MirrorResult mirrorMain(String discordUserId, UUID minecraftPlayerId) {
        try {
            Map<String, UUID> links = snapshotLinks();
            UUID currentForDiscord = links.get(discordUserId);
            if (minecraftPlayerId.equals(currentForDiscord)) {
                return MirrorResult.UNCHANGED;
            }
            String currentOwner = links.entrySet().stream()
                    .filter(entry -> minecraftPlayerId.equals(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
            if (currentOwner != null && !currentOwner.equals(discordUserId)) {
                return MirrorResult.CONFLICT;
            }
            if (currentForDiscord != null) {
                unlinkDiscord.invoke(manager, discordUserId);
            }
            link.invoke(manager, discordUserId, minecraftPlayerId);
            return MirrorResult.UPDATED;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return MirrorResult.UNAVAILABLE;
        }
    }

    @Override
    public MirrorResult clearMirror(String discordUserId) {
        try {
            if (!snapshotLinks().containsKey(discordUserId)) {
                return MirrorResult.UNCHANGED;
            }
            unlinkDiscord.invoke(manager, discordUserId);
            return MirrorResult.UPDATED;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return MirrorResult.UNAVAILABLE;
        }
    }
}
