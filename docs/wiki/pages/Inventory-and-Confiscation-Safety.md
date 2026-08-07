# Inventory and Confiscation Safety

Inventory viewing, editing, confiscation and restoration can cause item loss or duplication when used incorrectly. This page explains staff procedure. For current implementation status, safety design and direct source-file links, use [[Staff Tools, Investigations, and Player-State Safety]].

> **Current deployment:** Important persistence and rollback foundations exist, but complete concurrent-viewer, offline-owner, provider and recovery behavior still requires live staging. A command opening successfully does not prove that every destructive path is production-ready.

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

Helpers are intended to receive read-only inventory/Ender access for legitimate investigations. They should ask a Mod or above when assets need to be changed, confiscated or restored.

Do not work around a rank limit by moving items through another container, account or command. See [[Roles and Permissions|Rank-Authority]].

## Online viewing and editing

The online player's live inventory is authoritative. Avoid broad edits while the player is moving items, using containers or switching servers.

For an authorized correction:

1. tell staff what is changing and why;
2. save the relevant before state when practical;
3. change only the intended slots/items;
4. recheck the live inventory;
5. record the result in the report or case.

The intended implementation uses exact dirty-slot updates and synchronized viewers rather than saving a stale full inventory clone.

Stop if changes do not appear or another viewer/operation conflicts. Reopening and repeating can overwrite newer state.

## Offline players and queued patches

Offline editing is safe only when all of these can be proven:

- the player is offline network-wide;
- the owning backend/scope is known;
- an exclusive lease is held;
- no save is active;
- the revision is current;
- a durable before snapshot exists.

When ownership or timing is uncertain, the correct behavior is a queued patch applied before player interaction on login—not a guessed direct file edit.

Never manually edit player data files or reconstruct an inventory from memory or a screenshot. If the player connects, disconnects or switches servers during a sensitive action, stop and let the recovery workflow decide the outcome.

## Shulkers, bundles and nested items

Nested items are identified by exact paths and fingerprints. Reopen and verify a container before confirming a destructive action.

Do not assume two visually identical stacks are the same selected item. If a fingerprint changed, reselect instead of forcing the original operation.

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

## Technical status

Current merged-main states, primary managers/stores and remaining online/offline, confiscation, economy and recovery work are listed in [[Staff Tools, Investigations, and Player-State Safety]].

## Related pages

- [[Staff Handbook]]
- [[Helper Guide]]
- [[Staff Quick Start|Moderator-Quick-Start]]
- [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]]
- [[Incident Playbooks]]
- [[Recovery and Troubleshooting]]
- [[Staff Tools, Investigations, and Player-State Safety]]