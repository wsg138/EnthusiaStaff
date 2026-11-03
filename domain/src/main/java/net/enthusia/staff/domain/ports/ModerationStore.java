package net.enthusia.staff.domain.ports;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentResult;
import net.enthusia.staff.domain.escalation.PriorOffense;

public interface ModerationStore {
    List<PriorOffense> relatedHistory(UUID targetId, String family);

    PunishmentResult.Accepted createPunishment(PunishmentPlan plan);

    /**
     * Read-only lookup for a case that already committed under the exact
     * idempotency key. Used to recover a saved draft after cleanup failure,
     * before changed history or policy can incorrectly reject its replay.
     * Stores without a durable case index may retain the conservative default.
     */
    default Optional<CaseId> committedCase(IdempotencyKey key) {
        return Optional.empty();
    }
}
