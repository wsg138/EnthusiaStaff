package net.enthusia.staff.domain.discord;

/** Sanitized target-DM delivery state. */
public enum DiscordDeliveryOutcome {
    NOT_ATTEMPTED,
    DELIVERED,
    FAILED_RETRYABLE,
    FAILED_TERMINAL;

    public boolean failed() {
        return this == FAILED_RETRYABLE || this == FAILED_TERMINAL;
    }

    public boolean retryable() {
        return this == FAILED_RETRYABLE;
    }
}
