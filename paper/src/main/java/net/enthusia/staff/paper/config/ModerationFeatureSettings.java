package net.enthusia.staff.paper.config;

import java.time.ZoneId;
import java.util.Objects;
import net.enthusia.staff.domain.sanction.SanctionActionLimits;

public record ModerationFeatureSettings(
        int historyPageSize,
        boolean includeRequestEvents,
        boolean includeAppealEvents,
        ZoneId historyTimezone,
        SanctionActionLimits sanctionActionLimits
) {
    public ModerationFeatureSettings {
        if (historyPageSize < 1 || historyPageSize > 100) {
            throw new IllegalArgumentException("history page size must be between 1 and 100");
        }
        Objects.requireNonNull(historyTimezone, "historyTimezone");
        Objects.requireNonNull(sanctionActionLimits, "sanctionActionLimits");
    }

    public static ModerationFeatureSettings defaults() {
        return new ModerationFeatureSettings(
                8,
                true,
                true,
                ZoneId.of("UTC"),
                SanctionActionLimits.defaults()
        );
    }
}
