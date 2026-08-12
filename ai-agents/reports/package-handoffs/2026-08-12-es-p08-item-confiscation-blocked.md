# ES-P08 — Item confiscation and restoration — blocked handoff

Status: `BLOCKED`; classification: `PARKED_BLOCKED`.

This handoff publishes the true unmerged ES-P08 continuation state to `main` while preserving implementation PR #128 and `package/es-p08-item-confiscation`. It does not call the implementation complete and does not authorize another package.

## Live package identity

- Package start / pre-publication `main`: `7c032c6af32f7281f518a01ed6dc3b0252cabb5b`.
- Implementation branch: `package/es-p08-item-confiscation`.
- Implementation PR: #128, open/non-draft/mergeable at the last reconciliation.
- Frozen product head: `27b20bb56e540161f695e624916f91620261457d`.
- V18 remains the immutable migration ceiling; ES-P08 adds no migration.
- Issue #43 remains open/deferred; LiteBans remains authoritative.

## Implemented scope

PR #128 contains Founder-authorized `/case recoveritems <case-id>` recovery for one coherent case-linked quarantined `CONFISCATION`/`RESTORE_CONFISCATED` operation while retaining the existing durable inventory journal, confiscated-asset snapshot, restoration reservation/finalization, nested-item identity, leases/fencing, checksum/revision, and restart/login recovery foundations.

Recovery fails closed on unresolved/non-Founder actor identity, case-target/profile divergence, paired state/profile/fence divergence, missing or mismatched stored quarantine resource evidence, resolved/missing quarantine evidence, live competing leases, and multiple unresolved candidates. Successful authorization only requeues the exact pair and records audit/resolution state; it never applies inventory. Normal fenced checksum/revision recovery remains the only application path. Re-quarantine clears resolution fields while preserving earlier authorization in append-only audit history.

Excluded provider work (`ES-X02`, `ES-X03`, `ES-X04`), production inventories, whole-server rollback, destructive representative/load acceptance (`ES-V03`), production data/cutover, and authority changes remain outside this package.

## Exact-head evidence already passed

All passing evidence below is bound to frozen product head `27b20bb56e540161f695e624916f91620261457d` and is recorded in PR #128's verification ledger.

- `Validate Wiki` run `31555952998` passed.
- Coverage run `31555953013`, job `93988340387`, passed Temurin Java 21 full build/tests including MariaDB/Testcontainers, warnings-as-errors, runtime JAR inspection, provider-leak checks, and aggregate JaCoCo. Aggregate coverage: 48.99% lines, 40.07% branches, 51.52% instructions.
- Codacy static check `93988413158` passed with zero issues. Coverage variation passed at +0.2% against the -1.0% target; diff coverage was 74.5% with no configured diff gate.
- All valid CodeRabbit findings are addressed; all review threads are resolved. Exact-head manual review found no additional valid release-blocking defect. The CodeRabbit docstring-coverage UI warning is advisory and is not counted as a repository-gate pass.
- `Sentinel Restart Artifact` run `31555953004` passed for the exact head.
- Canonical public Pi run `31555950970` attempt 1 passed and bridged to private run `31556350997`, job `93989465759`, on trusted ARM64 runner `Lincoln-PI-4`. It proved exact source/artifact provenance, V1–V18 fresh migration, V18 restart no-op, two storage-ready `SHADOW_MIGRATION` Paper cycles, clean stop/reap, zero critical runtime failure patterns, unrelated-service preservation, sanitized evidence, verified-disposable database cleanup, and public transfer cleanup.

Local shell network access to GitHub was unavailable in this worker environment, so no separate local Gradle run is claimed. Exact-head hosted Java 21 build/test evidence above is the executable build evidence.

## Blocking Sentinel evidence

The selected package additionally requires an independent live Sentinel restart to literal `PAPER_RESTART_OK`; canonical Pi success is not a substitute.

- Fresh same-head job `150` did not reach product acceptance. It ended `RESTART_CYCLE_1_RESOURCE_GATE_FAILED` because temperature was 80.3 C against the configured 80.0 C ceiling.
- Same-head job `151` remained host-resource-gated and ultimately timed out. It is explicitly non-passing infrastructure history.
- Same-head job `153` progressed further: restart cycle 1 completed, then Sentinel refused cycle 2 at `RESTART_CYCLE_2_RESOURCE_GATE_FAILED` because temperature was 81.8 C against the configured 80.0 C ceiling. It is also non-passing infrastructure history; one completed cycle is not the required two-cycle `PAPER_RESTART_OK` result.

The trusted Sentinel implementation rechecks host resources before each restart cycle. Therefore the current blocker remains unavailable execution environment, not a successful package test and not currently a demonstrated ES-P08 code defect.

## Exact unblock condition

Do not send repeated identical restart commands while the same resource condition persists. Preserve unrelated legitimate Pi work.

A future sequential worker must first reconcile live GitHub and Sentinel state. ES-P08 becomes `ACTIONABLE_CONTINUATION` only after concrete live evidence shows the trusted Sentinel resource condition changed enough to sustain the required two-cycle restart. Then, if no valid exact-head success already exists, run one fresh exact-head restart and require literal `PAPER_RESTART_OK`.

After that, verify `main`, PR #128, exact PR head, all review threads/checks, and exact-head `PAPER_RESTART_OK`. Only then normally merge PR #128. After merge verify two-parent merge identity, resulting `main`, frozen-head containment/divergence, and safe deletion/cleanup of `package/es-p08-item-confiscation`, record GitHub-generated facts in PR #128 metadata, mark ES-P08 `COMPLETE`, update dependency-derived statuses, and stop.

If the resource condition remains unchanged, keep ES-P08 `PARKED_BLOCKED`; do not merge it, do not call the Sentinel gate passed, and do not activate `ES-X02`, whose dependency remains incomplete.

## Production and stop boundary

No production data was read or written; no deployment, shadow window, source rewrite, private-data acceptance, LiteBans authority change, issue #43 acceptance, or cutover occurred. This worker publishes blocker/routing state only after exhausting meaningful same-head work permitted by repository policy, then stops without beginning a second package.
