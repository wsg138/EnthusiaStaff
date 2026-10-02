package net.enthusia.staff.domain.network;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.sanction.SanctionType;

/** Online effects of a committed case, carried by the authenticated durable network outbox. */
public record PunishmentCommitNotification(CaseId caseId, UUID targetId, String publicReason,
        Instant issuedAt, List<SanctionType> types) {
    public PunishmentCommitNotification {
        if (caseId == null || targetId == null || publicReason == null || publicReason.isBlank()
                || issuedAt == null || types == null || types.isEmpty()) {
            throw new IllegalArgumentException("committed punishment notification is incomplete");
        }
        types = List.copyOf(types);
    }
}
