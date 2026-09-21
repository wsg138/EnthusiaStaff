# ES-T01 — Random staff-teleport stale-target hardening

## 1. Package identity
`ES-T01`; owner-authorized test-hardening repair; internal `COMP-STAFF`; priority 5; parallel-safe only while `paper/.../staff/**` remains disjoint from active package work.

## 2. Status
`PARKED_BLOCKED`.

Frozen executable/validation candidate: `991315dbe3f90c3a46842ca63a8ae6a76a716572`. The executable production tree is unchanged from the previously validated `12bfd267a2d9d2dd3709060300efd6d2bc3a220b`; commits after that point contain package-state documentation and a test-only Codacy remediation.

## 3. Objective
Remove stale-target behavior from the random staff teleport tool before hands-on test-server validation begins.

## 4. Why the package exists
On 2026-09-20 the owner explicitly authorized creation of review/bug-finding/fixing packages to improve readiness for immediate plugin testing. Review found that random teleport validated targets during candidate collection but later teleported to a stored location without revalidating the selected target.

## 5. Included audit IDs
`T01-STAFF-RTP-001` — execution-time target revalidation and stale-location avoidance.

`T01-STAFF-RTP-002` — move candidate identity/state reads off the global scheduler and onto each target entity scheduler.

## 6. Included behavior
Enumerate only player references globally, then read candidate identity/state on each target entity scheduler; revalidate the selected target on its entity scheduler immediately before actor teleport orchestration; reject targets that became staff-mode, vanished, frozen, exempt, dead, sleeping, mounted, spectator, or located in a disabled world; retry another candidate when a selected target becomes invalid/offline; capture the live location only after successful revalidation; preserve actor authorization revalidation and asynchronous teleport failure handling.

## 7. Explicit exclusions
No X03/D08/D09/D13 files, migrations, Discord behavior, provider integration, staff-mode persistence redesign, production deployment, authority change, cutover, or issue #216 finding.

## 8. Dependencies
`ES-P04` is `COMPLETE`. Owner authorization on 2026-09-20 permits this independent test-hardening repair.

## 9. Component and repository boundaries
Only `wsg138/EnthusiaStaff`; product changes are limited to `paper/.../staff/StaffToolRandomTeleportService.java` and focused staff-tool tests plus canonical package-state documentation.

## 10. Required branches
`package/es-t01-staff-teleport-hardening` from exact start `c1054da6a8f89b312df2e05e25edc958fceda7ef`.

## 11. Required PRs
One normal-merge PR to `EnthusiaStaff:main`: PR #215.

## 12. Implementation checklist
- [x] Reconcile live `main`, open PRs, migrations, parked packages, and collision paths.
- [x] Confirm no active package owned the random-teleport production/test paths at implementation time.
- [x] Implement execution-time target revalidation and fallback retry.
- [x] Move candidate identity/state reads onto each target entity scheduler.
- [x] Add focused success/rejection/retry/retirement/reconnect/authorization/teleport-failure coverage.
- [x] Run focused Java 21 tests and full applicable Paper tests.
- [x] Run changed-code complexity/method-length checks and `git diff --check`.
- [x] Open and continue PR #215 only.
- [x] Clear the hosted Codacy finding without suppression.
- [x] Run exact-head Coverage and Sentinel Artifact validation on `991315dbe3f90c3a46842ca63a8ae6a76a716572`.
- [ ] Restore the approved canonical staging credential.
- [ ] After credential repair, reconcile current `main` normally, freeze the resulting head, rerun every invalidated exact-head gate, and run canonical Pi exactly once.
- [ ] Merge normally only after every package-required gate is terminal green, then verify containment and publish `COMPLETE`.

## 13. Acceptance criteria
A target that was eligible during collection but is invalid at final selection is not teleported to; another candidate is tried when available; the selected target location is captured only after final revalidation; actor authorization is still checked before teleport; no active package work is overwritten.

## 14. Test requirements
Focused `:paper:test` coverage including target-policy state changes; full applicable Java 21 repository validation on the exact final executable PR head; `git diff --check`; changed-code complexity/method-length checks. Sentinel and canonical Pi remain package-required under the reconciled validation policy for this runtime-sensitive change.

## 15. Static-analysis requirements
Zero new valid Codacy/PMD findings in changed code. No broad suppressions. The former single `MEDIUM` Performance finding was validly remediated in test code by moving loop-local `Harness` construction into per-case helper methods and hoisting the repeated behavior list. Hosted Codacy Static Code Analysis check `106544003807` on `991315dbe3f90c3a46842ca63a8ae6a76a716572` is `SUCCESS` with zero annotations; the PR summary reports `0` new issues. Codacy Diff Coverage `106546409672` and Coverage Variation `106546409662` are also `SUCCESS`.

## 16. Documentation requirements
Registry, workspace state, package handoff, latest handoff, PR evidence, and terminal state.

## 17. Security and privacy requirements
No private player data, production data, credentials, raw logs, or secrets.

## 18. Migration impact
None. At this checkpoint no ES-T01 migration exists or is owned.

## 19. Bedrock considerations
No protocol-specific UI change; target eligibility remains based on server-side player state and permissions for Java and Bedrock players alike.

