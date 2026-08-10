# ES-P07 — Inventory and Ender editing runtime completion

Status: `ACTIVE` — implementation/review/direct wiring coverage complete; re-freezing for exact-head validation.

## Selection and classification

- Selected package: `ES-P07 — Inventory and Ender editing runtime completion`.
- Classification at selection: `READY`.
- Selection reason: live reconciliation found no `ACTIONABLE_CONTINUATION`; `ES-P07` and `ES-P06` were the only dependency-complete `READY` packages, and ES-P07 has the lower numerical priority (45).
- `ES-X01` remains `BLOCKED` / `PARKED_BLOCKED` because no supported RoseChat standalone repository/default branch/source/AGENTS can be resolved.
- All dependency-blocked validation/destructive packages remain parked.

## Starting state

- Worker type: generic sequential package worker.
- EnthusiaStaff starting/default head: `17fb50d02fdc35cffd1cbdc63e28f72cffd88315`.
- Temporary branch: `package/es-p07-inventory-runtime` created exactly from that head.
- Pull request: ready-for-review PR `#112` to `main`.
- Starting open EnthusiaStaff PRs: none.
- Starting ES-P07 branch/PR/handoff: none.
- Issue #43 remains open/deferred; LiteBans remains authoritative.
- Immutable migration boundary: V18 (`V18__cheat_tester_session_journal.sql`). ES-P07 adds no migration.

## Verified standalone heads at selection

Reconciliation facts only; ES-P07 is internal and modifies none of these repositories.

- `wsg138/enthusia-site`: `2fe7d59c1c5e12db0b7ba792fc9e2af4d24337c2`
- `wsg138/EnthusiaCurrency`: `9696501a01cc11f6e5220c5297a6f34b64204e61`
- `wsg138/EnthusiaMarket`: `bc24f1010642d6042307bc13a32fb33cc94e8883`
- `wsg138/EnthusiaCommend`: `2083061b8aeaa7fb3adaf89746f91a45e3a03e59`
- RoseChat provider repository: unresolved.

## Completed implementation

- Preserved `/invsee` and `/endersee` view/edit permission split and authoritative-directory command routing.
- Replaced stale whole-image player/Ender writes with exact logical dirty-slot application across storage, armor, offhand and Ender slots.
- Added a 32 MiB aggregate serialized snapshot safety limit while retaining the existing 16 MiB per-item limit.
- Changed duplicate `APPLYING` claim behavior so the same operation that still owns the exact live lease/fence receives an idempotent replay instead of self-blocking until expiry; competing operations remain excluded.
- Added a recovery guard for damage/resurrection, item consumption/damage/mending, entity interaction and Paper pick-item/equipment-swap paths while the inventory coordinator owns local state.
- Registered the guard through the existing Paper runtime owner; shutdown ownership was inspected and already correctly closes the coordinator.
- Added unit regression coverage for aggregate snapshot rejection and MariaDB/Testcontainers coverage for same-owner active-lease replay.
- Reconciled the original package test contract against the current suite: `CommandPermissionConfigurationTest` already verifies both inventory commands require `enthusiastaff.inventory.view`, and `PermissionPolicyConfigurationTest` already verifies Helper lacks `inventory.edit` while Mod/Developer receive it through the authoritative rank policy.
- Added `InventoryWorkflowWiringTest` to close the remaining direct command/GUI evidence gap. It structurally proves both `invsee` and `endersee` bind to the same `InventoryCommand`, target resolution uses the authoritative directory, open handoff returns to the viewer entity scheduler, and the GUI checks `inventory.edit` separately from the view permission.
- Updated the inventory safety Wiki with exact-slot, queued-offline-patch, login-recovery and validation boundaries.

## Harsh review findings

- Initial hosted compile of the recovery guard failed because `PlayerPickItemEvent` and `PlayerSwapWithEquipmentSlotEvent` were imported from the Bukkit package. They are Paper events; imports were corrected to `io.papermc.paper.event.player`. The failed run remains recorded as a failure.
- Shared confiscation/restoration callers of `InventoryImageCodec.apply` were inspected. They already make checksum/fencing decisions while holding local asset locks; dirty-slot application preserves those intended replacement/rollback semantics without implementing the separate ES-P08 package.
- A possible permanent gameplay lock after checksum-conflict quarantine was considered and rejected. Durable quarantine already fences later inventory operations/backend switching; an indefinite gameplay lock would require an operator-release policy that ES-P07 does not define.
- The first frozen head `c3545612d370ea237a63394e6a0401edbe650790` was deliberately superseded after the original ES-P07 contract was re-read and its explicit command/GUI wiring-test requirement was compared with the old audit's 0% direct wiring coverage. The missing structural wiring test was added instead of accepting green infrastructure as substitute evidence.
- No migration, production, external-provider, lifecycle-owner or second-package scope leak was found in the reviewed diff.

## Intermediate and superseded evidence — not final acceptance

Intermediate head `321a50f3ca120dba7d7e21542450d4d527dabbd3` passed:

- Coverage run `31422933262`, job `93567841848`, exact checkout under Temurin Java 21.0.11+10.
- Java 21 full build/tests and MariaDB/Testcontainers; runtime-JAR/provider-leak inspection; aggregate coverage 47.12% lines / 38.19% branches / 49.78% instructions.
- Validation artifact `9076237833`, digest `sha256:01ae05d4a92434c713fe1fcd6d98892d58c5431f51787d62077d5c1cf8fae287`.
- Validate Wiki `31422933273`, Sentinel Restart Artifact `31422933425`, and CodeRabbit status succeeded.

The first freeze `c3545612d370ea237a63394e6a0401edbe650790` also accumulated some green checks, including exact Sentinel artifact run `31423819694` / job `93570672910`, artifact `9076410532` digest `sha256:c8370a6a22a97eaf43ea244612d770b09a0a59383b40b504ae32b71a778346f7`, and Codacy static success. All `c354561...` checks are superseded because `InventoryWorkflowWiringTest` changed tracked content afterward. They are not final package evidence.

A noncanonical `/sentinel test restart @<sha>` PR comment was also posted while reconciling Sentinel procedure. The live guide was then re-read and showed the exact accepted body is `@enthusia-sentinel test restart` with no suffix. The noncanonical comment is ordinary ignored text and is not evidence. Final Sentinel validation must be re-issued only for the new frozen head after its exact artifact is green.

## Preserved exclusions and boundaries

- Item confiscation/restoration completion belongs to ES-P08.
- Provider-backed destructive work belongs to ES-X02/ES-X03/ES-X04.
- Production player data is out of scope.
- Representative private large-inventory/Java-Bedrock/distributed acceptance remains ES-V02.
- No deployment, production authority/cutover, issue #43 acceptance, production shadow window, source-data rewrite or migration repair occurred.

## Exact next action

Finish the tracking checkpoint, take the resulting PR #112 head as the new immutable frozen revision, and make no further tracked changes unless a final gate exposes a real ES-P07 defect. Require exact frozen-head Java/MariaDB, runtime-JAR, Wiki, static-analysis/coverage, CodeRabbit, successful Sentinel artifact plus exact `@enthusia-sentinel test restart` terminal `PAPER_RESTART_OK`, and canonical automatic public→private Pi staging. Merge normally only if the frozen head is unchanged, mergeable, fully green and has zero valid unresolved review threads. Then verify containment/divergence/branch cleanup, publish terminal COMPLETE/dependency-derived routing through the allowed documentation finalization, and stop without activating another package.