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
        SLOT_MISMATCH("Staff tools can only be used from your hotbar."),
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
        return validate(playerId, activeSessionToken, heldSlot, tool,
                new ItemContext(actualMaterial, tool == null ? -1 : tool.slot()), ownerTag, sessionTag, rank);
    }

    static Status validate(UUID playerId, String activeSessionToken, int heldSlot, StaffToolDefinition tool,
            ItemContext item, String ownerTag, String sessionTag, StaffRank rank) {
        if (tool == null) {
            return Status.UNKNOWN_TOOL;
        }
        Status sessionStatus = validateSession(playerId, activeSessionToken, tool, ownerTag, sessionTag, rank);
        return sessionStatus == Status.VALID
                ? validateItem(heldSlot, tool, item.actualMaterial(), item.expectedSlot())
                : sessionStatus;
    }

    record ItemContext(Material actualMaterial, int expectedSlot) { }

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

    private static Status validateItem(int heldSlot, StaffToolDefinition tool, Material actualMaterial, int expectedSlot) {
        if (heldSlot < 0 || heldSlot >= 9) {
            return Status.SLOT_MISMATCH;
        }
        // Player-owned tools may be rearranged within the inventory. PDC owner
        // and per-entry session binding still authorize every tool invocation.
        return actualMaterial == tool.material() ? Status.VALID : Status.MATERIAL_MISMATCH;
    }
}
