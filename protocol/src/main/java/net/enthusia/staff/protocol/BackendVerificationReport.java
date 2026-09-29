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
    public BackendVerificationReport(
            String backendId,
            String operationalMode,
            boolean storageReady,
            boolean channelConnected,
            Map<String, Check> integrations,
            Map<String, String> issues
    ) {
        if (backendId == null || backendId.isBlank()) {
            throw new IllegalArgumentException("backendId is required");
        }
        if (operationalMode == null || operationalMode.isBlank()) {
            throw new IllegalArgumentException("operationalMode is required");
        }
        this.backendId = backendId;
        this.operationalMode = operationalMode;
        this.storageReady = storageReady;
        this.channelConnected = channelConnected;
        this.integrations = integrations == null ? Map.of() : Map.copyOf(integrations);
        this.issues = issues == null ? Map.of() : Map.copyOf(issues);
    }

    public enum State {
        PASS,
        WARNING,
        DISABLED,
        CRITICAL
    }

    public record Check(State state, String detail) {
        public Check(State state, String detail) {
            this.state = Objects.requireNonNull(state, "state");
            this.detail = detail == null ? "" : detail;
        }
    }
}
