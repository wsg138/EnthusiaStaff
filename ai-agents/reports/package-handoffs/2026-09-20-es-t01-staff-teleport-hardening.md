# ES-T01 — staff teleport hardening handoff

Status: `PARKED_BLOCKED`.

Owner authorization: 2026-09-20 request to create review/bug-finding/fixing work useful for immediate plugin testing.

Start `main`: `c1054da6a8f89b312df2e05e25edc958fceda7ef`.
Branch: `package/es-t01-staff-teleport-hardening`.
PR: #215.
Frozen executable product head: `12bfd267a2d9d2dd3709060300efd6d2bc3a220b`. Any later state-only documentation commit preserves this executable candidate and its exact-head runtime evidence.

Collision preflight: active X03 PR #139, D08 PR #214, parked D09 PR #203, and parked D13 PR #178 do not own `paper/src/main/java/net/enthusia/staff/paper/staff/StaffToolRandomTeleportService.java` or `paper/src/test/java/net/enthusia/staff/paper/staff/StaffToolTargetPolicyTest.java`. No migration is added.

Finding `T01-STAFF-RTP-001`: candidate eligibility and location were captured during the initial multi-player collection phase. The later random choice reused that stale snapshot without revalidating the selected target, allowing a player that had since become vanished/frozen/staff/exempt/otherwise invalid to remain a teleport destination.

Repair: store eligible IDs during collection, shuffle once, then revalidate the selected target on its entity scheduler. Invalid/offline candidates are discarded and the next candidate is attempted. The current target location is captured only after that final revalidation. Actor authorization is still rechecked on the actor scheduler before `teleportAsync`.

Finding `T01-STAFF-RTP-002`: candidate collection ran on the global scheduler and called `Player#getUniqueId()` for every online player there. The staff-tools menu in the same subsystem already documents the safer rule: enumerate player references globally, then read mutable/entity identity state on the player's entity scheduler. Repair: candidate collection now snapshots `Player` references only, schedules each candidate directly on its entity scheduler, reads UUID/state there, and uses an atomic completion guard so retirement and execution cannot decrement the collection twice.

Regression coverage now exercises stale/invalid targets, reconnect identity, scheduler rejection/throw/retirement/duplicate-callback boundaries, authorization revalidation, retry exhaustion, and teleport failure/cancellation paths in addition to target-policy state changes.

Local validation after the repairs: Java 21.0.6 focused tests PASS; full `:paper:test` PASS; Lizard zero warnings at CCN <= 8 / method length <= 50 / args <= 8; `git diff --check` PASS. The repository orchestration validator reports pre-existing Dxx/X03 format debt, while its 8 validator unit tests pass and no T01-specific error is emitted.

Exact-head executable evidence on `12bfd267a2d9d2dd3709060300efd6d2bc3a220b`:
- Coverage run `35640745316`: `SUCCESS`.
- Sentinel Restart Artifact run `35640745025`: `SUCCESS`.
- live Sentinel restart job `506`: `PASSED` / `PAPER_RESTART_OK`.

Codacy is not cleared. Check run `106469630650` reports exactly one new `MEDIUM` issue in category `Performance`, but GitHub's available check/PR evidence exposes only that aggregate and no individual rule/path/line. The Codacy issue surface is not available through the authorized connector. Repository policy requires inspection of the exact finding before fixing or declaring a false positive, so no speculative source edit and no broad suppression/exclusion was made. Codacy disposition is therefore **UNRESOLVED / EVIDENCE-BLOCKED**, and zero new valid findings has not yet been established. Unblock by surfacing/exporting the individual rule/path/line/message or restoring an authorized analyzer/evidence surface; then narrowly fix the finding if valid or document that exact finding if false. Any product-code change creates a new executable head and invalidates the corresponding exact-head gates.

Canonical Pi is also not cleared. Exact-head Pi staging run `35641111973` successfully built the Paper runtime for `12bfd267a2d9d2dd3709060300efd6d2bc3a220b`, then failed in `Revalidate exact candidate and supersede stale staging` before private dispatch because access to `wsg138/EnthusiaStaff-Staging` returned `HTTP 401: Bad credentials`. The publish/dispatch/private-runtime steps were skipped, so no private staging run exists and this is neither a product failure nor a staging pass. The bridge was intentionally not rerun without evidence of credential repair.

Fresh evidence confirms the credential condition has not changed: at 2026-09-21 20:21 UTC, Pi Staging Supersession run `35650514529` for another package again failed while using `STAGING_TOKEN`, with `GitHub API GET /actions/workflows/plugin-live-test.yml/runs... failed: HTTP 401` and `Bad credentials`. Exact Pi unblock: repair/rotate `ENTHUSIASTAFF_STAGING_TOKEN`/the staging token used by the workflows so it authenticates to `wsg138/EnthusiaStaff-Staging` with the required Actions access; after Codacy is reconciled and the executable candidate is frozen, run canonical Pi exactly once and require an actual successful private staging result plus cleanup.

PR #215 remains open and must not merge while either blocker remains. Do not disturb X03/D08/D09/D13, V21/V22 ownership, private staging configuration, production authority, or issue #43. Because the owner prohibited creation of another branch/package/PR for this continuation, the parked-state record stays on this existing ES-T01 branch/PR.
