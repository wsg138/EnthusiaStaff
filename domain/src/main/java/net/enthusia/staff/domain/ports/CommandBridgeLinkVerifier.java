package net.enthusia.staff.domain.ports;

import java.util.UUID;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;

/** Revalidates that the Minecraft actor still belongs to the linked Enthusia subject. */
@FunctionalInterface
public interface CommandBridgeLinkVerifier {
    boolean isCurrentLink(ModerationSubjectId subjectId, UUID actorPlayerId);
}
