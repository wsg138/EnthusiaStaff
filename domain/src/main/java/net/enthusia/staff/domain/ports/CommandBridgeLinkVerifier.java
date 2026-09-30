package net.enthusia.staff.domain.ports;

import java.util.UUID;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;

/** Revalidates that both signed identities still belong to the same canonical Enthusia subject. */
@FunctionalInterface
public interface CommandBridgeLinkVerifier {
    boolean isCurrentLink(
            ModerationSubjectId subjectId,
            DiscordUserId discordUserId,
            UUID actorPlayerId
    );
}
