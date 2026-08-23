package net.enthusia.staff.domain.moderation;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class DiscordMinecraftLinkPolicy {
    private DiscordMinecraftLinkPolicy() {
    }

    public static void validateCurrentOwnership(Collection<DiscordMinecraftLink> links) {
        if (links == null) {
            throw new IllegalArgumentException("links must be present");
        }
        Map<UUID, DiscordUserId> currentOwners = new HashMap<>();
        Set<String> currentPairs = new HashSet<>();
        for (DiscordMinecraftLink link : links) {
            if (link == null) {
                throw new IllegalArgumentException("links must not contain null values");
            }
            if (!link.active()) {
                continue;
            }
            String pair = link.discordUserId().value() + ":" + link.minecraftPlayerId();
            if (!currentPairs.add(pair)) {
                throw new IllegalArgumentException("duplicate active Discord/Minecraft link");
            }
            DiscordUserId previous = currentOwners.putIfAbsent(link.minecraftPlayerId(), link.discordUserId());
            if (previous != null && !previous.equals(link.discordUserId())) {
                throw new IllegalArgumentException("a Minecraft account may have only one current Discord owner");
            }
        }
    }

    public static Set<UUID> currentMinecraftAccounts(
            DiscordUserId discordUserId,
            Collection<DiscordMinecraftLink> links
    ) {
        if (discordUserId == null) {
            throw new IllegalArgumentException("discordUserId must be present");
        }
        validateCurrentOwnership(links);
        return links.stream()
                .filter(DiscordMinecraftLink::active)
                .filter(link -> discordUserId.equals(link.discordUserId()))
                .map(DiscordMinecraftLink::minecraftPlayerId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static Optional<DiscordUserId> currentDiscordOwner(
            UUID minecraftPlayerId,
            Collection<DiscordMinecraftLink> links
    ) {
        if (minecraftPlayerId == null) {
            throw new IllegalArgumentException("minecraftPlayerId must be present");
        }
        validateCurrentOwnership(links);
        return links.stream()
                .filter(DiscordMinecraftLink::active)
                .filter(link -> minecraftPlayerId.equals(link.minecraftPlayerId()))
                .map(DiscordMinecraftLink::discordUserId)
                .findFirst();
    }
}
