# Inventory and Confiscation Safety

Inventory viewing, editing, confiscation and restoration can cause item loss or duplication when used incorrectly. This page explains staff procedure. For current implementation status, safety design and direct source-file links, use [[Staff Tools, Investigations, and Player-State Safety]].

> **Current implementation boundary:** Inventory/Ender viewing and ordinary editing use durable revision/checksum journals, exact dirty-slot live writes, bounded snapshots, queued offline patches and login recovery guards. Representative multi-backend, Java/Bedrock and large/private-data staging remains separate validation work. Item confiscation/restoration is a separate package and must not be inferred from ordinary inventory editing.

## Quick navigation

- Staff mode and freeze: [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]]
- Incident response: [[Incident Playbooks]]
- Recovery procedure: [[Recovery and Troubleshooting]]
- Feature status and source files: [[Staff Tools, Investigations, and Player-State Safety]]
- Technical trace: [[Developer Code Guide]]

## Choose the correct workflow

| Need | Correct approach |
| --- | --- |
| View a player's inventory | `/invsee <player>` |
| View a player's Ender chest | `/endersee <player>` |
| Make an ordinary authorized correction | Use the supported editor with edit permission |
| Remove prohibited items or case evidence | Use case-linked confiscation |
| Remove currency | Use the economy confiscation workflow |
| Return confiscated items | Use the original case restoration workflow |
| Resolve an uncertain or failed operation | Stop and contact an Admin or Founder |

Viewing an inventory does not grant permission to edit or confiscate it.

## Before opening player state

Confirm:

- there is a legitimate report, case or support reason;
- the correct player and inventory scope are selected;
- another staff member is not already handling the same operation;
- you know whether the task is viewing, ordinary correction or confiscation;
- the player is not rapidly logging in/out or switching backends;
- the system is not reporting a lock, recovery or storage failure.

Do not browse inventories out of curiosity or use staff-observed information for normal gameplay.

## Rank limits

Helpers receive read-only inventory/Ender access for legitimate investigations. A Mod or above with `enthusiastaff.inventory.edit` is required for ordinary edits.

Do not work around a rank limit by moving items through another container, account or command. See [[Roles and Permissions|Rank-Authority]].

## Online viewing and editing

The online player's live inventory is authoritative. Avoid broad edits while the player is moving items, using containers or switching servers.

For an authorized correction:

1. tell staff what is changing and why;
2. change only the intended slots/items;
3. recheck the live inventory;
4. record the result in the report or case when the situation requires it.

The runtime records a durable observation, compares the expected revision/checksum, and applies only the changed logical slots on the target player's scheduler. It does not close a stale full-inventory clone and overwrite unrelated slots. Storage, armor, offhand and Ender slots are represented in one logical image; nested item data remains inside the serialized item stack.

The complete serialized inventory image is bounded before it reaches the journal. Oversized or malformed snapshots fail closed rather than being accepted as recovery evidence.

Multiple viewers share the target's live session and are reconciled from the authoritative live inventory. A stale edit is rejected instead of forcing its old mirror over newer state.

## Offline players and queued patches

Offline editing is allowed only when network-wide presence says the player is offline and the latest authoritative observation belongs to the current inventory scope/backend. The editor works from that observation and closing a changed offline view prepares a durable queued patch; it does **not** guess at or directly rewrite a player `.dat` file.

The durable patch carries its expected revision/checksum, replacement checksum/snapshot, changed-slot list, operation identity and fencing token. Replaying the same APPLYING operation while it still owns the exact lease is idempotent and keeps the same fence; another operation cannot take that live lease.

On the next login, the pending patch is discovered before normal player interaction. While verification/application is unresolved, inventory clicks/opening, drop/pickup, held-slot/hand swaps, item consumption, durability/mending, damage/resurrection, entity interaction and Paper pick/equipment-swap paths are blocked. The target's current checksum must match either the expected before state or the already-applied replacement. Anything else is quarantined instead of being overwritten.

Never manually edit player data files or reconstruct an inventory from memory or a screenshot. If the player connects, disconnects or switches servers during a sensitive action, stop and let the durable recovery workflow decide the outcome.

## Shulkers, bundles and nested items

Nested contents are preserved as part of each serialized `ItemStack`. Ordinary inventory editing still addresses a top-level logical slot; it does not reinterpret nested contents as unrelated loose items.

Confiscation-specific exact item paths/fingerprints remain a separate destructive workflow. Do not assume two visually identical stacks are the same selected item. If a fingerprint changed, reselect instead of forcing the original operation.

## Item confiscation

Use confiscation for prohibited items, duplicated assets, case evidence or another case-authorized removal.

Do not manually delete items through ordinary editing and call that confiscation. The supported workflow is intended to:

- bind the removal to one case;
- acquire the relevant asset lock;
- save exact item paths and a durable before snapshot;
- revalidate before deletion;
- prevent duplicate replay;
- verify terminal state;
- allow authorized idempotent restoration.

Before confirming, check the selected items, quantities and nested paths. After commit, verify the player state and the case record.

Confiscation/restoration completion is outside the ordinary inventory-editing package and remains separately validated.

## Economy confiscation

Currency removal must use the supported EnthusiaCurrency moderation API. Never issue raw provider database updates or a second manual withdrawal when the result is uncertain.

Confirm:

- amount and reason;
- which balance sources are included;
- the authorizing case;
- exact before and expected after totals;
- the final verified result.

If totals do not match, stop. Do not try to “balance it out” with another deposit or withdrawal.

## Restoration

Restoration uses the original case-linked snapshot. Do not duplicate the saved items manually or recreate assets from memory.

The operation must remain idempotent and safe when the player's current inventory has changed. An uncertain fit should enter visible recovery/quarantine rather than silently dropping or duplicating items.

## Stop immediately when

- the wrong player or scope may be open;
- an item moved or changed after selection;
- the player disconnected or switched servers;
- another viewer/operation is active;
- the command reports conflict, pending operation, lease or recovery state;
- items/currency may have been removed twice;
- final inventory or balance cannot be verified;
- retry safety is uncertain.

Record the report/case, time, selected assets, visible before/after state and exact error. Do not delete rows, edit recovery state or release locks manually.

## Validation boundary

Automated unit and MariaDB/Testcontainers evidence can prove codec bounds, journal fencing/idempotency and deterministic state transitions. Hosted build/runtime-JAR checks and safe restart/staging gates prove only the exact revision they execute. Representative multi-backend contention, Java/Bedrock usability and large/private inventories remain later private validation; none of those should be inferred from a command opening successfully.

## Related pages

- [[Staff Handbook]]
- [[Helper Guide]]
- [[Staff Quick Start|Moderator-Quick-Start]]
- [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]]
- [[Incident Playbooks]]
- [[Recovery and Troubleshooting]]
- [[Staff Tools, Investigations, and Player-State Safety]]
