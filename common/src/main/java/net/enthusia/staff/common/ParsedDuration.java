package net.enthusia.staff.common;

import java.time.Duration;
import java.util.Optional;

public record ParsedDuration(Optional<Duration> temporary) {
    public ParsedDuration {
        if (temporary == null) {
            throw new IllegalArgumentException("temporary must not be null");
        }
        temporary.ifPresent(value -> {
            if (value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException("temporary duration must be positive");
            }
        });
    }

    public static ParsedDuration permanent() {
        return new ParsedDuration(Optional.empty());
    }

    public static ParsedDuration temporary(Duration duration) {
        return new ParsedDuration(Optional.of(duration));
    }

    public boolean isPermanent() {
        return temporary.isEmpty();
    }
}
