package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedLink;

/** Durable one-use account-link code state and private link-history reads. */
public interface AccountLinkingStore {
    enum Direction {
        DISCORD_TO_MINECRAFT,
        MINECRAFT_TO_DISCORD
    }

    void issueFromDiscord(
            DiscordUserId discordUserId,
            String codeHash,
            Instant createdAt,
            Instant expiresAt
    );

    void issueFromMinecraft(
            UUID minecraftPlayerId,
            String codeHash,
            Instant createdAt,
            Instant expiresAt
    );

    /** Resolves only a currently usable Minecraft-origin code for online-control verification. */
    UUID minecraftInitiatorForCode(String codeHash, Instant now);

    /** Atomically validates/consumes a Discord-origin code and commits the authoritative link. */
    VersionedLink completeFromMinecraft(
            String codeHash,
            UUID minecraftPlayerId,
            String operationKey,
            Instant now
    );

    /** Atomically validates/consumes a Minecraft-origin code and commits the authoritative link. */
    VersionedLink completeFromDiscord(
            String codeHash,
            DiscordUserId discordUserId,
            String operationKey,
            Instant now
    );

    List<VersionedLink> historyForMinecraft(UUID minecraftPlayerId);

    List<VersionedLink> historyForDiscord(DiscordUserId discordUserId);
}
