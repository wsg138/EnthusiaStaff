package net.enthusia.staff.paper.config.reload;

import java.util.List;
import java.util.Objects;

public record ConfigurationReloadResult(
        Outcome outcome,
        String message,
        List<String> details,
        boolean reasonPoliciesReloaded
) {
    public ConfigurationReloadResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(message, "message");
        details = details == null ? List.of() : List.copyOf(details);
    }

    public boolean successful() {
        return switch (outcome) {
            case NO_CHANGES, APPLIED, WAITING_FOR_STORAGE -> true;
            case VALIDATION_FAILED, RESTART_REQUIRED, RESTORED, UNAVAILABLE,
                    APPLY_FAILED, SHUTTING_DOWN -> false;
        };
    }

    public enum Outcome {
        NO_CHANGES,
        APPLIED,
        WAITING_FOR_STORAGE,
        VALIDATION_FAILED,
        RESTART_REQUIRED,
        RESTORED,
        UNAVAILABLE,
        APPLY_FAILED,
        SHUTTING_DOWN
    }
}
