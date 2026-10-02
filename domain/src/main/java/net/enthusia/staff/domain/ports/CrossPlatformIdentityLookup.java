package net.enthusia.staff.domain.ports;

import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;

/** Minimal linked-identity read boundary needed by D08 final confirmation. */
public interface CrossPlatformIdentityLookup {
    Optional<ModerationSubjectId> subjectForMinecraft(UUID playerId);

    Optional<ModerationSubjectId> subjectForDiscord(DiscordUserId userId);
}
