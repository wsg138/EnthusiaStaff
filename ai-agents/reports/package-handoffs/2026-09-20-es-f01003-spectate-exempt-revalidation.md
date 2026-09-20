# ES-F01003 handoff — spectate target protection revalidation

Date: 2026-09-20

## Scope and claim
- Issue: `#216`, finding `R01-003` only.
- Claim: `CLAIM R01-003 by ES-F01003/package/es-f01003-spectate-exempt-revalidation`, comment `5752608695`.
- Start `main`: `c1054da6a8f89b312df2e05e25edc958fceda7ef`.
- Branch: `package/es-f01003-spectate-exempt-revalidation`.
- PR: `#222`.

## Reconciled concurrent ownership
- T01 / PR #215: `StaffToolRandomTeleportService` plus focused tests and shared canonical package-state files; preserved.
- D08 / PR #214: cross-platform punishment/policy/runtime paths; preserved.
- D09 / PR #203: investigation/case paths plus branch-local V22; preserved.
- D13 / PR #178: Discord role-sync paths; preserved.
- X03 / PR #139: broad Market work plus branch-local V21; preserved.
- Review-fix claims active when this package was selected were reconciled from issue #216. None owned `StaffToolDispatcher` or this package's focused tests.
- Live Staff migration ceiling at selection: V20. X03 retains V21 ownership; D09 retains V22 ownership. ES-F01003 adds no migration.

## Reconstructed failure
`StaffToolDispatcher.inspectSpectateTarget(...)` checked vanish and `enthusiastaff.stafftools.spectate-exempt` only at selection time. `followSnapshot(...)` later teleported from the stored target snapshot without reacquiring target protection. After teleport, `prepareSpectatorAttachment(...)` reacquired the target but checked only vanish. A permission/rank update granting the explicit exemption during either asynchronous window could therefore be ignored.

## Implemented repair
- `followSnapshot(...)` now reacquires the target through `onEntity(...)` before teleport admission.
- `revalidateFollowTarget(...)` checks current vanish and spectate-exempt state on the target owner and fails closed before handing back to the actor.
- `teleportAfterTargetRevalidation(...)` rechecks current actor staff-session/tool authority on the actor owner immediately before `teleportAsync(...)`.
- `prepareSpectatorAttachment(...)` now applies the same current target-protection predicate on the target owner before scheduling direct attachment.
- Existing retirement callbacks remain authoritative for target disconnect/scheduler retirement; no direct off-owner fallback was added.
- `StaffToolSpectateTargetPolicy` isolates the small protection decision for direct behavioral coverage.

## Tests and analysis completed locally
Exact product commit before this handoff: `c78b9b7d4b57ac8b595fe8803b40d08b311066d0`.

On the isolated worktree using Java 21:
- `gradlew.bat :paper:test --no-daemon --no-build-cache --no-configuration-cache --console=plain` — PASS.
- Focused spectate policy and wiring regressions are included in that Paper suite.
- `git diff --check c1054da6...HEAD` — PASS.
- Lizard on changed production classes — zero warnings; all new methods CCN <= 2 and <= 11 lines; existing class maximum remains within repository practical CCN <= 8 / 50-line method limits.
- A local `clean build jacocoAggregateReport runtimeJars` attempt reached the MariaDB/Testcontainers integration suite but failed initialization because the Windows machine has no valid Docker environment. No integration product result is claimed from that attempt; hosted container-capable validation remains required.

## Hosted evidence state
- PR #222 exact product head `c78b9b7d...` automatically started Coverage run `35537457946` and Sentinel Restart Artifact run `35537457997`.
- Sentinel Restart Artifact `35537457997` completed successfully for that product head.
- Coverage/full hosted validation remained in progress when this handoff checkpoint was written; no pass is claimed yet.
- CodeRabbit status on the product head was successful and PR review-thread reconciliation exposed zero threads at the checkpoint; final exact-head review/Codacy reconciliation is still required.

## Privacy and production boundary
No production deployment, private player data access, credentials, raw logs, production configuration mutation, LiteBans authority change, issue #43 acceptance, or unrelated package behavior is included.

## Collision-safe package state
The package writes only this unique handoff and `ES-F01003.md`. Shared `PACKAGE-REGISTRY.md`, `WORKSPACE-STATE.md`, and `reports/agent-handoffs/latest.md` remain untouched while T01 owns those paths. Issue #216 remains the durable live routing authority until shared state can be serialized safely.

## Exact next action
Treat the commit containing this handoff as the new candidate head. Require fresh exact-head hosted build/test/runtime-artifact validation, Codacy zero new valid findings, zero valid unresolved review findings, and any repository-policy-applicable runtime/staging gates. If any valid defect appears, repair only R01-003 scope and repeat exact-head validation. Immediately before merge, re-read live `main`, issue #216 claims/fixes, all open PR changed paths, migration ownership, and shared package-state ownership. Merge normally only if every required gate is green and containment remains collision-safe.
