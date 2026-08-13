# Workspace state

Last updated: 2026-08-13

Live GitHub overrides stale records. Detailed package evidence remains in the registry, package records, canonical handoffs, and PR verification ledgers.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P06`, `ES-P07`, `ES-P08`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05`, `ES-R01`, `ES-R02`, `ES-V01` |
| Active package | `ES-X02 — EnthusiaCurrency destructive provider` remains `BLOCKED` / `PARKED_BLOCKED` only because canonical private Pi staging has not received a trusted runner. |
| Frozen Staff product head | `fbba02d10301b6bc6d80ada4ad7113f80ff95514` on aggregate PR #133; non-draft, mergeable, preserved unmerged. |
| Final standalone Currency main | `b922c5af30860a6c205f9ee16b817349a7677cd0`, reached through normal merges of Currency PRs #11, #12, and #13. |
| Standalone validation | Final Currency head `a968f04b09c11dc1816f2b802626adbcef0f73c8` passed exact branch-head Java 21 `mvn -B -ntp verify` (7 tests + shaded JAR), Codacy, review, and merged normally. |
| Aggregate hosted validation | Staff Coverage run `31692612391` passed full Java 21 multi-module build/tests, runtime-JAR/provider-leak inspection, JaCoCo, artifact upload, and Codacy coverage; Staff Codacy check `94423669170` has zero issues; zero valid unresolved review threads; Sentinel artifact run `31692612386` passed. |
| Canonical Pi state | Public run `31692610056` built and transferred the exact runtime and dispatched private run `31693194558`. Private job `94424932390` remains queued with `runner_id: 0`, empty runner name, and zero executed steps for `self-hosted/Linux/ARM64/enthusia-staging`. No Pi pass or product failure is claimed. |
| Infrastructure exception | None. ES-X02 has no explicit owner approval for `OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED`; the queued zero-execution job cannot be relabeled as completion. |
| Mirror/parity state | Pre-merge Git-object verification proves the Staff mirror is byte-identical to Currency `b922c5af...` for every standalone root object; component metadata remains `SYNC_PENDING` until Staff normal merge plus required post-merge `component_sync.py` parity. |
| Production boundary | Representative live destructive balances remain deliberately deferred to `ES-V03`; issue #43 remains open/deferred and LiteBans remains authoritative. |
| Exact next action | Resume ES-X02 as `ACTIONABLE_CONTINUATION` when the trusted Pi runner can allocate. First reconcile private run `31693194558` and public bridge `31692610056`; if they later completed, inspect exact private/public evidence and cleanup. Otherwise run one fresh exact-head canonical Pi only after the infrastructure condition changes. Require actual private execution before merging Staff PR #133. |

## Package boundary

Do not start ES-X03, ES-X04, ES-V03, or another package from this worker. Preserve Staff PR #133 and both repositories' legitimate unrelated work. No production data, deployment, shadow window, cutover, authority change, or private-data acceptance is authorized by ES-X02.
