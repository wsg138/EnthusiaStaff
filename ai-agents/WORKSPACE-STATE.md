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
| Superseded final-review head | `8b055b8851dd185ae5e8969148aaa11e0d1985e4` fixed the two valid synchronized-head CodeRabbit findings, but exact-head Codacy then found one valid repeated-literal warning in the strengthened permission metadata test. All evidence for `8b055b...` and earlier is superseded. |
| Final static correction | The current checkpoint replaces the four repeated `"default"` literals with one `DEFAULT_FIELD` test constant while retaining explicit non-null field-presence and false-value assertions. The commit produced by this checkpoint is the new immutable validation candidate. |
| ES-P07 handoff | `ai-agents/reports/package-handoffs/2026-08-10-es-p07-inventory-runtime-active.md`. |
| Other ready package | `ES-P06 — Discord notification delivery completion` remains `READY` at priority 60 and must not be started by this worker. |
| Parked provider package | `ES-X01 — RoseChat provider and communication integration`; `BLOCKED` / `PARKED_BLOCKED` because the supported RoseChat integration repository/source contract remains unresolved. |
| Dependency-blocked packages | `ES-P08`, `ES-X02`, `ES-X03`, `ES-X04`, `ES-V02`, `ES-V03`, `ES-A01`, and `ES-QA01` remain parked until their documented dependencies/external conditions change. |
| Migration boundary | V18 remains current and immutable. ES-P07 adds no migration. |
| Production boundary | Issue #43 remains open/deferred; LiteBans remains authoritative; no production data, production shadow, authority, cutover, deployment, or source-data rewrite is authorized. |
| Intended terminal state | `ES-P07 COMPLETE` after a normal merge of the exact validated head, verified containment/divergence and safe temporary-branch cleanup, dependency-derived routing publication, then worker stop without activating another package. |
| Exact next action | Freeze the commit containing this final static-analysis correction and rerun every exact-head hosted build/test, static-analysis/coverage, review-thread, Sentinel restart and canonical Pi gate. Merge/finalize only if that same head remains unchanged, mergeable and fully green. |

## ES-P07 current state

Live reconciliation resumed ES-P07 before ES-P06 because the formerly blocked private Pi runner materially became available. An old exact-head private attempt then failed before Paper because its transient bridge artifact returned HTTP 404; that failure was not called a pass. Current `main` was later normally merged into PR #112 to restore mergeability.

Product implementation remains unchanged and complete: exact logical dirty-slot application, complete slot-set prevalidation before mutation, 32 MiB aggregate snapshot bounds, same-operation APPLYING lease replay, additional login/recovery mutation guards, authoritative-directory lookup, entity-thread scheduling, offline queued patches, revision/checksum decisions, Velocity switch fencing, nested item serialization and shutdown ownership.

The synchronized candidate `562e8647063c3fa09b4349d167dc41d1a1660553` passed hosted Java/MariaDB/JAR/Wiki/Codacy-upload checks before CodeRabbit found two valid review issues. Head `8b055b8851dd185ae5e8969148aaa11e0d1985e4` fixed those issues and resolved all review threads, but exact-head Codacy static analysis then reported the valid four-occurrence `"default"` literal warning. This checkpoint fixes only that test maintainability issue plus the tracking records necessary to supersede `8b055b...` honestly.

Any Sentinel, canonical Pi, hosted or static/review result tied to `8b055b...` or an earlier head is non-final even if it completes successfully. Fresh final evidence must bind to the resulting static-correction head. Do not cancel or interfere with unrelated legitimate Sentinel or Staging jobs.

Item confiscation/restoration remains ES-P08. Provider-backed destructive work remains ES-X02/ES-X03/ES-X04. Representative broader private large-inventory/distributed Java/Bedrock acceptance remains ES-V02.

## Stop boundary

This worker owns only ES-P07. It must not activate, prepare, stage or partially implement ES-P06, ES-X01, ES-P08 or any other package. After ES-P07 reaches a truthful terminal state and dependency-derived statuses are updated, stop.
