package net.enthusia.staff.domain.policyv2;

import java.time.Duration;

public record DecayPolicy(
        Mode mode,
        Duration halfLife,
        Duration patternHalfLife,
        double repeatHalfLifeIncreasePerPrior,
        double maximumHalfLifeMultiplier
) {
    public enum Mode { EXPONENTIAL, NON_DECAYING }

    public DecayPolicy {
        if (mode == null) {
            throw new IllegalArgumentException("decay mode must be present");
        }
        if (mode == Mode.NON_DECAYING) {
            requireNonDecayingShape(
                    halfLife,
                    patternHalfLife,
                    repeatHalfLifeIncreasePerPrior,
                    maximumHalfLifeMultiplier
            );
        } else {
            if (patternHalfLife == null) {
                // Backward compatibility for snapshots/configurations written before
                // fading pattern memory existed.
                patternHalfLife = halfLife;
            }
            requireExponentialShape(
                    halfLife,
                    patternHalfLife,
                    repeatHalfLifeIncreasePerPrior,
                    maximumHalfLifeMultiplier
            );
        }
    }

    /**
     * Backward-compatible exponential policy. Existing callers get fading
     * pattern memory using the direct half-life until a separate pattern
     * half-life is configured.
     */
    public static DecayPolicy exponential(Duration halfLife, double repeatIncrease, double maximumMultiplier) {
        return exponential(halfLife, halfLife, repeatIncrease, maximumMultiplier);
    }

    public static DecayPolicy exponential(
            Duration halfLife,
            Duration patternHalfLife,
            double repeatIncrease,
            double maximumMultiplier
    ) {
        return new DecayPolicy(
                Mode.EXPONENTIAL,
                halfLife,
                patternHalfLife,
                repeatIncrease,
                maximumMultiplier
        );
    }

    public static DecayPolicy nonDecaying() {
        return new DecayPolicy(Mode.NON_DECAYING, null, null, 0.0, 1.0);
    }

    public double halfLifeMultiplier(double patternPersistence) {
        if (!Double.isFinite(patternPersistence) || patternPersistence < 0.0) {
            throw new IllegalArgumentException("pattern persistence must be finite and non-negative");
        }
        return Math.min(
                maximumHalfLifeMultiplier,
                1.0 + repeatHalfLifeIncreasePerPrior * patternPersistence
        );
    }

    private static void requireNonDecayingShape(
            Duration halfLife,
            Duration patternHalfLife,
            double increase,
            double maximum
    ) {
        if (halfLife != null || patternHalfLife != null || increase != 0.0 || maximum != 1.0) {
            throw new IllegalArgumentException("non-decaying policy cannot define decay parameters");
        }
    }

    private static void requireExponentialShape(
            Duration halfLife,
            Duration patternHalfLife,
            double increase,
            double maximum
    ) {
        requirePositiveDuration(halfLife, "exponential decay requires a positive half-life");
        requirePositiveDuration(patternHalfLife, "exponential decay requires a positive pattern half-life");
        if (!Double.isFinite(increase) || increase < 0.0 || !Double.isFinite(maximum) || maximum < 1.0) {
            throw new IllegalArgumentException("adaptive decay parameters are outside safe bounds");
        }
    }

    private static void requirePositiveDuration(Duration duration, String message) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(message);
        }
    }
}
