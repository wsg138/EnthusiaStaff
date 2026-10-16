package net.enthusia.staff.domain.guided;

/**
 * Versioned, immutable guidance thresholds for evaluating factual incident answers.
 * Profiles are calibration inputs, not permission to bypass configured sanctions.
 * The prototype is NOT owner approved and must not be enabled in production.
 */
public record GuidedSeverityProfile(
        String version,
        int baseImpact,
        int sustainedWeight,
        int largeScaleWeight,
        int ignoredWarningWeight,
        int targetedWeight,
        int persistenceWeight,
        int moderateThreshold,
        int severeThreshold,
        double conservativeFraction,
        double balancedFraction,
        double strongerFraction,
        double impactDurationWeight
) {
    private static final GuidedSeverityProfile PROTOTYPE = new GuidedSeverityProfile(
            "prototype-20261013-unapproved", 10, 15, 30, 10, 15, 10,
            25, 45, 0.25, 0.50, 0.75, 0.0);

    public GuidedSeverityProfile {
        if (version == null || !version.matches("[a-z][a-z0-9.-]{0,63}")) {
            throw new IllegalArgumentException("Severity profile needs a stable version ID");
        }
        if (!validWeight(baseImpact) || !validWeight(sustainedWeight)
                || !validWeight(largeScaleWeight) || !validWeight(ignoredWarningWeight)
                || !validWeight(targetedWeight) || !validWeight(persistenceWeight)) {
            throw new IllegalArgumentException("Severity weights must be between 0 and 100");
        }
        if (largeScaleWeight < sustainedWeight) {
            throw new IllegalArgumentException("Large-scale incidents cannot have less weight");
        }
        if (moderateThreshold <= baseImpact || severeThreshold <= moderateThreshold
                || severeThreshold > 600) {
            throw new IllegalArgumentException("Severity thresholds must be ordered and bounded");
        }
        if (!validFraction(impactDurationWeight) || impactDurationWeight > 0.5) {
            throw new IllegalArgumentException("Impact duration weighting must remain within [0,0.5]");
        }
        if (!validFraction(conservativeFraction) || !validFraction(balancedFraction)
                || !validFraction(strongerFraction)
                || conservativeFraction > balancedFraction
                || balancedFraction > strongerFraction) {
            throw new IllegalArgumentException("Duration fractions must be ordered within [0,1]");
        }
    }

    private static boolean validWeight(int value) {
        return value >= 0 && value <= 100;
    }

    private static boolean validFraction(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }

    /** Backward-compatible historical prototype only; not an approved sanction policy. */
    public static GuidedSeverityProfile prototype() {
        return PROTOTYPE;
    }

    public int stepOffset(int incidentImpact) {
        if (incidentImpact < 0) {
            throw new IllegalArgumentException("Incident impact must not be negative");
        }
        return incidentImpact >= severeThreshold ? 2
                : incidentImpact >= moderateThreshold ? 1 : 0;
    }

    public double durationFraction(int staffPreference) {
        return switch (staffPreference) {
            case -1 -> conservativeFraction;
            case 0 -> balancedFraction;
            case 1 -> strongerFraction;
            default -> throw new IllegalArgumentException("Unsupported staff preference");
        };
    }

    /**
     * Opt-in test calibration only. With impactDurationWeight=0 the historical
     * preference-only behavior is exactly preserved.
     *
     * A score is normalized within its current offset tier. Crossing a tier
     * boundary still uses the configured policy ladder and transition guards.
     */
    public double durationFraction(int staffPreference, int impact) {
        double preference = durationFraction(staffPreference);
        if (impactDurationWeight == 0) {
            return preference;
        }
        int offset = stepOffset(impact);
        int tierStart = switch (offset) {
            case 0 -> baseImpact;
            case 1 -> moderateThreshold;
            default -> severeThreshold;
        };
        int tierWidth = switch (offset) {
            case 0 -> moderateThreshold - baseImpact;
            default -> severeThreshold - moderateThreshold;
        };
        double normalized = Math.clamp((double) (impact - tierStart) / tierWidth, 0.0, 1.0);
        return (1.0 - impactDurationWeight) * preference
                + impactDurationWeight * normalized;
    }
}
