# ES-T01 — Random staff-teleport stale-target hardening

## 1. Package identity
`ES-T01`; owner-authorized test-hardening repair; internal `COMP-STAFF`; priority 5; parallel-safe only while `paper/.../staff/**` remains disjoint from active package work.

## 2. Status
`PARKED_BLOCKED`.

Frozen executable product head: `12bfd267a2d9d2dd3709060300efd6d2bc3a220b`. Any later commit that only records package/handoff state does not supersede this executable candidate.

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
No X03/D08/D09/D13 files, migrations, Discord behavior, provider integration, staff-mode persistence redesign, production deployment, authority change, or cutover.

## 8. Dependencies
`ES-P04` is `COMPLETE`. Owner authorization on 2026-09-20 permits this independent test-hardening repair.

## 9. Component and repository boundaries
Only `wsg138/EnthusiaStaff`; product changes are limited to `paper/.../staff/StaffToolRandomTeleportService.java` and focused staff-tool tests plus canonical package-state documentation.

## 10. Required branches
`package/es-t01-staff-teleport-hardening` from exact start `c1054da6a8f89b312df2e05e25edc958fceda7ef`.

## 11. Required PRs
One normal-merge PR to `EnthusiaStaff:main`.

## 12. Implementation checklist
- [x] Reconcile live `main`, open PRs, migrations, parked packages, and collision paths.
- [x] Confirm no active package owns `paper/.../staff/StaffToolRandomTeleportService.java` or `StaffToolTargetPolicyTest.java`.
- [x] Create package branch from exact live `main`.
- [x] Implement execution-time target revalidation and fallback retry.
- [x] Add focused state-change regression coverage.
- [x] Run focused Java 21 tests and changed-code analyzers locally.
- [x] Open draft PR #215 for the product checkpoint.
- [x] Run executable exact-head Coverage and Sentinel validation on frozen product head `12bfd267a2d9d2dd3709060300efd6d2bc3a220b`.
- [ ] Resolve the one Codacy Performance finding after its individual rule/path/line evidence becomes available.
- [ ] Obtain a successful canonical Pi private-staging result after the staging credential is repaired.
- [ ] Harsh-review final diff and resolve every valid finding.
- [ ] Merge normally, verify containment, publish terminal state, clean branch when safe.

## 13. Acceptance criteria
A target that was eligible during collection but is invalid at final selection is not teleported to; another candidate is tried when available; the selected target location is captured only after final revalidation; actor authorization is still checked before teleport; no active package work is overwritten.

## 14. Test requirements
Focused `:paper:test` coverage including target-policy state changes; full applicable Java 21 repository validation on the exact final executable PR head; `git diff --check`; changed-code complexity/method-length checks. Sentinel and canonical Pi are package-required under the reconciled validation policy for this runtime-sensitive change.

## 15. Static-analysis requirements
Zero new valid Codacy/PMD findings in changed code. No broad suppressions. The current Codacy check exposes only an aggregate one-new-issue summary (`Performance`, `MEDIUM`) through the available GitHub evidence surface; the individual rule/path/line is not exposed. Per repository policy, that finding is not being guessed at, silently accepted, or broadly suppressed. Its validity remains unresolved until the individual evidence can be inspected.

## 16. Documentation requirements
Registry, workspace state, package handoff, latest handoff, PR evidence, and terminal state.

## 17. Security and privacy requirements
No private player data, production data, credentials, raw logs, or secrets.

## 18. Migration impact
None. Live `main` remains through V20; X03 owns branch-local V21 and D09 owns V22.

## 19. Bedrock considerations
No protocol-specific UI change; target eligibility remains based on server-side player state and permissions for Java and Bedrock players alike.

## 20. Distributed-runtime considerations
All mutable target reads occur on the target entity scheduler; actor authorization and teleport start occur on the actor entity scheduler. Offline/retired targets fail closed into bounded fallback selection.

## 21. External-provider considerations
None.

