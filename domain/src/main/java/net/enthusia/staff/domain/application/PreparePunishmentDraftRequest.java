package net.enthusia.staff.domain.application;

import java.util.UUID;
import net.enthusia.staff.common.Checks;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.casefile.CaseVisibility;

public record PreparePunishmentDraftRequest(
        UUID targetId,
        Actor actor,
        String reasonId,
        String internalExplanation,
        CaseVisibility visibility,
        String commandName
) {
    public PreparePunishmentDraftRequest {
        if (targetId == null || actor == null || visibility == null) {
            throw new IllegalArgumentException("punishment draft request fields must be present");
        }
        reasonId = Checks.nonBlank(reasonId, "reasonId", 96);
        if (internalExplanation == null) {
            throw new IllegalArgumentException("internalExplanation must be present");
        }
        internalExplanation = internalExplanation.trim();
        if (internalExplanation.length() > 4_000) {
            throw new IllegalArgumentException("internalExplanation exceeds 4000 characters");
        }
        commandName = Checks.nonBlank(commandName, "commandName", 32);
    }
}
