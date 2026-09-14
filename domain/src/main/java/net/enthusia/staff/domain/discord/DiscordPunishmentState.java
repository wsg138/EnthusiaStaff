package net.enthusia.staff.domain.discord;

/** Durable lifecycle of a Discord-side punishment effect. */
public enum DiscordPunishmentState {
    PENDING_APPLY,
    RETRY_APPLY,
    APPLIED,
    COMPLETED,
    FAILED_APPLY,
    PENDING_REMOVE,
    RETRY_REMOVE,
    FAILED_REMOVE,
    ENDED,
    REVOKED,
    OVERTURNED,
    EXPIRED;

    public boolean terminal() {
        return switch (this) {
            case COMPLETED, FAILED_APPLY, FAILED_REMOVE, ENDED, REVOKED, OVERTURNED, EXPIRED -> true;
            default -> false;
        };
    }

    public boolean removalPending() {
        return this == PENDING_REMOVE || this == RETRY_REMOVE || this == FAILED_REMOVE;
    }
}
