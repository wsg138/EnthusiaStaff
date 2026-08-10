# Workspace state

Last updated: 2026-08-10

Live GitHub overrides stale records. This file records the current sequential-worker routing snapshot; detailed evidence remains in package records and canonical handoffs.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05`, `ES-R01`, `ES-R02`, `ES-V01` |
| Active/selected package | None. `ES-V01 — Private LiteBans representative-data verification` is terminal `COMPLETE`; this worker must stop without activating another package. |
| Highest-priority next package | `ES-P07 — Inventory and Ender editing runtime completion` is `READY` at priority 45. A new sequential worker may select it after reconciling live GitHub. |
| Other ready package | `ES-P06 — Discord notification delivery completion` remains `READY` at priority 60. |
| Parked provider package | `ES-X01 — RoseChat provider and communication integration`; `BLOCKED` / `PARKED_BLOCKED` because the supported RoseChat standalone repository/default branch/source/AGENTS remain unresolved. |
| ES-V01 implementation | Final frozen PR head `de39e30232df9bd44d4b4df54a8922e815bada76`; PR `#110` merged normally as `9a6c7240a4f6fffd216af0239709867b79080ddc`. The package branch was auto-deleted after containment proved no unique work remained. |
| ES-V01 hosted proof | Final exact-head Coverage `31353964138` / job `93349968412` succeeded under Java 21 with the full test suite and MariaDB/Testcontainers. Codacy static `93347267178` succeeded; final diff coverage `93350870761` was 100.0%; coverage variation `93350870850` was +0.01%. |
| ES-V01 review state | Substantive CodeRabbit review on pre-review head `2485c8b7a4a80ae306216eb9f66f1e9415d9eac0` found three valid documentation inconsistencies plus a missing UUID-backed ban integration fixture. All were fixed in `de39e30232df9bd44d4b4df54a8922e815bada76`; all three review threads are resolved/outdated and marked addressed. Valid unresolved thread count is zero. The incremental re-review after the fix was rate-limited and is not represented as a second full review. Exact-head CodeRabbit commit status was success. |
| ES-V01 canonical Pi proof | Final public run `31353964382`: build job `93349969346` success and bridge job `93350945971` success; fork-boundary helper `93349969918` was skipped, not passed. Correlated private run `31354311211` / job `93350973876` succeeded on trusted `Lincoln-PI-4` against staging-controls head `991316917be5116546a3ceab101d0ad9e6b1dca3`. |
| ES-V01 private Pi evidence | Exact artifact/provenance/freshness passed; disposable DB pre-reset passed; two Paper starts and two storage-ready `SHADOW_MIGRATION` cycles passed; first cycle applied V1–V18, second verified schema v18 current/no-op; both shutdowns and failure scans passed; post-reset removed 69 objects and passed. Sanitized artifact `9050381344`, digest `sha256:34f77c0fe32fee5c79872daf9487371b17404f3308c4212b736b6f011a194bd0`. |
| LiteBans representative evidence | Local repair `22934e33` was reproduced as `ea07f55a` and later contained in final head `de39e30232df9bd44d4b4df54a8922e815bada76`. Private representative dry-run/import/replay/abandoned-run recovery passed and the database stayed local. Seven malformed/rejected rows remain a separate later data-policy decision. |
| Migration boundary | V18 remains current and immutable. ES-V01 changed no Flyway migration. |
| Production boundary | Issue #43 remains open/deferred; LiteBans remains authoritative; no production data, production shadow, authority, cutover, deployment, or source-data rewrite was performed. |
| Newly READY from ES-V01 completion | None. ES-A01 remains deferred/blocked by ES-V02, ES-V03, owner authorization, and issue #43. |
| Exact next sequential-worker action | Start a new worker, reconcile live GitHub, then select `ES-P07 — Inventory and Ender editing runtime completion` as the highest-priority `READY` package if live state still agrees. This ES-V01 worker must not start it. |

## ES-V01 terminal result

ES-V01 is `COMPLETE`. The representative private LiteBans source remained local throughout. The recovered UUID-only compatibility repair now has synthetic coverage for UUID-backed bans and mutes while retaining the IP-only ban path. The final frozen exact head passed hosted Java 21/full tests, MariaDB/Testcontainers, static analysis, canonical public→private Pi staging, substantive review closure, and zero-valid-unresolved-thread requirements before PR #110 was normally merged.

Two non-passing staging attempts on the final head remain part of the truthful audit trail. Private run `31353309582` reached trusted provenance but failed before any completed Paper/storage-ready cycle during shared-host resource contention. A rerun then produced private run `31353848239`, whose provenance guard rejected an attempt-number mismatch before Paper executed. Neither is counted as passing evidence. Fresh canonical public run `31353964382` / private run `31354311211` subsequently passed the full contract without changing the frozen source head.

Containment is proven because `9a6c7240a4f6fffd216af0239709867b79080ddc` has `de39e30232df9bd44d4b4df54a8922e815bada76` as its second parent and no unique feature-tree delta remains. GitHub auto-deleted `package/es-v01-litebans-private-verification` after merge.

## ES-P05 terminal result

ES-P05 is complete. The final implementation head passed exact hosted/static/review/canonical public→private Pi proof and PR #81 merged normally. The implementation branch was safely auto-deleted after containment. Documentation-only completion PR #107 and owner-evidence routing correction PR #108 then merged normally.

The routing correction was necessary because owner-provided LiteBans evidence was not available to the concurrent PR #107 finalizer and because live provider reconciliation still shows the RoseChat repository unresolved. No ES-P05 product code, tests, migration, runtime configuration or workflow was changed by the routing correction.

## Owner-provided LiteBans evidence boundary

The supplied private local run used the repository migration service against a private MariaDB 10.11.6 LiteBans copy. It found a UUID-only ban/mute compatibility defect and produced local fix `22934e33`. After the fix, 153 supported sanctions imported and replayed idempotently without duplicate cases/events or mapped timestamp/expiry mismatches. No private database or row data is stored here.

The seven rejected malformed source/history rows remain unresolved policy input before rehearsal: 2 `INVALID_SOURCE_ROW` and 5 `INVALID_HISTORY_ROW`. They do not authorize silent skipping, source rewriting, production shadow/cutover, issue #43 acceptance, or authority changes.

## Stop boundary

ES-V01 is terminal `COMPLETE`. Do not activate, prepare, stage, or partially implement ES-P07, ES-P06, ES-X01, or any other package in this worker. A later sequential worker must reconcile live state before selecting the next package.
