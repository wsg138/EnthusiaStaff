# Workspace state

Last updated: 2026-08-10

Live GitHub overrides stale records. This file records the current sequential-worker routing snapshot; detailed evidence remains in package records and canonical handoffs.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05`, `ES-R01`, `ES-R02`, `ES-V01` |
| Active/selected package | `ES-P07 — Inventory and Ender editing runtime completion`; `ACTIVE`; implementation/review complete and freeze-ready; branch `package/es-p07-inventory-runtime`; PR `#112`. |
| ES-P07 starting head | `17fb50d02fdc35cffd1cbdc63e28f72cffd88315`; branch was created exactly from this `main` head after live reconciliation. |
| ES-P07 handoff | `ai-agents/reports/package-handoffs/2026-08-10-es-p07-inventory-runtime-active.md`. |
| Other ready package | `ES-P06 — Discord notification delivery completion` remains `READY` at priority 60 and must not be started by this worker. |
| Parked provider package | `ES-X01 — RoseChat provider and communication integration`; `BLOCKED` / `PARKED_BLOCKED` because the supported RoseChat standalone repository/default branch/source/AGENTS remain unresolved. |
| Dependency-blocked packages | `ES-P08`, `ES-X02`, `ES-X03`, `ES-X04`, `ES-V02`, `ES-V03`, `ES-A01`, and `ES-QA01` remain parked until their documented dependencies/external conditions change. |
| Migration boundary | V18 remains current and immutable. ES-P07 adds no migration. |
| Production boundary | Issue #43 remains open/deferred; LiteBans remains authoritative; no production data, production shadow, authority, cutover, deployment, or source-data rewrite is authorized. |
| Exact next action | Finish the ES-P07 tracking checkpoint, freeze the resulting PR #112 head, mark it ready, require exact-head hosted/static/review/Sentinel/canonical Pi evidence, merge normally only if unchanged and fully green, then publish terminal state and stop. |

## ES-P07 freeze-ready state

Live selection found no actionable continuation. ES-P07 and ES-P06 were the only dependency-complete READY packages, so ES-P07 was selected at priority 45. No second package has been started.

Implementation and harsh source review are complete. ES-P07 now uses exact logical dirty-slot application instead of stale whole-image inventory replacement; bounds complete serialized inventory images at 32 MiB; makes same-operation APPLYING lease replay idempotent while preserving competing-operation fencing; and blocks additional vanilla/Paper mutation paths while a login/recovery inventory lock is active. Existing command permission split, async authoritative directory lookup, entity-thread scheduling, offline queued patches, durable revision/checksum decisions, Velocity switch fencing, nested item serialization and shutdown ownership were preserved.

Intermediate sanity head `321a50f3ca120dba7d7e21542450d4d527dabbd3` passed Java 21 full build/tests, MariaDB/Testcontainers, runtime-JAR/provider-leak inspection, aggregate JaCoCo, Codacy coverage upload, Wiki validation and Sentinel artifact production. That head is explicitly not final acceptance because tracking records changed afterward. Final proof must be for the frozen PR head produced by this checkpoint.

The first recovery-guard compile attempt failed on incorrect Bukkit package imports for two Paper events and was fixed before the successful sanity build; the failed run is not represented as passing evidence.

Item confiscation/restoration remains ES-P08. Provider-backed destructive work remains ES-X02/ES-X03/ES-X04. Representative private large-inventory, distributed Java/Bedrock acceptance remains ES-V02.

## ES-V01 terminal result

ES-V01 is `COMPLETE`. Final frozen PR #110 head `de39e30232df9bd44d4b4df54a8922e815bada76` passed Java 21/full tests and MariaDB/Testcontainers, Codacy static/diff coverage, canonical public→private Pi staging and substantive review closure. PR #110 merged normally as `9a6c7240a4f6fffd216af0239709867b79080ddc`; containment was proven and the package branch was auto-deleted.

The private LiteBans database remained local. Sanitized representative results remain recorded in the ES-V01 package/handoff. Seven malformed/rejected rows remain a separate later data-policy decision. No production shadow, migration, cutover, authority change, source rewrite or issue #43 activation occurred.

## ES-P05 terminal result

ES-P05 is `COMPLETE`. Its final implementation head passed exact hosted/static/review/canonical public→private Pi proof and PR #81 merged normally. The implementation branch was safely auto-deleted after containment. Documentation-only completion/routing corrections were subsequently merged and are retained in the package record.

## Stop boundary

This worker owns only ES-P07. It must not activate, prepare, stage or partially implement ES-P06, ES-X01, ES-P08 or any other package. After ES-P07 reaches a truthful terminal state and dependency-derived statuses are updated, stop.
