package net.enthusia.staff.domain.alt;

public enum AltRelationshipState {
    SAME_NETWORK(0.25),
    LOW_CONFIDENCE(0.20),
    SEMI_CONFIDENT(0.50),
    CONFIDENT(0.75),
    VERY_CONFIDENT(0.90),
    CONFIRMED_ALT(1.00),
    APPROVED_ALT(1.00),
    SHARED_HOUSEHOLD(0.75),
    NOT_RELATED(0.00);

    private final double confidence;

    AltRelationshipState(double confidence) {
        this.confidence = confidence;
    }

    /**
     * A configured confidence category is a policy grade, not a calibrated
     * statistical probability that the accounts belong to the same person.
     */
    public double confidence() {
        return confidence;
    }

    public boolean inheritsAutomatically() {
        return !preventsAutomaticInheritance()
                && confidence >= AltInheritancePolicy.AUTOMATIC_THRESHOLD;
    }

    public boolean preventsAutomaticInheritance() {
        return this == APPROVED_ALT || this == SHARED_HOUSEHOLD || this == NOT_RELATED;
    }
}
