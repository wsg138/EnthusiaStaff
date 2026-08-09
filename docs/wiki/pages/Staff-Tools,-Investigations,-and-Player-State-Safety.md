# Staff Tools, Investigations, and Player-State Safety

This hub covers workflows that observe or mutate live player state: staff mode, operational tools, vanish, freeze, inventory/Ender access, confiscation, economy actions, alt investigations, client/inspector context, and future controlled test systems.

These areas are high risk because stale state, scheduler mistakes, disconnects, server switches, crashes, or ambiguous recovery can leak staff items, overwrite newer data, lose assets, duplicate assets, or expose private information.

For staff procedure, use [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]], [[Inventory and Confiscation Safety]], or [[Alt Investigations]]. For source tracing, use [[Developer Code Guide]]. For review invariants, use [[Code Review Guide]].

## Quick status

| Area | Merged-main state | Main limitation |
| --- | --- | --- |
| Durable staff mode | **Implemented, not staging-verified** | Representative Java/Bedrock/Folia/restart/distributed restoration evidence remains. |
| Operational staff hotbar/tools | **Implemented, not staging-verified** | Real-client/Folia/distributed acceptance and advanced future tools remain. |
| Vanish | **Available with limitations** | Complete cross-plugin/visual/packet/provider coverage remains. |
| Freeze | **Partial** | Exhaustive bypass coverage and representative restart/client/backend-switch staging remain. |
| Online inventory/Ender access | **Partial** | Concurrent viewers, nested data, stale-state and full runtime ownership proof remain. |
| Offline inventory/queued patches | **Partial** | File/save ownership, login ordering, interruption/recovery and multi-server proof remain. |
| Item confiscation/restoration | **Partial** | Full failure injection, movement/container races, restart recovery and dupe/loss proof remain. |
| Economy moderation/restoration | **Partial / provider-dependent** | Complete supported EnthusiaCurrency moderation contract and end-to-end recovery remain. |
| Alt/network identity workflows | **Partial** | Confidence/exceptions/inheritance/UI/key-rotation/private-data acceptance remain. |
| Inspector/client evidence | **Partial** | Complete combined staff view/provider state/privacy/Bedrock presentation remain. |
| Cheat testers/fake entities/fake bases | **Planned / incomplete** | Controlled evidence-only implementation and exact state restoration are not complete. |

## Staff mode

`/staff` creates durable session state before the temporary staff profile is applied. The recovery contract is more important than the hotbar: normal inventory, armor, offhand, XP, health/hunger, effects, location/server, game mode, flight and related state must be restored from the durable original snapshot rather than reconstructed from memory.

Primary paths:

- [StaffModeManager](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java)
- [StaffModeActivationCoordinator](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/staff/StaffModeActivationCoordinator.java)
- [StaffModeAccessPolicy](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/staff/StaffModeAccessPolicy.java)
- [StaffStateCodec](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/staff/StaffStateCodec.java)
- [staff domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/staff)
- [JdbcStaffSessionStore](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcStaffSessionStore.java)

Entry/exit/reconnect/restart logic should fail closed when durable state cannot be proved. A failed restore should preserve the recovery snapshot rather than overwrite it with the broken current state.

## Operational staff tools

Current merged staff mode includes the operational hotbar/tool routing layer. The important boundary is that a tool item does **not** grant authority.

Relevant classes include:

- [StaffToolDefinition](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/staff/StaffToolDefinition.java)
- [StaffToolDispatcher](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/staff/StaffToolDispatcher.java)
- [StaffToolCooldowns](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/staff/StaffToolCooldowns.java)
- [StaffModeWorldInteractionListener](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/staff/StaffModeWorldInteractionListener.java)
- `paper/command/StaffToolsCommand.java`

The dispatcher rechecks the active session, owner UUID, current session token, canonical slot/material, current explicit rank, and action permission before routing. Command-backed actions continue through their existing service/command boundary so the hotbar cannot bypass authorization, operational modes, provider health, hierarchy, or audit.

Current routes include random-player teleport, inspect, freeze, reports, follow/spectate, vanish, staff chat, and the tools menu. Bedrock users have text/command fallbacks. Advanced cheat-testing functionality is separate and should not be inferred from the existence of a reserved tool slot.

Staff instructions: [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]].

## Vanish

Vanish uses durable intent plus a rank-aware `StaffVisibilityService`. Current merged behavior includes incremental viewer/target reconciliation, reconnect/session fencing, Paper hide/show application, and narrowly scoped ProtocolLib player-info handling.

Primary paths:

- [StaffVisibilityService](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/api/StaffVisibilityService.java)
- [DefaultStaffVisibilityService](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/visibility/DefaultStaffVisibilityService.java)
- [VanishManager](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/visibility/VanishManager.java)
- [visibility package](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/visibility)
- [JdbcVanishStore](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcVanishStore.java)

Do not generalize this to complete invisibility across every plugin, command completion, voice recipient, sound/particle/container effect, scoreboard, public API or analytics integration. Those are explicit integration surfaces.

Deep dive: [[Vanish Internals]].

## Freeze

Freeze is a durable investigation restriction, not a punishment. It must remain consistent across reconnect/restart and must not rely on one movement event while other interaction/teleport/backend paths stay open.

Primary paths:

- [FreezeManager](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/freeze/FreezeManager.java)
- [FreezeCommand](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/command/FreezeCommand.java)
- [freeze domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/freeze)
- [JdbcFreezeStore](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcFreezeStore.java)

