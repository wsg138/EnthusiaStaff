package net.enthusia.staff.paper.staff;

import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.Material;

/** Pure validation for binding a hotbar item to one live staff session, owner, tool and slot. */
final class StaffToolSessionPolicy {
    enum Status {
        VALID,
        UNKNOWN_TOOL,
        STALE_SESSION,
        RANK_UNAVAILABLE,
        OWNER_MISMATCH,
        SESSION_MISMATCH,
        SLOT_MISMATCH,
        MATERIAL_MISMATCH
    }

    private StaffToolSessionPolicy() {
    }

    static Status validate(
            UUID playerId,
            String activeSessionToken,
            int heldSlot,
            StaffToolDefinition tool,
            Material actualMaterial,
            String ownerTag,
            String sessionTag,
            StaffRank rank
    ) {
        if (tool == null) {
            return Status.UNKNOWN_TOOL;
        }
        if (activeSessionToken == null || activeSessionToken.isBlank()) {
            return Status.STALE_SESSION;
        }
        if (!tool.availableFor(rank)) {
            return Status.RANK_UNAVAILABLE;
        }
        if (!playerId.toString().equals(ownerTag)) {
            return Status.OWNER_MISMATCH;
        }
        if (!activeSessionToken.equals(sessionTag)) {
            return Status.SESSION_MISMATCH;
        }
        if (heldSlot != tool.slot()) {
            return Status.SLOT_MISMATCH;
        }
        if (actualMaterial != tool.material()) {
            return Status.MATERIAL_MISMATCH;
        }
        return Status.VALID;
    }
}
