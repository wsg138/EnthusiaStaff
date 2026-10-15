package net.enthusia.staff.paper.modcase;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore;

/**
 * Fresh reverse lookup against the existing authoritative Discord moderation store.
 * Subject membership alone is NOT proof of a current link. Imported/recovery links
 * do not establish verified ownership, and two matches are ambiguous and denied.
 */
public final class PaperCaseVerifiedIdentityLinks {
    private PaperCaseVerifiedIdentityLinks() { }

    public static Function<String, Optional<UUID>> uniqueCurrentVerified(
            Supplier<DiscordModerationPersistenceStore> currentStore) {
        Objects.requireNonNull(currentStore);
        return discordId -> {
            DiscordModerationPersistenceStore store = Objects.requireNonNull(currentStore.get());
            DiscordUserId user = new DiscordUserId(discordId);
            var subject = store.subjectForDiscord(user);
            if (subject.isEmpty()) {
                return Optional.empty();
            }
            var matches = subject.orElseThrow().subject().minecraftAccountIds().stream()
                    .filter(id -> currentLinkMatches(store, id, user)).limit(2).toList();
            return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
        };
    }

    public static BiPredicate<UUID, String> currentVerified(
            Supplier<DiscordModerationPersistenceStore> currentStore) {
        Objects.requireNonNull(currentStore);
        return (playerId, discordId) -> {
            if (playerId == null) {
                return false;
            }
            DiscordModerationPersistenceStore store = Objects.requireNonNull(currentStore.get());
            return currentLinkMatches(store, playerId, new DiscordUserId(discordId));
        };
    }

    private static boolean currentLinkMatches(
            DiscordModerationPersistenceStore store, UUID playerId, DiscordUserId discordId) {
        return store.currentLink(playerId).filter(current -> {
            var link = current.link();
            return link.active() && playerId.equals(link.minecraftPlayerId())
                    && discordId.equals(link.discordUserId())
                    && switch (link.source()) {
                        case DISCORD_CODE, MINECRAFT_CODE -> true;
                        default -> false;
                    };
        }).isPresent();
    }
}
