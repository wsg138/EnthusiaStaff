package net.enthusia.staff.domain.sanction;

public record SanctionActionLimits(
        int minimumReasonLength,
        int maximumReasonLength,
        boolean allowPermanentReduction
) {
    private static final int MAXIMUM_SUPPORTED_REASON_LENGTH = 2_000;

    public SanctionActionLimits {
        if (minimumReasonLength < 1 || maximumReasonLength < minimumReasonLength
                || maximumReasonLength > MAXIMUM_SUPPORTED_REASON_LENGTH) {
            throw new IllegalArgumentException("sanction action reason limits are invalid");
        }
    }

    public static SanctionActionLimits defaults() {
        return new SanctionActionLimits(3, 500, true);
    }

    public boolean accepts(String reason) {
        if (reason == null) {
            return false;
        }
        int length = reason.trim().length();
        return length >= minimumReasonLength && length <= maximumReasonLength;
    }
}
