package net.enthusia.staff.domain.moderation;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record ModerationSubject(
        ModerationSubjectId subjectId,
        Set<ModerationIdentity> identities,
        Optional<MainMinecraftAccount> mainMinecraftAccount
) {
    public ModerationSubject {
        if (subjectId == null || identities == null || mainMinecraftAccount == null) {
            throw new IllegalArgumentException("moderation subject fields must be present");
        }
        if (identities.isEmpty() || identities.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("moderation subject must contain at least one valid identity");
        }
        identities = Set.copyOf(identities);
        if (mainMinecraftAccount.isPresent()
                && !identities.contains(new MinecraftIdentityRef(mainMinecraftAccount.orElseThrow().playerId()))) {
            throw new IllegalArgumentException("main Minecraft account must belong to the moderation subject");
        }
    }

    public Set<UUID> minecraftAccountIds() {
        return identities.stream()
                .filter(MinecraftIdentityRef.class::isInstance)
                .map(MinecraftIdentityRef.class::cast)
                .map(MinecraftIdentityRef::playerId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<DiscordUserId> discordUserIds() {
        return identities.stream()
                .filter(DiscordIdentityRef.class::isInstance)
                .map(DiscordIdentityRef.class::cast)
                .map(DiscordIdentityRef::userId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean linkedAcrossPlatforms() {
        return !minecraftAccountIds().isEmpty() && !discordUserIds().isEmpty();
    }
}
