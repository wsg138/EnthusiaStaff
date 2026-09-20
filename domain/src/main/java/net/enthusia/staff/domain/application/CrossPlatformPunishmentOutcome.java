package net.enthusia.staff.domain.application;

import java.util.Set;
import net.enthusia.staff.domain.auth.DiscordEnforcementPrecondition;

public sealed interface CrossPlatformPunishmentOutcome {
    record Accepted(
            CrossPlatformPunishmentResult result,
            Set<DiscordEnforcementPrecondition> discordPreconditions
    ) implements CrossPlatformPunishmentOutcome {
        public Accepted {
            if (result == null || discordPreconditions == null) {
                throw new IllegalArgumentException("accepted cross-platform outcome fields must be present");
            }
            discordPreconditions = Set.copyOf(discordPreconditions);
        }
    }

    record Rejected(String code, String message) implements CrossPlatformPunishmentOutcome {
        public Rejected {
            if (code == null || code.isBlank() || message == null || message.isBlank()) {
                throw new IllegalArgumentException("cross-platform rejection fields must be present");
            }
        }
    }
}
