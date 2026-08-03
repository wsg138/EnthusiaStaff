package net.enthusia.staff.domain.escalation;

/**
 * Immutable decay behavior captured from the policy that created a prior offense.
 *
 * <p>Legacy history without a stored value remains {@link #UNKNOWN}; unknown history is not
 * decayed because reconstructing a past policy from current configuration would invent history.</p>
 */
public enum DecayEligibility {
    ELIGIBLE(true),
    INELIGIBLE(false),
    UNKNOWN(false);

    private final boolean decayPermitted;

    DecayEligibility(boolean decayPermitted) {
        this.decayPermitted = decayPermitted;
    }

    public boolean permitsDecay() {
        return decayPermitted;
    }
}
