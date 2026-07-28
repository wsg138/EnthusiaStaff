package net.enthusia.staff.domain.discord;

import java.time.Instant;
import java.util.Optional;

public record DiscordChannelStatus(
        String destination,
        int consecutiveFailures,
        Optional<Instant> openUntil,
        Optional<String> lastErrorCode,
        Optional<Instant> lastSuccessAt,
        long pendingMessages,
        long deadLetterMessages
) {
    public DiscordChannelStatus {
        if (destination == null || destination.isBlank() || consecutiveFailures < 0 || openUntil == null
                || lastErrorCode == null || lastSuccessAt == null || pendingMessages < 0 || deadLetterMessages < 0) {
            throw new IllegalArgumentException("Discord channel status fields are invalid");
        }
    }

    public boolean circuitOpen(Instant now) {
        return openUntil.filter(now::isBefore).isPresent();
    }
}
