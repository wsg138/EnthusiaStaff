package net.enthusia.staff.domain.discord;

import java.time.Instant;
import java.util.Optional;

public record DiscordFailureOutcome(boolean deadLettered, boolean circuitOpened, Optional<Instant> openUntil) {
    public DiscordFailureOutcome {
        if (openUntil == null) {
            throw new IllegalArgumentException("openUntil must be present as an Optional");
        }
    }
}
