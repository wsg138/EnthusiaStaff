package net.enthusia.staff.domain.casefile;

import java.time.Duration;
import java.time.Instant;

public final class CaseInactivityPolicy {
    public static final Duration DEFAULT_INACTIVITY = Duration.ofDays(30);

    private final Duration inactivity;

    public CaseInactivityPolicy() {
        this(DEFAULT_INACTIVITY);
    }

    public CaseInactivityPolicy(Duration inactivity) {
        if (inactivity == null || inactivity.isZero() || inactivity.isNegative()) {
            throw new IllegalArgumentException("case inactivity duration must be positive");
        }
        this.inactivity = inactivity;
    }

    public Duration inactivity() {
        return inactivity;
    }

    public Instant closesAt(Instant lastActivityAt) {
        if (lastActivityAt == null) {
            throw new IllegalArgumentException("lastActivityAt must be present");
        }
        return lastActivityAt.plus(inactivity);
    }

    public boolean shouldClose(CaseState state, Instant lastActivityAt, Instant now) {
        if (state == null || lastActivityAt == null || now == null) {
            throw new IllegalArgumentException("case state, last activity, and current time must be present");
        }
        return state == CaseState.OPEN && !now.isBefore(closesAt(lastActivityAt));
    }
}