## 20. Distributed-runtime considerations
All mutable target reads occur on the target entity scheduler; actor authorization and teleport start occur on the actor entity scheduler. Offline/retired targets fail closed into bounded fallback selection.

## 21. External-provider considerations
None.

## 22. Completion definition
One normal-merge PR; focused and full applicable tests green on the final executable head; zero valid unresolved review/static findings; canonical Pi private staging green; merge containment verified; canonical state published.

## 23. Resume state
Branch: `package/es-t01-staff-teleport-hardening`. PR: #215. Frozen executable/validation candidate: `991315dbe3f90c3a46842ca63a8ae6a76a716572`. Start `main`: `c1054da6a8f89b312df2e05e25edc958fceda7ef`.

The production implementation remains the one already validated at `12bfd267a2d9d2dd3709060300efd6d2bc3a220b`. Between that commit and `991315d...`, GitHub compare shows only this package/handoff documentation plus `StaffToolRandomTeleportServiceTest.java`; no production Java changed.

## 24. Last completed checkpoint
The stale-target and Folia ownership defects are repaired. Local Java 21 focused tests and full `:paper:test` passed; changed production code stayed within the practical complexity/method bounds and `git diff --check` passed.

Exact hosted evidence for `991315dbe3f90c3a46842ca63a8ae6a76a716572`:
- Coverage run `35663450629`: `SUCCESS`.
- Sentinel Restart Artifact run `35663450669`: `SUCCESS`.
- Codacy Static Code Analysis `106544003807`: `SUCCESS`, zero annotations / zero new issues.
- Codacy Diff Coverage `106546409672`: `SUCCESS`, `78.9%` diff coverage.
- Codacy Coverage Variation `106546409662`: `SUCCESS`, `+0.18%` variation.
- Review threads: none.

The prior live Sentinel job `506` passed on executable head `12bfd267a2d9d2dd3709060300efd6d2bc3a220b` with `PAPER_RESTART_OK`. A fresh restart command (`5768485647`) and status query (`5768569846`) were issued after the test-only Codacy repair, but the Sentinel bot has not published a new result. This is not represented as a fresh exact-SHA pass. Hosted artifact comparison provides supporting executable-equivalence evidence: the Paper JARs built from `12bfd267...` and `991315d...` contain the same 4,641 unpacked runtime files with zero byte-content differences; only archive/build metadata changes the container hashes.

## 25. Remaining checklist
The Codacy blocker is cleared. The remaining package blocker is the canonical staging credential. After it is legitimately repaired, reconcile the then-current `main` into this branch with a normal merge (never rebase/squash/force-push), rerun every exact-head gate invalidated by that reconciliation, run canonical Pi exactly once on the final frozen candidate, require an actual successful private staging result plus cleanup, then perform final reconciliation and normal merge.

Current `main` at this state publication has advanced to `4ffab626f4f044ef03adcf2d41c25690e1bdaa71` via PR #223. ES-T01 is intentionally not churning its blocked branch merely to absorb moving `main`; that reconciliation belongs immediately after the external credential prerequisite is restored and before final validation/merge.

## 26. Known blockers
1. **Canonical Pi staging credential blocker.** Original ES-T01 run `35641111973` built the exact Paper runtime successfully, then failed before private dispatch because access to `wsg138/EnthusiaStaff-Staging` returned `HTTP 401: Bad credentials`; no private staging run occurred.
2. The condition remains current. Pi Staging Supersession run `35663448866` on `991315dbe3f90c3a46842ca63a8ae6a76a716572` again failed while using `STAGING_TOKEN` with `HTTP 401: Bad credentials` before any private staging execution.

Exact unblock: the staging owner must rotate or replace `ENTHUSIASTAFF_STAGING_TOKEN` with the approved least-privilege automation credential that can read Actions workflow history and dispatch the required private workflow in `wsg138/EnthusiaStaff-Staging`. Repository policy explicitly forbids substituting a personal credential or bypassing the public bridge. The GitHub connector available to this worker does not expose Actions-secret mutation APIs, so this credential cannot be repaired from this worker session.

## 27. Final evidence
Frozen executable/validation candidate `991315dbe3f90c3a46842ca63a8ae6a76a716572`:
- Coverage `35663450629`: `SUCCESS`.
- Sentinel Restart Artifact `35663450669`: `SUCCESS`.
- Codacy Static `106544003807`: `SUCCESS`, zero annotations.
- Codacy diff/variation checks: `SUCCESS`.
- PR review threads: zero.
- Prior unchanged-runtime live Sentinel: job `506`, `PASSED` / `PAPER_RESTART_OK` at `12bfd267...`; no fresh bot result is claimed for `991315d...`.
- Canonical Pi: not rerun because the required credential is still demonstrably invalid; fresh supersession `35663448866` proves the same 401 condition remains.

## 28. Merge and synchronization record
`BLOCKED`; PR #215 remains open and must not merge while the canonical Pi credential prerequisite remains unresolved. No direct-main push, rebase, squash, force-push, auto-merge, replacement branch/package/PR, or issue #216 finding was used.
