# Staff Tools, Investigations, and Player-State Safety

This hub answers two questions: **what staff/player-state tooling exists on merged `main`, and where should I go for the exact procedure or implementation?** These workflows are high risk because stale state, scheduler mistakes, disconnects, server switches, crashes or ambiguous recovery can leak staff items, overwrite newer data, lose/duplicate assets, or expose private information.

For staff procedure, start with [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]], [[Cheat Tester]], [[Inventory and Confiscation Safety]], or [[Alt Investigations]]. For source tracing, use [[Developer Code Guide]]. For review invariants, use [[Code Review Guide]].

## Quick status

| Area | Merged-main state | Main limitation |
| --- | --- | --- |
| Durable staff mode | **Implemented, not staging-verified** | Representative Java/Bedrock/Folia/restart/distributed restoration remains. |
| Operational staff hotbar/tools | **Implemented, not staging-verified** | Real-client/Folia/distributed acceptance remains. |
| Cheat Tester / fake entities / fake bases | **Implemented, not staging-verified** | Representative distributed Java/Bedrock/ProtocolLib behavior and private runtime acceptance remain. |
| Vanish | **Available with limitations** | Complete cross-plugin/visual/packet/provider coverage remains. |
| Freeze | **Partial** | Exhaustive bypass coverage and representative restart/client/backend-switch staging remain. |
| Online inventory/Ender access | **Partial** | Concurrent viewers, nested data, stale-state and full runtime ownership proof remain. |
| Offline inventory/queued patches | **Partial** | Login ordering, file/save ownership, interruption/recovery and multi-server proof remain. |
| Item confiscation/restoration | **Partial** | Full failure injection, movement/container races, restart recovery and dupe/loss proof remain. |
| Economy moderation/restoration | **Partial / provider-dependent** | Complete supported EnthusiaCurrency contract and end-to-end recovery remain. |
| Alt/network identity workflows | **Partial** | Confidence/exceptions/inheritance/UI/key rotation/private-data acceptance remain. |
| Inspector/client evidence | **Partial** | Complete combined staff view/provider state/privacy/Bedrock presentation remain. |

`Implemented, not staging-verified` means the feature exists in merged source with automated evidence, not that production/runtime acceptance has passed.

## Staff mode and operational tools

Staff mode creates durable session state before applying the temporary staff profile. The recovery contract is more important than the hotbar: the original inventory, armor, offhand, XP, health/hunger, effects, location/server, game mode, flight and related owned state must be restored from the durable snapshot rather than reconstructed from memory.

Primary paths:

- `paper/.../staff/StaffModeManager.java`
- `paper/.../staff/StaffModeActivationCoordinator.java`
- `paper/.../staff/StaffToolDispatcher.java`
- `paper/.../staff/StaffModeWorldInteractionListener.java`
- `domain/.../staff/`
- `persistence/.../JdbcStaffSessionStore.java`

The hotbar routes random teleport, inspect, freeze, reports, Cheat Tester, follow/spectate, vanish, staff chat and the tools menu. The item itself grants no authority: dispatcher and downstream command/service boundaries recheck session, owner/token, rank, permission, provider health and operational state.

Staff instructions: [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]].

## Cheat Tester, fake entities and fake bases

Cheat Tester is **merged functionality**. It is an evidence-only system for short bounded probes and does not automatically punish a player.

Current tester types include totem refill, no-fall, velocity, auto-armor and an optional ProtocolLib-backed fake entity. State-changing tests use durable V18 journal state and exact restoration/verification before terminal completion.

Fake bases are client-side virtual block overlays rather than real world mutation. The runtime requires safe already-loaded placement, bounded lifetime/concurrency and durable coordinate-free lifecycle audit. Cleanup sends current real block data back to viewers; a client-session/process loss cannot turn the virtual structure into saved world blocks.

Primary areas:

- `paper/src/main/java/net/enthusia/staff/paper/cheattester/`
- `paper/src/main/java/net/enthusia/staff/paper/command/CheatTesterCommand.java`
- `domain/src/main/java/net/enthusia/staff/domain/cheattester/`
- `persistence/src/main/java/net/enthusia/staff/persistence/JdbcCheatTesterSessionStore.java`
- `persistence/src/main/resources/db/migration/V18__cheat_tester_session_journal.sql`
- nearby Paper/domain/integration tests

The repository's overall Flyway history has since advanced to V19; V18 remains the migration that owns Cheat Tester journal state.

Procedure and exact controls: [[Cheat Tester]].

## Vanish

Vanish uses durable intent plus a rank-aware `StaffVisibilityService`. Merged behavior includes incremental viewer/target reconciliation, reconnect/session fencing, Paper hide/show application and narrowly scoped ProtocolLib player-info handling.

Primary paths:

- `paper/.../api/StaffVisibilityService.java`
- `paper/.../visibility/DefaultStaffVisibilityService.java`
- `paper/.../visibility/VanishManager.java`
- `persistence/.../JdbcVanishStore.java`

