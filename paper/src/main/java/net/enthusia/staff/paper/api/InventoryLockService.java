package net.enthusia.staff.paper.api;

import java.util.UUID;

public interface InventoryLockService {
    boolean isLocked(UUID playerId);
}
