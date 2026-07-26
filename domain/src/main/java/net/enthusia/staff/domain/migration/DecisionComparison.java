package net.enthusia.staff.domain.migration;

public record DecisionComparison(long compared, long mismatched) {
    public DecisionComparison {
        if (compared < 0 || mismatched < 0 || mismatched > compared) {
            throw new IllegalArgumentException("invalid comparison counts");
        }
    }

    public boolean matches() {
        return mismatched == 0;
    }
}