Do not generalize this to complete invisibility across every plugin, command completion, voice recipient, sound/particle/container effect, scoreboard, public API or analytics integration. Deep dive: [[Vanish Internals]].

## Freeze

Freeze is a durable investigation restriction, not a punishment. Review movement, damage, inventory/container, item use, interaction, teleport, command/chat and backend-switch paths separately.

Primary paths:

- `paper/.../freeze/FreezeManager.java`
- `paper/.../command/FreezeCommand.java`
- `domain/.../freeze/`
- `persistence/.../JdbcFreezeStore.java`

A fail-closed restriction while durable status cannot be verified is a safety response, not proof of a freeze record. Real Java/Bedrock/Folia/restart acceptance remains required.

## Inventory, Ender access and offline patches

The live player is authoritative for online editing. Safe mutation applies intended dirty changes on the player's owning scheduler rather than closing a stale full clone over newer state.

Offline editing requires trustworthy network-wide offline status, owning scope, revision/checksum and durable lease/fence. When direct file ownership cannot be proved, a queued patch is safer than guessing at a `.dat` file.

Primary paths:

- `paper/.../inventory/InventoryCoordinator.java`
- `paper/.../command/InventoryCommand.java`
- `domain/.../inventory/`
- `persistence/.../JdbcInventoryJournalStore.java`
- `persistence/.../JdbcInventoryPatchTransitions.java`

High-risk cases include armor/offhand, nested containers, multiple viewers, movement while open, server switch, login ordering and stale fingerprints. Procedure: [[Inventory and Confiscation Safety]].

## Confiscation and restoration

Confiscation is a case-linked destructive workflow. It should identify exact selected assets, persist a before snapshot, revalidate current state, remove only the selected assets, verify the result and retain enough durable state for idempotent restore or quarantine.

Primary paths include `paper/.../inventory/ConfiscationCoordinator.java`, the inventory domain, `JdbcInventoryJournalStore`, and case commands.

Do not recover by manually recreating items while a durable operation may still be retryable. See [[Recovery and Troubleshooting]].

## Economy moderation

EnthusiaStaff owns moderation intent, case/audit linkage, operation journaling, verification and recovery. EnthusiaCurrency remains balance authority. Direct provider-table SQL is not an acceptable shortcut.

Primary areas:

- `paper/.../economy/EconomyCoordinator.java`
- `paper/.../economy/EnthusiaCurrencyGateway.java`
- `domain/.../economy/`
- `persistence/.../JdbcEconomyJournalStore.java`
- `integration-contracts/`

Provider-side completion and representative cross-plugin recovery still gate the full claim.

## Alt relationships and protected network identity

The alt subsystem stores protected equality/evidence state rather than displaying raw addresses as ordinary moderation data. UUID identity, relationship confidence and intentional evasion are separate questions.

Primary paths:

- `common/.../security/NetworkIdentityProtector.java`
- `common/.../security/NetworkAddressTextGuard.java`
- `domain/.../alt/`
- `persistence/.../JdbcNetworkIdentityStore.java`
- Velocity presence/network-identity code

Remaining work includes richer operational acceptance, key rotation and production-like private-data validation. Procedure: [[Alt Investigations]]. Privacy: [[Privacy and Data Handling]].

## Inspector and client evidence

Inspector/client surfaces assemble authorized context such as identity, location/server, platform/client information, moderation history, reports and related investigation state. Provider evidence is context, not automatic proof of cheating.

Primary paths:

- `paper/.../command/InspectCommand.java`
- `paper/.../command/ClientCommand.java`
- `paper/.../client/`
- `domain/.../evidence/`

Missing providers should produce explicit unknown/unavailable results rather than invented evidence.

## Review priorities

Changes in this area deserve extra attention to:

- Paper/Folia entity/global/region scheduler ownership;
- stale callback/session fencing;
- durable-before-side-effect ordering;
- revision/checksum/lease/fence correctness;
- disconnect, backend switch, reload, disable and restart recovery;
- exact restoration rather than “best effort” reconstruction;
- Java/Bedrock interaction and fallbacks;
- provider missing/incompatible behavior;
- privacy of inventories, coordinates, client evidence and network identity;
- tests that distinguish deterministic state-machine proof from real runtime acceptance.

Use [[Code Review Guide]] for the cross-cutting checklist and [[Build and Testing]] for evidence interpretation.

## Go deeper

- [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]] — staff procedure.
- [[Cheat Tester]] — tester/fake-entity/fake-base detail.
- [[Vanish Internals]] — visibility/session/scheduler/packet behavior.
- [[Inventory and Confiscation Safety]] — inventory/confiscation procedure.
- [[Alt Investigations]] — alt-investigation procedure.
- [[Privacy and Data Handling]] — sensitive evidence boundaries.
- [[Recovery and Troubleshooting]] — safe recovery.
- [[Developer Code Guide]] — detailed source traces.
- [[Code Review Guide]] — scheduler, player-state, persistence and privacy review.
- [[Build and Testing]] — evidence limits and runtime acceptance.