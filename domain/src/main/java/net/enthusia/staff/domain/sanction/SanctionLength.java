package net.enthusia.staff.domain.sanction;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public record SanctionLength(Kind kind, Optional<Duration> temporary) {
    public enum Kind {
        INSTANT,
        TEMPORARY,
        PERMANENT
    }

    public SanctionLength {
        if (kind == null || temporary == null) {
            throw new IllegalArgumentException("kind and temporary must be present");
        }
        temporary.ifPresent(duration -> {
            if (duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException("sanction duration must be positive");
            }
        });
        if ((kind == Kind.TEMPORARY) != temporary.isPresent()) {
            throw new IllegalArgumentException("only a temporary sanction may have a duration");
        }
    }

    public static SanctionLength instant() {
        return new SanctionLength(Kind.INSTANT, Optional.empty());
    }

    public static SanctionLength permanent() {
        return new SanctionLength(Kind.PERMANENT, Optional.empty());
    }

    public static SanctionLength temporary(Duration duration) {
        return new SanctionLength(Kind.TEMPORARY, Optional.of(duration));
    }

    public boolean isInstant() {
        return kind == Kind.INSTANT;
    }

    public boolean isPermanent() {
        return kind == Kind.PERMANENT;
    }

    public Optional<Instant> expirationFrom(Instant issuedAt) {
        return temporary.map(issuedAt::plus);
    }
}
