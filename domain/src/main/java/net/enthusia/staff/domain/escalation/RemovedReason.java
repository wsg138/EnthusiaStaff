package net.enthusia.staff.domain.escalation;

import java.util.regex.Pattern;
import net.enthusia.staff.common.Checks;

/**
 * Read-only metadata for a stable reason identifier that is no longer selectable.
 */
public record RemovedReason(
        String id,
        String family,
        String publicReason
) {
    private static final Pattern ID = Pattern.compile("[a-z0-9]+(?:[.-][a-z0-9]+)*");

    public RemovedReason {
        id = Checks.nonBlank(id, "id", 96);
        family = Checks.nonBlank(family, "family", 64);
        publicReason = Checks.nonBlank(publicReason, "publicReason", 160);
        if (!ID.matcher(id).matches() || !ID.matcher(family).matches()) {
            throw new IllegalArgumentException("removed reason and family IDs must be stable lowercase identifiers");
        }
    }
}
