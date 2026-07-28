package net.enthusia.staff.domain.application;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import net.enthusia.staff.common.Checks;
import net.enthusia.staff.domain.casefile.CaseVisibility;

public record PunishmentDraft(
        UUID draftId,
        UUID actorId,
        UUID targetId,
        String reasonId,
        String internalExplanation,
        CaseVisibility visibility,
        String commandName,
        PunishmentExpectation expectation,
        Instant createdAt,
        Instant expiresAt
) {
    private static final Pattern COMMAND_NAME = Pattern.compile("[a-z][a-z0-9-]{0,31}");

    public PunishmentDraft {
        if (draftId == null || actorId == null || targetId == null || visibility == null
                || expectation == null || createdAt == null || expiresAt == null) {
            throw new IllegalArgumentException("punishment draft fields must be present");
        }
        reasonId = Checks.nonBlank(reasonId, "reasonId", 96);
        if (internalExplanation == null) {
            throw new IllegalArgumentException("internalExplanation must be present");
        }
        internalExplanation = internalExplanation.trim();
        if (internalExplanation.length() > 4_000) {
            throw new IllegalArgumentException("internalExplanation exceeds 4000 characters");
        }
        commandName = Checks.nonBlank(commandName, "commandName", 32).toLowerCase(java.util.Locale.ROOT);
        if (!COMMAND_NAME.matcher(commandName).matches()) {
            throw new IllegalArgumentException("commandName is invalid");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("punishment draft expiration must follow creation");
        }
    }

    public boolean expiredAt(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("current time must be present");
        }
        return !expiresAt.isAfter(now);
    }
}
