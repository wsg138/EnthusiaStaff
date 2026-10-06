package net.enthusia.staff.domain.policyv2;

import java.time.Duration;

public record DecayPolicy(
        Mode mode,
        Duration halfLife,
        double repeatHalfLifeIncreasePerPrior,
        double maximumHalfLifeMultiplier
) {
    public enum Mode { EXPONENTIAL, NON_DECAYING }

    public DecayPolicy {
        if (mode == null) {
            throw new IllegalArgumentException("decay mode must be present");
        }
        if (mode == Mode.NON_DECAYING) {
            requireNonDecayingShape(halfLife, repeatHalfLifeIncreasePerPrior, maximumHalfLifeMultiplier);
        } else {
            requireExponentialShape(halfLife, repeatHalfLifeIncreasePerPrior, maximumHalfLifeMultiplier);
        }
    }

    public static DecayPolicy exponential(Duration halfLife, double repeatIncrease, double maximumMultiplier) {
        return new DecayPolicy(Mode.EXPONENTIAL, halfLife, repeatIncrease, maximumMultiplier);
    }

    public static DecayPolicy nonDecaying() {
        return new DecayPolicy(Mode.NON_DECAYING, null, 0.0, 1.0);
    }

    public double halfLifeMultiplier(int priorRelatedCount) {
        if (priorRelatedCount < 0) {
            throw new IllegalArgumentException("prior related count must not be negative");
        }
        return Math.min(maximumHalfLifeMultiplier, 1.0 + repeatHalfLifeIncreasePerPrior * priorRelatedCount);
    }

    private static void requireNonDecayingShape(Duration halfLife, double increase, double maximum) {
        if (halfLife != null || increase != 0.0 || maximum != 1.0) {
            throw new IllegalArgumentException("non-decaying policy cannot define decay parameters");
        }
    }

    private static void requireExponentialShape(Duration halfLife, double increase, double maximum) {
        if (halfLife == null || halfLife.isZero() || halfLife.isNegative()) {
            throw new IllegalArgumentException("exponential decay requires a positive half-life");
        }
        if (!Double.isFinite(increase) || increase < 0.0 || !Double.isFinite(maximum) || maximum < 1.0) {
            throw new IllegalArgumentException("adaptive decay parameters are outside safe bounds");
        }
    }
}
