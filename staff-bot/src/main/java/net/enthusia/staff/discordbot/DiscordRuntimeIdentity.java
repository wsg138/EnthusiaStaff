package net.enthusia.staff.discordbot;

import java.util.Objects;
import java.util.Set;

/**
 * Public Discord runtime identity facts used to fail closed before command handling is enabled.
 */
public record DiscordRuntimeIdentity(
        long applicationId,
        boolean botPublic,
        Set<Long> guildIds,
        boolean stagingChannelPresent,
        boolean stagingChannelOperational) {

    public DiscordRuntimeIdentity {
        guildIds = Set.copyOf(Objects.requireNonNull(guildIds, "guildIds"));
    }
}
