package net.enthusia.staff.domain.moderation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;

public record AccountLinkAudit(
        String operationKey,
        Actor actor,
        AccountLinkAuditAction action,
        Optional<DiscordUserId> discordUserId,
        Optional<UUID> minecraftPlayerId,
        String detail,
        Instant createdAt
) {
    public AccountLinkAudit {
        if (operationKey == null || operationKey.isBlank() || operationKey.length() > 128
                || actor == null || action == null || discordUserId == null
                || minecraftPlayerId == null || detail == null || detail.isBlank()
                || detail.length() > 512 || createdAt == null) {
            throw new IllegalArgumentException("account-link audit fields are invalid");
        }
    }
}
