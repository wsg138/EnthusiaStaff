# Staff Tools, Investigations, and Player-State Safety

**Estimated group completion: about 44%.**

This group contains features that observe or change live player state. It includes
staff mode, vanish, freeze, inventory/Ender access, confiscation, economy actions,
alt investigations, the player inspector, cheat testers and fake systems.

These workflows are high risk because a stale view, crash, server switch or
incorrect recovery decision can leak staff items, overwrite newer player data,
lose assets or create duplicates.

- Return to [[Feature Completion Status|Implementation-Status]].
- Staff procedures: [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]],
  [[Inventory and Confiscation Safety]], and [[Alt Investigations]].
- Source-level traces: [[Developer Code Guide]].

> Percentages are rounded planning estimates. Exact evidence and blockers remain
> in the
> [requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md).

## Find a staff-tool area

| Area | Complete | What it does | Jump to details |
| --- | ---: | --- | --- |
| Staff mode | **50%** | Separates staff work from normal gameplay using a durable before snapshot and exact restore. | [Staff mode](#staff-mode) |
| Freeze | **43%** | Temporarily blocks a target from changing evidence or leaving an active investigation. | [Freeze](#freeze) |
| Vanish and spectator masking | **55%** | Hides staff from unauthorized viewers across tab, entities, commands and integrations. | [Vanish](#vanish-and-spectator-masking) |
| Online inventory and Ender access | **50%** | Lets authorized staff view or edit a live player's inventory without stale full-state writes. | [Online inventory](#online-inventory-and-ender-access) |
| Offline inventory and queued patches | **43%** | Safely edits offline data only when ownership is certain, otherwise queues a login-time patch. | [Offline inventory](#offline-inventory-and-queued-patches) |
| Item confiscation and restoration | **53%** | Removes exact case-linked assets after a durable snapshot and allows idempotent restoration. | [Item confiscation](#item-confiscation-and-restoration) |
| Economy confiscation and restoration | **43%** | Removes an exact verified amount through EnthusiaCurrency without raw database writes. | [Economy](#economy-confiscation-and-restoration) |
| Alt relationships and inheritance | **35%** | Stores protected network evidence, confidence and approved exceptions without exposing raw addresses. | [Alts](#alt-relationships-and-sanction-inheritance) |
| Player inspector and client view | **45%** | Combines identity, reports, punishments, client, alt and provider context for staff. | [Inspector](#player-inspector-and-client-view) |
| Staff hotbar and tools menu | **40%** | Provides rank-specific investigation tools while preventing staff-item leakage. | [Staff tools](#staff-hotbar-and-tools-menu) |
| Cheat testers | **30%** | Runs controlled evidence-only tests and restores exact temporary state. | [Cheat testers](#cheat-testers) |
| Fake entity and fake base | **18%** | Presents isolated virtual investigation targets without modifying the real world. | [Fake systems](#fake-entity-and-fake-base) |

## Staff mode

### What it does

`/staff` creates a durable snapshot of the staff member's normal state before
applying a rank-specific investigation profile. Exiting should remove every staff
item and restore the exact inventory, armor, offhand, XP, health, hunger, effects,
location, server, game mode, flight and metadata.

### Primary files

- [Staff mode manager](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java)
- [Staff access policy](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/staff/StaffModeAccessPolicy.java)
- [Staff state codec](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/staff/StaffStateCodec.java)
- [Staff domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/staff)
- [Staff-session store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcStaffSessionStore.java)
- [Staff command](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/command/StaffModeCommand.java)

### Related pages

- [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]]
- [[Roles and Permissions|Rank-Authority]]

### What remains

Capture and verify every required field; enforce rank profiles against accidental
vanilla/plugin bypasses; complete cross-server location restore, CombatLogX gates,
crash/reconnect resume, disable recovery and staff-item leak prevention.

## Freeze

### What it does

`/freeze` applies a durable temporary investigation restriction. The finished
workflow blocks movement, damage, inventories, containers, item use, block
interaction, teleporting, backend switching and unauthorized commands while
routing the frozen player's chat only to self and staff.

### Primary files

- [Freeze manager](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/freeze/FreezeManager.java)
- [Freeze command](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/command/FreezeCommand.java)
- [Freeze domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/freeze)
- [Freeze store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcFreezeStore.java)
- [Paper listeners](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper)

### Current development

PR #27 contains additional freeze lifecycle, reconnect, persistence and Folia-safe
work. Until merged, that branch is active evidence rather than the behavior of
`main`.

### What remains

Reconcile the active work, cover every movement/world/inventory bypass, implement
staff-only chat, prevent backend switching, complete reconnect/offline expiration
and stage restart behavior on Java, Bedrock and Folia-compatible scheduling.

## Vanish and spectator masking

### What it does

Vanish is separate from staff mode. A central visibility service decides whether
one viewer may see one staff target. Paper hide/show calls, player-info packet
filtering and entity tracking suppression work together so unauthorized players
do not see vanished or actual-spectator state.

### Primary files

- [Visibility API](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/api/StaffVisibilityService.java)
- [Default visibility service](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/visibility/DefaultStaffVisibilityService.java)
- [Vanish manager](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/visibility/VanishManager.java)
- [Vanish store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcVanishStore.java)
- [Vanish command](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/command/VanishCommand.java)
- [Visibility package](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/visibility)

### Related pages

- [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]]
- [[Vanish Internals]]

### What remains

Complete all tab/player-info fields, entity metadata/equipment/tracker resends,
command suggestions, `/seen`, teleport/message/pay, playtime, RoseChat, voice,
sounds, particles, containers and public APIs. Stage reconnect/performance behavior
with real Java, Bedrock and protocol versions.

## Online inventory and Ender access

### What it does

`/invsee` and `/endersee` open an authorized view of live player state. The target
player remains authoritative. Edits should apply exact dirty-slot changes on the
owning server thread and synchronize compatible viewers instead of saving a stale
full clone.

### Primary files

- [Inventory command](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/command/InventoryCommand.java)
- [Inventory coordinator](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/inventory/InventoryCoordinator.java)
- [Paper inventory package](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/inventory)
- [Inventory domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/inventory)
- [Inventory journal store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcInventoryJournalStore.java)

### Related pages

- [[Inventory and Confiscation Safety]]

### What remains

Complete armor/offhand/container views, dirty-slot updates, audit, one target
coordinator, concurrent-viewer synchronization, nested shulkers/bundles, stale
fingerprint rejection and safe close behavior.

## Offline inventory and queued patches

### What it does

Direct offline editing is allowed only when the player is offline network-wide,
the owning server/scope is known, an exclusive lease is held, no save is active,
the revision is current and a before snapshot is durable. When those facts cannot
be proven, the operation becomes a queued patch applied before player interaction
on login.

### Primary files

- [Inventory coordinator](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/inventory/InventoryCoordinator.java)
- [Inventory journal store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcInventoryJournalStore.java)
- [Inventory patch transitions](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcInventoryPatchTransitions.java)
- [Inventory domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/inventory)

### What remains

Prove network-wide ownership, active-save rejection, atomic file replacement,
reread verification, login-time ordering, retention, conflict/quarantine handling
and interruption at every stage.

## Item confiscation and restoration

### What it does

Confiscation is a case-linked destructive workflow, not ordinary inventory
editing. It selects exact nested item paths/fingerprints, locks relevant assets,
saves a durable snapshot, revalidates the selection and deletes only after the
snapshot is committed. `/case restoreitems` returns the original saved assets
idempotently.

### Primary files

- [Confiscation coordinator](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/inventory/ConfiscationCoordinator.java)
- [Inventory/confiscation domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/inventory)
- [Inventory journal store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcInventoryJournalStore.java)
- [Case command](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/command/CaseCommand.java)

### What remains

Complete full-container selection, stale reselection, movement/container bypass
protection, lock renewal, crash recovery, verification, rollback/quarantine,
changed-inventory restoration and server-switch behavior without loss or dupes.

## Economy confiscation and restoration

### What it does

The economy workflow calculates an exact plan, acquires locks, saves before state,
invokes EnthusiaCurrency through a supported moderation API, verifies the final
total and records terminal audit/recovery state. EnthusiaStaff must not change
provider balances through raw SQL.

### Primary files

- [Economy coordinator](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/economy/EconomyCoordinator.java)
- [Currency gateway](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/economy/EnthusiaCurrencyGateway.java)
- [Economy domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/economy)
- [Economy journal store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcEconomyJournalStore.java)
- [Currency integration contracts](https://github.com/wsg138/EnthusiaStaff/tree/main/integration-contracts/src/main/java)

### What remains

Rebuild the Currency moderation provider, exact snapshots/plans, offline support,
configurable removal order, replay/conflict states, final verification,
restoration and quarantine recovery.

## Alt relationships and sanction inheritance

### What it does

The alt system stores protected network equality tokens and relationship evidence
without exposing raw addresses. It distinguishes uncertain links, confirmed alts,
approved alts, shared households and not-related decisions. Only sufficiently
confident relationships inherit the exact remaining active ban/mute state.

### Commands

- `/alts`
- `/alt link`, `approve`, `household`, `notrelated`, `unlink`, `reopen`

### Primary files

- [Network identity protector](https://github.com/wsg138/EnthusiaStaff/blob/main/common/src/main/java/net/enthusia/staff/common/security/NetworkIdentityProtector.java)
- [HMAC token service](https://github.com/wsg138/EnthusiaStaff/blob/main/common/src/main/java/net/enthusia/staff/common/security/HmacTokenService.java)
- [Alt domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/alt)
- [Network identity store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcNetworkIdentityStore.java)
- [Velocity runtime](https://github.com/wsg138/EnthusiaStaff/tree/main/velocity/src/main/java/net/enthusia/staff/velocity)

### Related pages

- [[Alt Investigations]]
- [[Privacy and Data Handling]]

### What remains

Implement confidence weighting/aging, simultaneous-play reduction, maintenance
suppression, network-change decay, approved/household/not-related lifecycle,
inheritance, unread alerts, GUI, Bedrock presentation, recoverable encryption,
key rotation and production-like data review.

## Player inspector and client view

### What it does

The inspector is intended to combine identity, current server/world, Java/Bedrock,
protocol/version, client evidence, punishments, warnings, reports, alts, inventory,
Ender chest, freeze, spectate, provider state and authorized actions in one place.

### Primary files

- [Inspect command](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/command/InspectCommand.java)
- [Client command](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/command/ClientCommand.java)
- [Client integrations](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/client)
- [Evidence domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/evidence)

### What remains

Build the complete combined view, provider status, actions, privacy filtering,
permissions, offline behavior and Bedrock-compatible presentation.

## Staff hotbar and tools menu

### What it does

Staff mode should provide a rank-specific nine-slot hotbar for random teleport,
inspection, freeze, reports, cheat testing, follow/spectate, vanish, staff chat
and a tools menu. Every temporary item must be removed before normal state is
restored.

### Primary files and areas

- [Paper staff package](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/staff)
- [Staff mode manager](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java)
- [Paper command package](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/command)

### What remains

Finish all nine slots, rank profiles, tool actions, interaction restrictions,
state restoration and no-give/no-take/no-leak guarantees.

## Cheat testers

### What it does

Cheat testers create controlled, evidence-only scenarios such as Totem refill,
No-fall, Velocity/anti-knockback and Auto-armor. They must snapshot and restore
exact state, record relevant latency/TPS/geometry/effects and never punish
automatically.

### Primary areas

- [Paper staff/tool code](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper)
- [Inventory journal](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcInventoryJournalStore.java)
- [Client evidence domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/evidence)

### What remains

Implement each tester, exact temporary-state journaling, restore after crash or
disconnect, evidence output, permissions and Java/Bedrock validation.

## Fake entity and fake base

### What it does

Fake systems present investigation-only virtual content to the suspect and
authorized staff. A fake entity records aim/interaction behavior. A fake base uses
virtual blocks or a schematic without changing the real world, clears on timeout
or context change and warns before expiry.

### Required command

- `/fakebase` is required but not currently registered.

### Primary areas

- [Paper runtime](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper)
- [Protocol/packet foundations](https://github.com/wsg138/EnthusiaStaff/tree/main/protocol/src/main/java/net/enthusia/staff/protocol)
- [Plugin command metadata](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/plugin.yml)

### What remains

Implement isolated entity/block presentation, target/staff visibility, evidence,
cleanup on distance/world/server/disconnect/timeout, warning/extend/clear/teleport
controls and real Java/Bedrock tests without real world mutation.

## Related pages

- [[Feature Completion Status|Implementation-Status]]
- [[Remaining Development Map|Development-Blueprint]]
- [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]]
- [[Inventory and Confiscation Safety]]
- [[Alt Investigations]]
- [[Vanish Internals]]
- [[Commands and Permissions]]
- [[Roles and Permissions|Rank-Authority]]
- [[Developer Code Guide]]
