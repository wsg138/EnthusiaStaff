package net.enthusia.staff.domain.sanction;

public record SanctionActionLimits(
        int minimumReasonLength,
        int maximumReasonLength,
        boolean allowPermanentReduction
) {
    public SanctionActionLimits {
        if (minimumReasonLength < 1 || maximumReasonLength < minimumReasonLength
                || maximumReasonLength > 512) {
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
