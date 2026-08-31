package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Small fixed-window fence protecting expensive Discord/database reads behind authenticated requests. */
final class ModerationReadApiRateLimiter {
    private final int capacity;
    private final Duration window;
    private final Clock clock;
    private Instant windowStart;
    private int used;

    ModerationReadApiRateLimiter(int capacity, Duration window) {
        this(capacity, window, Clock.systemUTC());
    }

    ModerationReadApiRateLimiter(int capacity, Duration window, Clock clock) {
        if (capacity < 1 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("rate limit must have positive capacity and window");
        }
        this.capacity = capacity;
        this.window = window;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.windowStart = clock.instant();
    }

    synchronized boolean tryAcquire() {
        Instant now = clock.instant();
        if (!now.isBefore(windowStart.plus(window))) {
            windowStart = now;
            used = 0;
        }
        if (used >= capacity) {
            return false;
        }
        used++;
        return true;
    }
}
