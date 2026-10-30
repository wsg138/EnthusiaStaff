package net.enthusia.staff.discordbot;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Privacy-safe, monotonic disconnect/recovery measurements for each independent
 * JDA gateway. This class never consumes frame reasons, Discord tokens, guild
 * identifiers, gateway URLs, or message contents.
 */
final class DiscordGatewayReconnectDiagnostics {
    private final LongSupplier monotonicNanos;
    private boolean disconnected;
    private long startedNanos;
    private long episodeEvents;
    private long totalEvents;

    DiscordGatewayReconnectDiagnostics(LongSupplier monotonicNanos) {
        this.monotonicNanos = Objects.requireNonNull(monotonicNanos, "monotonicNanos");
    }

    synchronized Disconnection disconnected() {
        long now = monotonicNanos.getAsLong();
        if (!disconnected) {
            disconnected = true;
            startedNanos = now;
            episodeEvents = 0;
        }
        episodeEvents++;
        totalEvents++;
        return new Disconnection(totalEvents, episodeEvents);
    }

    synchronized Optional<Restoration> restored() {
        if (!disconnected) {
            // The initial Ready event is not a reconnect, and should not
            // produce a misleading zero-duration recovery message.
            return Optional.empty();
        }
        long elapsedNanos = monotonicNanos.getAsLong() - startedNanos;
        long millis = TimeUnit.NANOSECONDS.toMillis(Math.max(0L, elapsedNanos));
        Restoration result = new Restoration(millis, episodeEvents, totalEvents);
        disconnected = false;
        episodeEvents = 0;
        return Optional.of(result);
    }

    record Disconnection(long totalEvents, long episodeEvents) { }

    record Restoration(long downtimeMillis, long episodeEvents, long totalEvents) { }
}
