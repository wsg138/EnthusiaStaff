# ES-X02 — EnthusiaCurrency destructive provider — Pi runner blocked handoff

Date: 2026-08-13
Status: `BLOCKED` / `PARKED_BLOCKED`
Classification: external infrastructure unavailable; no product failure; no exception approved

## Durable checkpoint

ES-X02 has completed standalone implementation/fixes and all non-Pi aggregate validation. Do not reopen old Codacy work or repeat superseded heads.

Final standalone Currency `main`: `b922c5af30860a6c205f9ee16b817349a7677cd0`, reached by normal merge commits from PRs #11, #12, and #13. Final standalone validation head `a968f04b09c11dc1816f2b802626adbcef0f73c8` passed exact branch-head Java 21 `mvn -B -ntp verify` (run `31692395919`, job `94422400756`, 7 tests, shaded JAR), Codacy suite `85973637978`, review, and mergeability.

Aggregate Staff product PR #133 is open/non-draft/mergeable at exact frozen head `fbba02d10301b6bc6d80ada4ad7113f80ff95514`. The mirrored Currency tree is Git-object-identical to standalone main for all standalone root objects; only aggregate-only `COMPONENT-METADATA.md` is extra. Do not modify or merge PR #133 until canonical Pi succeeds.

## Green final aggregate evidence

- Coverage run `31692612391`, job `94423135991`: full Java 21 clean multi-module build/tests, runtime JARs/provider-leak checks, JaCoCo, validation artifact, and Codacy coverage upload all passed. Coverage: 48.98% lines / 40.05% branches / 51.52% instructions. Validation artifact `9178197820`.
- Runtime JARs: Paper SHA-256 `a142d0c30cbe4d085dea0901287f1d1bf9d84cb2143a0322091afb908342c6a6`; Velocity SHA-256 `c891d4744ed142edffa0352b4c20f39428fbc379c46313dbbe234878345ec1c7`; provider leaks 0.
- Staff Codacy check `94423669170`: success, zero issues/annotations.
- Review: all valid findings fixed in standalone and re-imported; zero valid unresolved GitHub review threads. Final CodeRabbit status is success/rate-limited with no new finding.
- Sentinel artifact run `31692612386`, job `94423077006`: success; artifact `9178016407`.

## Canonical Pi blocker

Public canonical Pi run `31692610056` successfully built/verified the exact Staff runtime, uploaded the runtime package, published the bounded transient transfer, dispatched the private self-hosted workflow, and located private run `31693194558`.

Private job `94424932390` is named `Verify bridge and boot/restart runtime on Lincoln-PI-4` and requires labels `self-hosted`, `Linux`, `ARM64`, `enthusia-staging`. At publication it remains queued with:

- `runner_id: 0`
- `runner_name: ""`
- `runner_group_id: 0`
- `steps: []`

Therefore no private Paper boot/restart, migration, MariaDB/Flyway, persistence, shutdown scan, cleanup, process-reap, or evidence-upload step has executed. This is infrastructure-unavailable evidence only. It is not `PASS`, not a product failure, and cannot be substituted by the successful public build.

No explicit owner approval exists for `OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED` on ES-X02. Do not self-approve or infer one from the package request.

## Exact resume procedure

1. Reconcile live Staff/Currency heads, PR #133, public run `31692610056`, and private run `31693194558` before doing anything else.
2. If private job `94424932390` has since allocated/completed, inspect the job/run logs and sanitized evidence. Require trusted runner identity plus all applicable runtime, database/migration, restart, persistence, guarded cleanup/process-reap, evidence sanitization, and public transfer-cleanup assertions. Do not infer success from a green public build or job title.
3. If the existing run timed out/cancelled while still `runner_id: 0`, retain that as non-passing zero-execution history. Only issue one fresh exact-head canonical Pi after concrete evidence that the trusted runner condition changed; do not repeatedly probe an unchanged unavailable runner.
4. On real Pi success, re-check PR #133 head remains exactly `fbba02d10301b6bc6d80ada4ad7113f80ff95514`, mergeable, Codacy-clean, review-clean, and that all required exact-head evidence still applies.
5. Merge PR #133 normally with expected head `fbba02d...`; no squash/rebase/force/auto-merge.
6. Run post-merge `tools/component-sync/component_sync.py compare` against exact Currency main `b922c5af30860a6c205f9ee16b817349a7677cd0`. Require no added/removed/modified product file and record hashes/manifests/merge SHAs.
7. Update component metadata to `IN_SYNC`, publish ES-X02 `COMPLETE` in registry/package/workspace/latest handoff, verify containment and safe branch cleanup, then stop. Do not start ES-X03/ES-X04 in the same worker.

Representative live destructive balances remain explicitly deferred to `ES-V03`. No production balances, private evidence rows, production authority, cutover, or issue #43 change belongs in ES-X02.
