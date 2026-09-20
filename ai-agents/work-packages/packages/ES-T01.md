# ES-T01 — Random staff-teleport stale-target hardening

## 1. Package identity
`ES-T01`; owner-authorized test-hardening repair; internal `COMP-STAFF`; priority 5; parallel-safe only while `paper/.../staff/**` remains disjoint from active package work.

## 2. Status
`ACTIVE` / `ACTIONABLE_CONTINUATION`.

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
- [ ] Run exact-head hosted/static validation.
- [ ] Harsh-review final diff and resolve valid findings.
- [ ] Merge normally, verify containment, publish terminal state, clean branch when safe.

## 13. Acceptance criteria
A target that was eligible during collection but is invalid at final selection is not teleported to; another candidate is tried when available; the selected target location is captured only after final revalidation; actor authorization is still checked before teleport; no active package work is overwritten.

## 14. Test requirements
Focused `:paper:test` coverage including target-policy state changes; full applicable Java 21 repository validation on the exact final PR head; `git diff --check`; changed-code complexity/method-length checks. Sentinel/canonical Pi apply only if current validation policy and exact changed scope require them after live reconciliation.

## 15. Static-analysis requirements
Zero new valid Codacy/PMD findings in changed code. No broad suppressions.

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
One normal-merge PR; focused and full applicable tests green on exact head; zero valid unresolved review/static findings; merge containment verified; canonical state published.

## 23. Resume state
Worker: automatic owner-authorized testing-hardening worker. Branch: `package/es-t01-staff-teleport-hardening`. PR: #215. Initial product checkpoint `d69b48a8088c8562b6d0a6ccd39a005f95266e4a` was superseded by the same-package Folia-safety follow-up; the current live PR head becomes the next product candidate after push. Start `main`: `c1054da6a8f89b312df2e05e25edc958fceda7ef`.

## 24. Last completed checkpoint
Concrete stale-target defect repaired, plus review found and repaired a same-service Folia issue where UUIDs were read from the global scheduler. Java 21.0.6 focused tests and full `:paper:test` pass after both repairs; Lizard reports zero threshold violations at CCN <= 8, method length <= 50, and arguments <= 8; `git diff --check` passes. The orchestration validator still reports pre-existing legacy Dxx/X03 record-format errors, while its own 8 unit tests pass and T01 adds no new reported orchestration error.

## 25. Remaining checklist
Open/checkpoint the PR, complete exact-head hosted/static/review gates, normal merge, containment, and terminal publication.

## 26. Known blockers
`NONE` at package claim. Active X03/D08/D09/D13 paths are explicitly excluded and collision preflight is clear for the two changed product/test files.

## 27. Final evidence
Pending exact-head validation and review.

## 28. Merge and synchronization record
Pending. Internal package; external parity is not applicable.
