package net.enthusia.staff.discordbot;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedSubject;

/**
 * Converts a linked Discord identity into current Enthusia staff authority.
 * Discord roles are intentionally absent from every input to this resolver.
 */
final class LinkedStaffActorResolver {
    static final class MissingStaffLinkException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        MissingStaffLinkException() {
            super("Discord invoker is not linked to a current staff identity");
        }
    }

    private record RankedIdentity(UUID playerId, StaffRank rank) {
    }

    private final Function<DiscordUserId, Optional<VersionedSubject>> invokerSubjects;
    private final StaffAuthorityClient authority;

    LinkedStaffActorResolver(StaffModerationReadService reads, StaffAuthorityClient authority) {
        this(userId -> reads.discordTarget(userId).subject(), authority);
    }

    LinkedStaffActorResolver(
            Function<DiscordUserId, Optional<VersionedSubject>> invokerSubjects,
            StaffAuthorityClient authority
    ) {
        if (invokerSubjects == null || authority == null) {
            throw new IllegalArgumentException("actor resolver dependencies must be present");
        }
        this.invokerSubjects = invokerSubjects;
        this.authority = authority;
    }

    Actor invoker(DiscordUserId discordUserId, String displayName) {
        VersionedSubject subject = invokerSubjects.apply(discordUserId)
                .orElseThrow(MissingStaffLinkException::new);
        RankedIdentity ranked = strongest(subject).orElseThrow(MissingStaffLinkException::new);
        return new Actor(ranked.playerId(), safeName(displayName, discordUserId.value()), ranked.rank());
    }

    Optional<Actor> targetStaff(StaffModerationReadService.Target target) {
        if (target.subject().isEmpty()) {
            return Optional.empty();
        }
        return strongest(target.subject().orElseThrow()).map(ranked -> new Actor(
                ranked.playerId(),
                ranked.playerId().toString(),
                ranked.rank()
        ));
    }

    private Optional<RankedIdentity> strongest(VersionedSubject subject) {
        List<UUID> ids = subject.subject().minecraftAccountIds().stream().sorted().toList();
        return ids.stream()
                .map(id -> authority.rank(id).map(rank -> new RankedIdentity(id, rank)))
                .flatMap(Optional::stream)
                .filter(value -> value.rank() != StaffRank.SYSTEM)
                .max(Comparator.comparingInt(value -> authorityLevel(value.rank())));
    }

    private static int authorityLevel(StaffRank rank) {
        return switch (rank) {
            case HELPER -> 10;
            case MOD, DEVELOPER -> 20;
            case ADMIN -> 30;
            case FOUNDER -> 40;
            case SYSTEM -> 0;
        };
    }

    private static String safeName(String displayName, String fallback) {
        return displayName == null || displayName.isBlank() ? fallback : displayName;
    }
}
