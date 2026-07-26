package net.enthusia.staff.domain.alt;

public enum AltRelationshipState {
    SAME_NETWORK,
    LOW_CONFIDENCE,
    SEMI_CONFIDENT,
    CONFIDENT,
    VERY_CONFIDENT,
    CONFIRMED_ALT,
    APPROVED_ALT,
    SHARED_HOUSEHOLD,
    NOT_RELATED;

    public boolean inheritsAutomatically() {
        return this == CONFIDENT || this == VERY_CONFIDENT || this == CONFIRMED_ALT;
    }

    public boolean preventsAutomaticInheritance() {
        return this == APPROVED_ALT || this == SHARED_HOUSEHOLD || this == NOT_RELATED;
    }
}
