package net.enthusia.staff.paper.commandbridge;

import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.MainMinecraftAccount;
import net.enthusia.staff.domain.moderation.ModerationSubject;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.CommandBridgeLinkVerifier;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore;

/** Re-reads canonical linking state on Paper so a stale Discord resolution cannot authorize execution. */
public final class CanonicalCommandBridgeLinkVerifier implements CommandBridgeLinkVerifier {
    private final SubjectLookup subjects;

    public CanonicalCommandBridgeLinkVerifier(DiscordModerationPersistenceStore store) {
        if (store == null) {
            throw new IllegalArgumentException("Discord moderation persistence store is required");
        }
        this.subjects = store::subjectForMinecraft;
    }

    CanonicalCommandBridgeLinkVerifier(SubjectLookup subjects) {
        if (subjects == null) {
            throw new IllegalArgumentException("subject lookup is required");
        }
        this.subjects = subjects;
    }

    @Override
    public boolean isCurrentLink(
            ModerationSubjectId subjectId,
            DiscordUserId discordUserId,
            UUID actorPlayerId
    ) {
        if (subjectId == null || discordUserId == null || actorPlayerId == null) {
            return false;
        }
        return subjects.subjectForMinecraft(actorPlayerId)
                .map(DiscordModerationPersistenceStore.VersionedSubject::subject)
                .filter(subject -> subject.subjectId().equals(subjectId))
                .filter(subject -> subject.discordUserIds().contains(discordUserId))
                .filter(ModerationSubject::linkedAcrossPlatforms)
                .flatMap(ModerationSubject::mainMinecraftAccount)
                .map(MainMinecraftAccount::playerId)
                .filter(actorPlayerId::equals)
                .isPresent();
    }

    @FunctionalInterface
    interface SubjectLookup {
        Optional<DiscordModerationPersistenceStore.VersionedSubject> subjectForMinecraft(UUID playerId);
    }
}
