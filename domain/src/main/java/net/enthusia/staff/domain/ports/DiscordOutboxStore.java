package net.enthusia.staff.domain.ports;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.discord.DiscordChannelStatus;
import net.enthusia.staff.domain.discord.DiscordFailureOutcome;
import net.enthusia.staff.domain.discord.DiscordOutboxMessage;

public interface DiscordOutboxStore {
    List<DiscordOutboxMessage> claimDue(String owner, int limit, Duration lease, Instant now);

    boolean delivered(UUID messageId, String owner, Instant now);

    DiscordFailureOutcome failed(
            UUID messageId,
            String owner,
            String errorCode,
            Instant availableAt,
            Instant now,
            int maximumAttempts,
            int failureThreshold,
            Duration circuitDuration
    );

    void deferWithoutAttempt(UUID messageId, String owner, Instant availableAt);

    List<DiscordChannelStatus> channelStatuses();

    int retryDestination(String destination, Instant now, int maximumMessages);
}
