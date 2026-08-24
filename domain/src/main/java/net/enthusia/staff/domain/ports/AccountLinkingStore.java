package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedLink;

/** Durable one-use account-link code state and private link-history reads. */
public interface AccountLinkingStore {
    enum Direction {
        DISCORD_TO_MINECRAFT,
        MINECRAFT_TO_DISCORD
    }

    record CodeClaim(
            UUID codeId,
            Direction direction,
            Optional<DiscordUserId> discordInitiator,
            Optional<UUID> minecraftInitiator,
            Instant expiresAt,
            String operationKey,
            boolean consumed
    ) {
        public CodeClaim {
            if (codeId == null || direction == null || discordInitiator == null
                    || minecraftInitiator == null || expiresAt == null
                    || operationKey == null || operationKey.isBlank()) {
                throw new IllegalArgumentException("link-code claim fields must be present");
            }
            boolean discord = discordInitiator.isPresent();
            boolean minecraft = minecraftInitiator.isPresent();
            if (discord == minecraft
                    || (direction == Direction.DISCORD_TO_MINECRAFT) != discord) {
                throw new IllegalArgumentException("link-code initiator must match its direction");
            }
        }
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

    CodeClaim claim(
            String codeHash,
            Direction expectedDirection,
            String operationKey,
            Instant now
    );

    void consume(UUID codeId, String operationKey, Instant consumedAt);

    void release(UUID codeId, String operationKey, Instant now);

    List<VersionedLink> historyForMinecraft(UUID minecraftPlayerId);

    List<VersionedLink> historyForDiscord(DiscordUserId discordUserId);
}
