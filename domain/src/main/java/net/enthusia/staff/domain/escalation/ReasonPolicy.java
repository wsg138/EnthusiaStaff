package net.enthusia.staff.domain.escalation;

import java.util.List;
import java.util.regex.Pattern;
import net.enthusia.staff.common.Checks;
import net.enthusia.staff.domain.auth.StaffRank;

public record ReasonPolicy(
        String id,
        String family,
        String publicReason,
        int severity,
        boolean decayEnabled,
        List<PunishmentStep> steps,
        List<String> examples,
        boolean publicByDefault,
        boolean reportable,
        boolean confiscationAllowed,
        StaffRank requiredRank,
        boolean automaticDetectionAllowed,
        AltInheritanceMode altInheritance
) {
    private static final Pattern ID = Pattern.compile("[a-z0-9]+(?:[.-][a-z0-9]+)*");

    public ReasonPolicy {
        id = Checks.nonBlank(id, "id", 96);
        family = Checks.nonBlank(family, "family", 64);
        publicReason = Checks.nonBlank(publicReason, "publicReason", 160);
        if (!ID.matcher(id).matches() || !ID.matcher(family).matches()) {
            throw new IllegalArgumentException("reason and family IDs must be stable lowercase identifiers");
        }
        if (severity < 0 || severity > 100) {
            throw new IllegalArgumentException("severity must be between 0 and 100");
        }
        steps = List.copyOf(Checks.nonEmpty(steps, "steps"));
        examples = List.copyOf(examples == null ? List.of() : examples);
        if (requiredRank == null || altInheritance == null) {
            throw new IllegalArgumentException("requiredRank and altInheritance must be present");
        }
        if (requiredRank == StaffRank.DEVELOPER || requiredRank == StaffRank.SYSTEM) {
            throw new IllegalArgumentException("configured punishments require MOD, ADMIN, or FOUNDER authority");
        }
        for (int index = 0; index < steps.size(); index++) {
            if (steps.get(index).ordinal() != index) {
                throw new IllegalArgumentException("step ordinals must be contiguous and zero based");
            }
        }
    }

    public ReasonPolicy(
            String id,
            String family,
            String publicReason,
            int severity,
            boolean decayEnabled,
            List<PunishmentStep> steps
    ) {
        this(id, family, publicReason, severity, decayEnabled, steps, List.of(), true, true,
                false, StaffRank.MOD, false, AltInheritanceMode.ACTIVE_SANCTIONS);
    }

    public PunishmentStep stepAt(int ordinal) {
        return steps.get(Math.min(Math.max(ordinal, 0), steps.size() - 1));
    }
}
