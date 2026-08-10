# Workspace state

Last updated: 2026-08-10

Live GitHub overrides stale records. This file records the current sequential-worker routing snapshot; detailed evidence remains in package records and canonical handoffs.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05`, `ES-R01`, `ES-R02`, `ES-V01` |
| Active/selected package | `ES-P07 — Inventory and Ender editing runtime completion`; `ACTIVE` / `ACTIONABLE_CONTINUATION`; branch `package/es-p07-inventory-runtime`; ready PR `#112`. |
| Original ES-P07 start | `17fb50d02fdc35cffd1cbdc63e28f72cffd88315`; branch was created exactly from that `main` head. |
| Current target main synchronization | Current target `main` `2d8fcf27b0bac980211149ae8f7f4e7798998ee5` was normally merged into the package branch as `562e8647063c3fa09b4349d167dc41d1a1660553`. The merge restored PR mergeability and changed no ES-P07 product file. |
| Final review correction | CodeRabbit reviewed the synchronized candidate and found two valid issues: the permission metadata test did not prove the `default` fields existed, and tracked package state still described an older freeze as final. This checkpoint fixes both. The resulting commit is the new immutable validation candidate; all evidence bound to `562e864...` or earlier is superseded. |
| ES-P07 handoff | `ai-agents/reports/package-handoffs/2026-08-10-es-p07-inventory-runtime-active.md`. |
| Other ready package | `ES-P06 — Discord notification delivery completion` remains `READY` at priority 60 and must not be started by this worker. |
| Parked provider package | `ES-X01 — RoseChat provider and communication integration`; `BLOCKED` / `PARKED_BLOCKED` because the supported RoseChat integration repository/source contract remains unresolved. |
| Dependency-blocked packages | `ES-P08`, `ES-X02`, `ES-X03`, `ES-X04`, `ES-V02`, `ES-V03`, `ES-A01`, and `ES-QA01` remain parked until their documented dependencies/external conditions change. |
| Migration boundary | V18 remains current and immutable. ES-P07 adds no migration. |
| Production boundary | Issue #43 remains open/deferred; LiteBans remains authoritative; no production data, production shadow, authority, cutover, deployment, or source-data rewrite is authorized. |
| Intended terminal state | `ES-P07 COMPLETE` after a normal merge of the exact validated head, verified containment/divergence and safe temporary-branch cleanup, dependency-derived routing publication, then worker stop without activating another package. |
| Exact next action | Freeze the commit containing this final review correction and rerun every exact-head hosted build/test, static-analysis/coverage, review-thread, Sentinel restart and canonical Pi gate. Merge/finalize only if that same head remains unchanged, mergeable and fully green. |

## ES-P07 current state

Live reconciliation resumed ES-P07 before ES-P06 because the formerly blocked private Pi runner materially became available. The old exact-head private attempt then failed before Paper because its transient bridge artifact returned HTTP 404; that failure was not called a pass. A later automatic retry was superseded when current `main` had to be normally merged into PR #112 to restore mergeability.

Product implementation is complete. ES-P07 uses exact logical dirty-slot application instead of stale whole-image replacement; validates the complete dirty-slot set before obtaining or mutating player inventory; bounds complete serialized inventory images at 32 MiB; makes same-operation APPLYING lease replay idempotent while preserving competing-operation fencing; and blocks additional vanilla/Paper mutation paths while a login/recovery inventory lock is active. Existing authoritative-directory lookup, entity-thread scheduling, offline queued patches, revision/checksum decisions, Velocity switch fencing, nested item serialization and shutdown ownership are preserved.

The synchronized candidate `562e8647063c3fa09b4349d167dc41d1a1660553` passed the exact hosted Java 21 clean/full build and tests with MariaDB/Testcontainers, runtime-JAR/provider-leak inspection, aggregate JaCoCo/Codacy upload and Wiki. Those results are now diagnostic only because CodeRabbit subsequently identified two valid findings. This checkpoint adds explicit `hasNonNull("default")` assertions for both inventory permission metadata nodes and updates tracked freeze/routing language so the synchronized predecessor is not misrepresented as final evidence.

All earlier validation heads, including `b34aade6ae79c7aaada0ada3c87970f937b6db6a` and `562e8647063c3fa09b4349d167dc41d1a1660553`, are superseded. Sentinel job 83 and any canonical Pi work tied to `562e864...` are also stale for final acceptance even if they later execute. Fresh final evidence must bind to the resulting review-fix head.

Item confiscation/restoration remains ES-P08. Provider-backed destructive work remains ES-X02/ES-X03/ES-X04. Representative broader private large-inventory/distributed Java/Bedrock acceptance remains ES-V02.

## Stop boundary

This worker owns only ES-P07. It must not activate, prepare, stage or partially implement ES-P06, ES-X01, ES-P08 or any other package. After ES-P07 reaches a truthful terminal state and dependency-derived statuses are updated, stop.
