package net.enthusia.staff.paper.api;

import java.util.UUID;

public interface SanctionQueryService {
    boolean hasActiveMute(UUID playerId);

    boolean hasActiveBan(UUID playerId);
}
