package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.inventory.InventoryRecoveryResult;

@FunctionalInterface
public interface InventoryRecoveryStore {
    InventoryRecoveryResult requeueCaseAssets(CaseId caseId, UUID actorId, Instant now);
}
