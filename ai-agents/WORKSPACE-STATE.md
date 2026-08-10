# Workspace state

Last updated: 2026-08-10

Live GitHub overrides stale records. This file records the current sequential-worker routing snapshot; detailed evidence remains in package records and canonical handoffs.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05`, `ES-R01`, `ES-R02`, `ES-V01` |
| Active/selected package | `ES-P07 — Inventory and Ender editing runtime completion`; `ACTIVE`; implementation, direct wiring coverage, CodeRabbit data-integrity fix and Codacy static-analysis fix complete; branch `package/es-p07-inventory-runtime`; ready PR `#112`. |
| ES-P07 starting head | `17fb50d02fdc35cffd1cbdc63e28f72cffd88315`; branch created exactly from this `main` head after live reconciliation. |
| ES-P07 handoff | `ai-agents/reports/package-handoffs/2026-08-10-es-p07-inventory-runtime-active.md`. |
| Other ready package | `ES-P06 — Discord notification delivery completion` remains `READY` at priority 60 and must not be started by this worker. |
| Parked provider package | `ES-X01 — RoseChat provider and communication integration`; `BLOCKED` / `PARKED_BLOCKED` because the supported RoseChat integration repository/source contract remains unresolved. |
| Dependency-blocked packages | `ES-P08`, `ES-X02`, `ES-X03`, `ES-X04`, `ES-V02`, `ES-V03`, `ES-A01`, and `ES-QA01` remain parked until their documented dependencies/external conditions change. |
| Migration boundary | V18 remains current and immutable. ES-P07 adds no migration. |
| Production boundary | Issue #43 remains open/deferred; LiteBans remains authoritative; no production data, production shadow, authority, cutover, deployment, or source-data rewrite is authorized. |
| Intended terminal state | `ES-P07 COMPLETE` after a normal merge of the exact validated head, verified containment/divergence and safe temporary-branch cleanup, dependency-derived routing publication, then worker stop without activating another package. |
| Exact next action | Finish the current handoff record, freeze the resulting PR #112 head, require fresh exact-head hosted build/test, static-analysis/coverage, zero unresolved review threads, Sentinel restart and canonical Pi evidence, then merge/finalize only if unchanged and fully green. |

## ES-P07 final-refreeze state

Live selection found no actionable continuation. ES-P07 and ES-P06 were the only dependency-complete READY packages, so ES-P07 was selected at priority 45. No second package has been started.

Implementation is complete. ES-P07 uses exact logical dirty-slot application instead of stale whole-image inventory replacement; validates the entire dirty-slot set before obtaining/mutating player inventory; bounds complete serialized inventory images at 32 MiB; makes same-operation APPLYING lease replay idempotent while preserving competing-operation fencing; and blocks additional vanilla/Paper mutation paths while a login/recovery inventory lock is active. Existing async authoritative-directory lookup, entity-thread scheduling, offline queued patches, durable revision/checksum decisions, Velocity switch fencing, nested item serialization and shutdown ownership are preserved.

The original ES-P07 test contract was re-read before merge. Existing direct tests prove both inventory commands require `enthusiastaff.inventory.view` and the authoritative rank policy separates that read permission from `enthusiastaff.inventory.edit`. `InventoryWorkflowWiringTest` proves `invsee`/`endersee` share the inventory command/coordinator path, authoritative lookup returns through the viewer entity scheduler, and GUI editing is independently gated by `inventory.edit`.

CodeRabbit identified and the worker fixed a valid partial-mutation defect: a later invalid logical slot could previously fail after an earlier valid slot was written. The complete deduplicated slot set is now validated before the mutation phase, with mixed valid/out-of-range regression coverage. All review threads are resolved.

Exact-head Codacy analysis on freeze `2c0ad0543f9f39f242e8a75c532a77ca2afb52de` then found two valid method-level synchronization warnings in the bounded encode stream. The stream is local to one encode invocation and is not shared, so the unnecessary `synchronized` method modifiers were removed rather than replaced with pointless lock blocks. That freeze and all of its checks are superseded.

Earlier sanity/freeze heads `321a50f3ca120dba7d7e21542450d4d527dabbd3`, `c3545612d370ea237a63394e6a0401edbe650790`, `6c7ec06622b8ee20d00aa3839e5741f44a0f1976`, and `2c0ad0543f9f39f242e8a75c532a77ca2afb52de` are all non-final evidence. The first recovery-guard compile failure remains non-passing. Sentinel attempts on `2c0ad054...` failed host resource preconditions at 637 MB available versus 700 MB required and are not Paper restart passes. No failed, cancelled or superseded run may be used as final proof.

Item confiscation/restoration remains ES-P08. Provider-backed destructive work remains ES-X02/ES-X03/ES-X04. Representative private large-inventory, distributed Java/Bedrock acceptance remains ES-V02.

## Stop boundary

This worker owns only ES-P07. It must not activate, prepare, stage or partially implement ES-P06, ES-X01, ES-P08 or any other package. After ES-P07 reaches a truthful terminal state and dependency-derived statuses are updated, stop.
