package net.enthusia.staff.paper.api;

import java.util.List;
import java.util.UUID;

public interface PunishmentQueryService {
    List<ActiveSanction> activeSanctions(UUID playerId);

    record ActiveSanction(String caseId, String type, Long expirationEpochMillis) {
    }
}