## 22. Completion definition
One normal-merge PR; focused and full applicable tests green on exact executable head; zero valid unresolved review/static findings; canonical Pi private staging green; merge containment verified; canonical state published.

## 23. Resume state
Worker: automatic owner-authorized testing-hardening worker. Branch: `package/es-t01-staff-teleport-hardening`. PR: #215. Initial product checkpoint `d69b48a8088c8562b6d0a6ccd39a005f95266e4a` was superseded by the same-package Folia-safety follow-up. Frozen executable product candidate: `12bfd267a2d9d2dd3709060300efd6d2bc3a220b`. Start `main`: `c1054da6a8f89b312df2e05e25edc958fceda7ef`.

## 24. Last completed checkpoint
The stale-target defect and same-service Folia issue were repaired. Java 21.0.6 focused tests and full `:paper:test` pass; Lizard reports zero threshold violations at CCN <= 8, method length <= 50, and arguments <= 8; `git diff --check` passes. On frozen executable head `12bfd267a2d9d2dd3709060300efd6d2bc3a220b`, hosted Coverage run `35640745316` succeeded, Sentinel Restart Artifact run `35640745025` succeeded, and live Sentinel restart job `506` passed with `PAPER_RESTART_OK`.

## 25. Remaining checklist
Inspect and individually disposition the exact Codacy finding; if a product fix is required, rerun every gate invalidated by the new product head. Repair the staging credential and then run canonical Pi exactly once on the frozen/current executable candidate, requiring an actual successful private staging result. Only after both blockers clear may normal merge and terminal publication proceed.

## 26. Known blockers
1. **Codacy evidence blocker.** Check run `106469630650` reports one new `MEDIUM` `Performance` issue, but the available GitHub check/PR evidence exposes no individual rule, path, or line and the Codacy issue surface is unavailable through the authorized connector. Repository policy requires individual evidence before a fix or false-positive suppression. No speculative product edit or broad exclusion has been made. Exact unblock: expose/export the individual Codacy finding (rule, path, line/message) or restore an authorized analyzer/evidence surface, then classify it; narrowly fix it if valid or document that exact finding if false.
2. **Canonical Pi staging credential blocker.** Exact-head run `35641111973` built the exact Paper runtime successfully, then failed before private dispatch while revalidating/superseding staging because access to `wsg138/EnthusiaStaff-Staging` returned `HTTP 401: Bad credentials`. The private staging run never occurred, so this is not a product failure and not a staging pass. Fresh repository-wide evidence at 2026-09-21 20:21 UTC still shows the staging credential failing with the same HTTP 401 in Pi Staging Supersession run `35650514529`; therefore ES-T01 staging was not rerun. Exact unblock: repair/rotate `ENTHUSIASTAFF_STAGING_TOKEN`/the staging token used by the workflows so it can authenticate to `wsg138/EnthusiaStaff-Staging` with the required Actions access, then run canonical Pi exactly once and require a real successful private result plus cleanup.

## 27. Final evidence
Frozen executable product head `12bfd267a2d9d2dd3709060300efd6d2bc3a220b`:
- Coverage `35640745316`: `SUCCESS`.
- Sentinel Restart Artifact `35640745025`: `SUCCESS`.
- live Sentinel restart job `506`: `PASSED` / `PAPER_RESTART_OK`.
- Codacy check `106469630650`: unresolved one-new-issue aggregate (`Performance`, `MEDIUM`); individual evidence unavailable, so zero-new-valid is not yet established.
- Canonical Pi `35641111973`: `FAILURE` before private dispatch due `HTTP 401: Bad credentials`; exact runtime build succeeded; no private staging result exists.

## 28. Merge and synchronization record
`BLOCKED`; PR #215 remains open and must not merge while either blocker remains. Internal package; external parity is not applicable. Because the owner explicitly prohibited creation of another branch/package/PR for this continuation, this parked-state record is kept on the existing ES-T01 branch/PR rather than opening a separate state-publication PR.
