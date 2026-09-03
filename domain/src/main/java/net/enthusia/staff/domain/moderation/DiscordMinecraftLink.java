package net.enthusia.staff.domain.moderation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record DiscordMinecraftLink(
        DiscordUserId discordUserId,
        UUID minecraftPlayerId,
        Instant linkedAt,
        Optional<Instant> unlinkedAt,
        DiscordMinecraftLinkSource source
) {
    public DiscordMinecraftLink {
        if (discordUserId == null || minecraftPlayerId == null || linkedAt == null
                || unlinkedAt == null || source == null) {
            throw new IllegalArgumentException("Discord/Minecraft link fields must be present");
        }
        if (unlinkedAt.isPresent() && unlinkedAt.orElseThrow().isBefore(linkedAt)) {
            throw new IllegalArgumentException("unlink time must not precede link time");
        }
    }

    public boolean active() {
        return unlinkedAt.isEmpty();
    }
}
