package net.enthusia.staff.domain.ports;

import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentResult;
import net.enthusia.staff.domain.escalation.PriorOffense;

public interface ModerationStore {
    List<PriorOffense> relatedHistory(UUID targetId, String family);

    PunishmentResult.Accepted createPunishment(PunishmentPlan plan);
}
