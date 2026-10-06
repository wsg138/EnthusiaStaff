package net.enthusia.staff.domain.policyv2;

public record HistoryWindow(double minimumInclusive, Double maximumExclusive) {
    public HistoryWindow {
        if (!Double.isFinite(minimumInclusive) || minimumInclusive < 0.0) {
            throw new IllegalArgumentException("history minimum must be finite and non-negative");
        }
        if (maximumExclusive != null
                && (!Double.isFinite(maximumExclusive) || maximumExclusive <= minimumInclusive)) {
            throw new IllegalArgumentException("history maximum must be greater than the minimum");
        }
    }

    public static HistoryWindow atLeast(double minimumInclusive) {
        return new HistoryWindow(minimumInclusive, null);
    }

    public boolean matches(double contribution) {
        return contribution >= minimumInclusive
                && (maximumExclusive == null || contribution < maximumExclusive);
    }

    public boolean overlaps(HistoryWindow other) {
        double thisMax = maximumExclusive == null ? Double.POSITIVE_INFINITY : maximumExclusive;
        double otherMax = other.maximumExclusive == null ? Double.POSITIVE_INFINITY : other.maximumExclusive;
        return minimumInclusive < otherMax && other.minimumInclusive < thisMax;
    }
}
