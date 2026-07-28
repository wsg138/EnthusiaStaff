package net.enthusia.staff.paper.api;

import java.util.UUID;

public interface StaffVisibilityService {
    boolean isVanished(UUID playerId);

    boolean canSee(UUID viewerId, UUID targetId);
}