Review movement, damage, inventory/container, item use, block interaction, teleport, command/chat and backend-switch paths separately. Real Java/Bedrock/Folia/restart acceptance is still required.

## Online inventory and Ender access

The live player remains authoritative. Safe editing should apply the intended current revision/dirty change on the owning scheduler rather than closing a stale cloned inventory and overwriting newer state.

Primary paths:

- [InventoryCommand](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/command/InventoryCommand.java)
- [InventoryCoordinator](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/inventory/InventoryCoordinator.java)
- [paper inventory package](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/inventory)
- [inventory domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/inventory)
- [JdbcInventoryJournalStore](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcInventoryJournalStore.java)

High-risk cases include armor/offhand, nested containers, multiple viewers/editors, movement while open, server switch, stale fingerprints and close/save races.

Procedure/deep safety rules: [[Inventory and Confiscation Safety]].

## Offline inventory and queued patches

Offline mutation is safe only when network-wide offline status, owning server/scope, exclusive lease/fence, save state, revision and before snapshot are trustworthy. When direct ownership cannot be proved, a durable queued patch is safer than guessing at a player file.

Relevant source:

- `paper/inventory/InventoryCoordinator.java`
- `persistence/JdbcInventoryJournalStore.java`
- `persistence/JdbcInventoryPatchTransitions.java`
- `domain/inventory/`

The remaining work is concentrated in real file/save ownership, atomic replacement/reread verification, login ordering, interruptions at every stage, retention/conflict behavior and distributed acceptance.

## Item confiscation and restoration

Confiscation is a case-linked destructive workflow. It should identify exact item paths/fingerprints, persist a durable before snapshot, revalidate current state, remove only the selected assets, verify the result, and retain enough journal state for idempotent restore or quarantine.

Primary paths:

- [ConfiscationCoordinator](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/inventory/ConfiscationCoordinator.java)
- `domain/inventory/`
- `persistence/JdbcInventoryJournalStore.java`
- `paper/command/CaseCommand.java`

Do not recover by manually recreating items while a durable operation may still be retryable. See [[Recovery and Troubleshooting]].

## Economy moderation

EnthusiaStaff owns moderation intent, case/audit linkage, operation journaling, verification and recovery. EnthusiaCurrency remains balance authority. Direct balance-table SQL is not an acceptable shortcut.

Primary paths:

- [EconomyCoordinator](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/economy/EconomyCoordinator.java)
- [EnthusiaCurrencyGateway](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/economy/EnthusiaCurrencyGateway.java)
- [economy domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/economy)
- [JdbcEconomyJournalStore](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcEconomyJournalStore.java)
- [integration contracts](https://github.com/wsg138/EnthusiaStaff/tree/main/integration-contracts/src/main/java)

Complete provider-side API behavior and end-to-end failure/recovery proof remain required.

## Alt relationships and protected network identity

The alt subsystem stores protected equality/evidence state rather than displaying raw addresses as ordinary moderation data. UUID identity and confidence/relationship policy must remain separate from “same address means same player” assumptions.

Primary paths:

- [NetworkIdentityProtector](https://github.com/wsg138/EnthusiaStaff/blob/main/common/src/main/java/net/enthusia/staff/common/security/NetworkIdentityProtector.java)
- [HmacTokenService](https://github.com/wsg138/EnthusiaStaff/blob/main/common/src/main/java/net/enthusia/staff/common/security/HmacTokenService.java)
- [alt domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/alt)
- [JdbcNetworkIdentityStore](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcNetworkIdentityStore.java)
- Velocity presence/network-identity code

Remaining work includes richer confidence/aging and household/approved/not-related lifecycle, inheritance, alerts/UI, key rotation and production-like private-data review.

Staff procedure: [[Alt Investigations]]. Privacy: [[Privacy and Data Handling]].

## Inspector and client evidence

The inspector/client surfaces are intended to assemble identity, current location/server context, verified platform/client information, moderation history, reports, alts, inventory state and authorized actions without leaking private fields to the wrong audience.

Primary paths:

- [InspectCommand](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/command/InspectCommand.java)
- [ClientCommand](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/command/ClientCommand.java)
- [client integrations](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/client)
- [evidence domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/evidence)

Provider evidence is context, not automatically proof of cheating. Missing providers should produce an explicit unavailable/unknown state rather than invented results.

## Cheat testers and fake systems

The goals include controlled cheat testers, fake entities and fake bases. These systems are not complete merely because an operational staff-tools slot is reserved or shared primitives exist.

When implemented, they must be evidence-oriented and isolated: no permanent world mutation, no player-state loss, exact cleanup/restore, no leaked fake entities to unrelated players, bounded duration/resources, and safe disconnect/restart behavior.

Until that complete workflow and representative staging exist, document these as planned/incomplete rather than operational staff tools.

## Go deeper

- [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]] — staff procedure and current operational-tool behavior.
- [[Vanish Internals]] — detailed visibility/session/scheduler/packet behavior.
- [[Inventory and Confiscation Safety]] — inventory/confiscation procedure and invariants.
- [[Alt Investigations]] — staff alt-investigation procedure.
- [[Privacy and Data Handling]] — sensitive evidence boundaries.
- [[Recovery and Troubleshooting]] — safe failure/recovery procedure.
- [[Developer Code Guide]] — detailed source traces.
- [[Code Review Guide]] — scheduler, player-state, persistence and privacy review.
- [[Build and Testing]] — evidence limits and runtime acceptance.