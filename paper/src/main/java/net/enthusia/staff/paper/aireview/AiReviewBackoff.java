package net.enthusia.staff.paper.aireview;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

final class AiReviewBackoff {
    private final Duration initialDelay;
    private final Duration maximumDelay;

    private int consecutiveFailures;
    private Instant blockedUntil = Instant.MIN;

    AiReviewBackoff(Duration initialDelay, Duration maximumDelay) {
        this.initialDelay = positive(initialDelay, "initialDelay");
        this.maximumDelay = positive(maximumDelay, "maximumDelay");
        if (maximumDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("maximumDelay must be >= initialDelay");
        }
    }

    synchronized void success() {
        consecutiveFailures = 0;
        blockedUntil = Instant.MIN;
    }

    synchronized Duration failure(Instant now) {
        Objects.requireNonNull(now, "now");
        consecutiveFailures = Math.min(31, consecutiveFailures + 1);
        long multiplier = 1L << Math.min(20, consecutiveFailures - 1);
        long delayMillis;
        try {
            delayMillis = Math.multiplyExact(initialDelay.toMillis(), multiplier);
        } catch (ArithmeticException exception) {
            delayMillis = maximumDelay.toMillis();
        }
        delayMillis = Math.min(delayMillis, maximumDelay.toMillis());
        blockedUntil = now.plusMillis(delayMillis);
        return Duration.ofMillis(delayMillis);
    }

    synchronized Duration remaining(Instant now) {
        Objects.requireNonNull(now, "now");
        if (!now.isBefore(blockedUntil)) {
            return Duration.ZERO;
        }
        return Duration.between(now, blockedUntil);
    }

    synchronized int consecutiveFailures() {
        return consecutiveFailures;
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
