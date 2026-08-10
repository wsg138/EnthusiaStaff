package net.enthusia.staff.velocity;

import java.util.List;
import java.util.Objects;

record VelocityConfigurationReloadResult(
        Outcome outcome,
        String message,
        List<String> details
) {
    VelocityConfigurationReloadResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(message, "message");
        details = details == null ? List.of() : List.copyOf(details);
    }

    boolean successful() {
        return outcome == Outcome.NO_CHANGES || outcome == Outcome.APPLIED;
    }

    enum Outcome {
        NO_CHANGES,
        APPLIED,
        VALIDATION_FAILED,
        RESTART_REQUIRED,
        UNAVAILABLE,
        SHUTTING_DOWN
    }
}
