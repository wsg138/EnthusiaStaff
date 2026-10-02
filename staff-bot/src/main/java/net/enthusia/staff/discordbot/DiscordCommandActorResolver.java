package net.enthusia.staff.discordbot;

import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.MainMinecraftAccount;
import net.enthusia.staff.domain.moderation.ModerationSubject;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore;

/** Resolves Discord actors exclusively through the canonical Enthusia identity/linking store. */
public final class DiscordCommandActorResolver {
    private final SubjectLookup subjects;

    public DiscordCommandActorResolver(DiscordModerationPersistenceStore store) {
        if (store == null) {
            throw new IllegalArgumentException("Discord moderation persistence store is required");
        }
        this.subjects = store::subjectForDiscord;
    }

    DiscordCommandActorResolver(SubjectLookup subjects) {
        if (subjects == null) {
            throw new IllegalArgumentException("subject lookup is required");
        }
        this.subjects = subjects;
    }

    public Optional<ResolvedActor> resolve(DiscordUserId discordUserId) {
        if (discordUserId == null) {
            return Optional.empty();
        }
        return subjects.subjectForDiscord(discordUserId)
                .filter(versioned -> containsDiscordActor(versioned.subject(), discordUserId))
                .flatMap(this::resolvedMain);
    }

    private Optional<ResolvedActor> resolvedMain(DiscordModerationPersistenceStore.VersionedSubject versioned) {
        ModerationSubject subject = versioned.subject();
        Optional<MainMinecraftAccount> main = subject.mainMinecraftAccount();
        if (!subject.linkedAcrossPlatforms() || main.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedActor(subject.subjectId(), main.orElseThrow().playerId()));
    }

    private static boolean containsDiscordActor(ModerationSubject subject, DiscordUserId discordUserId) {
        return subject.discordUserIds().contains(discordUserId);
    }

    @FunctionalInterface
    interface SubjectLookup {
        Optional<DiscordModerationPersistenceStore.VersionedSubject> subjectForDiscord(DiscordUserId userId);
    }

    public record ResolvedActor(ModerationSubjectId subjectId, UUID actorPlayerId) {
        public ResolvedActor {
            if (subjectId == null || actorPlayerId == null) {
                throw new IllegalArgumentException("resolved command actor is invalid");
            }
        }
    }
}
