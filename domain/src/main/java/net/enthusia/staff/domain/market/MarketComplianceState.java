package net.enthusia.staff.domain.market;

public enum MarketComplianceState {
    PREPARING,
    PREPARED,
    MODERATION_HOLD,
    RESTORED,
    RELEASED,
    BLACKLIST_ACTIVE,
    BLACKLIST_REMOVED,
    REJECTED,
    CONFLICT,
    QUARANTINED;

    public boolean terminal() {
        return switch (this) {
            case RESTORED, RELEASED, BLACKLIST_REMOVED, REJECTED, CONFLICT -> true;
            default -> false;
        };
    }
}
