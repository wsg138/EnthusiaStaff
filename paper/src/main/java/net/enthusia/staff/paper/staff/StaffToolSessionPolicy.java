package net.enthusia.staff.paper.staff;

import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.Material;

/** Pure validation for binding a hotbar item to one live staff session, owner, tool and slot. */
final class StaffToolSessionPolicy {
    enum Status {
        VALID("The staff tool is valid."),
        UNKNOWN_TOOL("Unknown staff tool tag; the item is stale or spoofed."),
        STALE_SESSION("That staff tool belongs to a stale or inactive staff session."),
        RANK_UNAVAILABLE("That staff tool is not available to your current explicit staff rank."),
        OWNER_MISMATCH("That staff tool belongs to another staff player and cannot be used."),
        SESSION_MISMATCH("That staff tool belongs to an older staff session and cannot be used."),
        SLOT_MISMATCH("That staff tool is outside its protected hotbar slot and cannot be used."),
        MATERIAL_MISMATCH("That staff tool does not match the server-issued tool definition.");

        private final String message;

        Status(String message) {
            this.message = message;
        }

        String message() {
            return message;
        }
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
        Status sessionStatus = validateSession(playerId, activeSessionToken, tool, ownerTag, sessionTag, rank);
        return sessionStatus == Status.VALID
                ? validateItem(heldSlot, tool, actualMaterial)
                : sessionStatus;
    }

    private static Status validateSession(
            UUID playerId,
            String activeSessionToken,
            StaffToolDefinition tool,
            String ownerTag,
            String sessionTag,
            StaffRank rank
    ) {
        if (activeSessionToken == null || activeSessionToken.isBlank()) {
            return Status.STALE_SESSION;
        }
        if (!tool.availableFor(rank)) {
            return Status.RANK_UNAVAILABLE;
        }
        if (!playerId.toString().equals(ownerTag)) {
            return Status.OWNER_MISMATCH;
        }
        return activeSessionToken.equals(sessionTag) ? Status.VALID : Status.SESSION_MISMATCH;
    }

    private static Status validateItem(int heldSlot, StaffToolDefinition tool, Material actualMaterial) {
        if (heldSlot != tool.slot()) {
            return Status.SLOT_MISMATCH;
        }
        return actualMaterial == tool.material() ? Status.VALID : Status.MATERIAL_MISMATCH;
    }
}
