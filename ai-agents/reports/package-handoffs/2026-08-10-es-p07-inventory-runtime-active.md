# ES-P07 — Inventory and Ender editing runtime completion

Status: `ACTIVE` — implementation/direct wiring coverage/substantive review fix complete; repeated harsh review and refreeze in progress.

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

- Preserved `/invsee` and `/endersee` view/edit permission separation and authoritative-directory routing.
- Replaced stale whole-image writes with exact logical dirty-slot writes for storage, armor, offhand and Ender slots.
- Added complete-slot-set validation before any player inventory mutation so a later invalid slot cannot leave an earlier slot partially applied.
- Added a 32 MiB aggregate serialized snapshot safety limit while retaining the 16 MiB per-item limit.
- Made a duplicate `APPLYING` claim by the same operation idempotently replay its exact active lease/fence while competing operations remain excluded.
- Added recovery guards for damage/resurrection, item consume/damage/mend, entity interaction and Paper pick-item/equipment-swap paths while inventory recovery owns local state.
- Preserved existing entity-thread scheduling, offline authoritative observations/queued patches, revision/checksum decisions, Velocity switch fencing, nested item serialization and lifecycle ownership.
- Added unit coverage for aggregate snapshot bounds and mixed valid/invalid dirty-slot validation plus MariaDB/Testcontainers same-owner lease replay coverage.
- Verified existing permission tests cover `inventory.view` versus `inventory.edit` rank separation and added `InventoryWorkflowWiringTest` for both command bindings, authoritative lookup/entity-scheduler handoff and GUI edit gating.
- Updated the inventory safety Wiki without claiming deferred private representative acceptance.

## Harsh review and fixes

- Initial hosted compile of the recovery guard failed because two Paper events were imported from the Bukkit namespace. They were corrected to `io.papermc.paper.event.player`; the failed run remains non-passing evidence.
- Shared confiscation/restoration callers of `InventoryImageCodec.apply` were inspected. They already make checksum/fencing decisions while holding asset locks; dirty-slot application preserves those replacement/rollback semantics without implementing ES-P08.
- A permanent gameplay lock after checksum-conflict quarantine was considered and rejected because durable quarantine already fences later inventory operations/backend switching; an indefinite gameplay freeze would require a separate operator-release policy outside ES-P07.
- The original package contract was re-read after first freeze and exposed the missing direct command/GUI wiring-test requirement. `InventoryWorkflowWiringTest` was added instead of treating infrastructure success as substitute evidence.
- CodeRabbit then found a valid data-integrity defect on second freeze `6c7ec06622b8ee20d00aa3839e5741f44a0f1976`: a list like `[validSlot, invalidSlot]` could mutate the valid slot before rejection. The corrected `applySlots` validates the complete deduplicated set before obtaining `PlayerInventory`, and the pure validator is regression-tested with mixed valid/out-of-range input.
- Documentation feedback is being reconciled while the freeze is open; no final merge will occur with a valid unresolved review thread.

## Superseded evidence — not final acceptance

- Intermediate sanity head `321a50f3ca120dba7d7e21542450d4d527dabbd3`: Java 21 full build/tests, MariaDB/Testcontainers, runtime-JAR/provider-leak inspection, aggregate coverage/Codacy, Wiki and Sentinel artifact sanity checks passed.
- First freeze `c3545612d370ea237a63394e6a0401edbe650790`: superseded by the required command/GUI wiring test.
- Second freeze `6c7ec06622b8ee20d00aa3839e5741f44a0f1976`: superseded by the valid CodeRabbit partial-mutation finding and fix.
- A noncanonical `/sentinel test restart @<sha>` comment was ordinary ignored text. Final Sentinel validation must use exactly `@enthusia-sentinel test restart` only after the new frozen-head Sentinel artifact is green.

No failed, cancelled, skipped, wrong-head or superseded run is final package evidence.

## Preserved exclusions and boundaries

- Item confiscation/restoration completion belongs to ES-P08.
- Provider-backed destructive work belongs to ES-X02/ES-X03/ES-X04.
- Production player data is out of scope.
- Representative private large-inventory/Java-Bedrock/distributed acceptance remains ES-V02.
- No deployment, production authority/cutover, issue #43 acceptance, production shadow window, source-data rewrite or migration repair occurred.

## Exact next action

Finish the repeated harsh review and review-finding/routing record updates, then make the latest-handoff update the final immutable PR #112 head. If any further real defect requires a tracked fix, repeat harsh review, freeze a new head and rerun every exact-head gate. Otherwise require the immutable head to pass Java 21 full build/tests and MariaDB/Testcontainers, runtime-JAR/provider-leak inspection, Wiki validation, Codacy/static/coverage, CodeRabbit with zero valid unresolved threads, successful exact Sentinel artifact plus terminal `PAPER_RESTART_OK`, and canonical automatic public→private Pi staging with correlated private restart/cleanup evidence. Merge normally only if that head remains unchanged and mergeable, then verify containment/divergence/branch cleanup, publish terminal COMPLETE/dependency-derived routing and stop without activating another package.