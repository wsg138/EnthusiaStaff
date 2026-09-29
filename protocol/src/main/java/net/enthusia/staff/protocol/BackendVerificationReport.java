package net.enthusia.staff.protocol;

import java.util.Map;
import java.util.Objects;

/**
 * Sanitized, non-destructive backend verification snapshot exchanged over the
 * authenticated persistent channel.
 */
public record BackendVerificationReport(
        String backendId,
        String operationalMode,
        boolean storageReady,
        boolean channelConnected,
        Map<String, Check> integrations,
        Map<String, String> issues
) {
    public BackendVerificationReport {
        if (backendId == null || backendId.isBlank()) {
            throw new IllegalArgumentException("backendId is required");
        }
        if (operationalMode == null || operationalMode.isBlank()) {
            throw new IllegalArgumentException("operationalMode is required");
        }
        integrations = integrations == null ? Map.of() : Map.copyOf(integrations);
        issues = issues == null ? Map.of() : Map.copyOf(issues);
    }

    public enum State {
        PASS,
        WARNING,
        DISABLED,
        CRITICAL
    }

    public record Check(State state, String detail) {
        public Check {
            Objects.requireNonNull(state, "state");
            detail = detail == null ? "" : detail;
        }
    }
}
