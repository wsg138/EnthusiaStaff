# `ES-P11` — Fake-base generation and cleanup

## 1. Package identity
`ES-P11`; Internal; primary `COMP-STAFF`; priority 90; sequential after tester system.

## 2. Status
`REVIEW` — implementation, direct tests, Wiki/runbook, and package self-review are complete on PR #88; final exact-head hosted/static/reviewer gates, normal merge, containment, cleanup, and terminal publication remain.

## 3. Objective
Implement bounded, auditable fake-base generation, access control, lifecycle, and complete cleanup without risking real-world or player data.

## 4. Why the package exists
`AUD-TESTER-003` required a separate world-safety design after generic tester infrastructure.

## 5. Included audit IDs
`AUD-TESTER-003`.

## 6. Included behavior
Approved bounded templates/placement; staff authorization; isolated ownership; timeout/cancel/completion/failure/reload/restart cleanup; conflict detection; audit; leakage prevention; recovery of abandoned operations.

## 7. Explicit exclusions
General world rollback; CoreProtect replacement; unbounded generation; production deployment; unrelated tester features.

## 8. Dependencies
`ES-P10` is `COMPLETE`.

## 9. Component and repository boundaries
Root fake-base/world-safety/tester/tests/resources/docs only. No external component, permanent branch, or isolated PR.

## 10. Required branches
Active temporary branch: `package/es-p11-fake-bases`; delete after verified merge containment.

## 11. Required PRs
Implementation PR #88: `ES-P11: fake-base generation and cleanup` targeting `main`. It remains the only product PR for ES-P11 and is ready to leave draft after the review-state checkpoint is complete.

## 12. Implementation checklist
- [x] Fresh live GitHub reconciliation and dependency/branch/migration ownership check.
- [x] Activate package from exact legitimate `main` `68a6d936066383f5b8139304f40b2d01d0dfe036`.
- [x] Reconcile Paper/Folia world APIs; define templates/bounds/safety/recovery.
- [x] Implement bounded client-only generation and cleanup state machine.
- [x] Add direct template/placement/lifecycle/audit/metadata tests plus MariaDB/Testcontainers audit-ledger integration coverage.
- [x] Document commands, permissions, limits, recovery, privacy, migration boundary, and ES-V02 acceptance boundary.
- [x] Harsh self-review and repair concurrency, Folia ownership, authorization, render/restore, scheduler, audit, and thread-affinity findings.
- [ ] Freeze exact head; complete actual reviewer/static/hosted gates; reconcile newer `main`; merge normally; verify containment; delete implementation branch; publish terminal state.

## 13. Acceptance criteria
Generation cannot overwrite protected/real data; every operation is bounded/owned/audited; cleanup is idempotent after all termination modes; abandoned state is detected/recovered; ordinary players cannot access operator controls or persistent artifacts.

## 14. Test requirements
Placement/conflict/bounds, chunk/world ownership, cancellation/timeout/completion, partial generation, reload/restart/process interruption, duplicate cleanup, permission, and scheduler/thread behavior. Direct pure and integration coverage now exercise the deterministic safety contracts; live Java/Bedrock/distributed presentation remains ES-V02 by contract.

## 15. Static-analysis requirements
All configured analysis/review gates with zero valid unresolved findings on one frozen exact head before merge.

## 16. Documentation requirements
Commands/permissions/templates/limits, world-safety and recovery runbook, exclusions, Wiki, package state/handoff, and implementation evidence report are present. Final exact-head/merge evidence remains to be recorded during terminal publication.

## 17. Security and privacy requirements
Strict staff authorization; no player-base targeting or private-coordinate evidence; fail closed when ownership/safety is uncertain. Async control actions re-check current authority before commit, and durable audit events intentionally exclude fake-base coordinates.

## 18. Migration impact
No migration added. ES-P11 uses the existing `audit_events` ledger for coordinate-free lifecycle evidence and keeps virtual-operation state in memory because client-only blocks cannot persist a process/server interruption. V18 remains immutable.

## 19. Bedrock considerations
Staff controls have command/text fallback through `/cheattester base`; suspect-facing rendering uses Paper's supported multi-block-change API rather than Java-only NMS/ProtocolLib packet internals. Representative Java/Bedrock acceptance remains ES-V02.

## 20. Distributed-runtime considerations
Operations require the target to be online on the local backend, stay within one already-loaded target chunk through final placement validation, never load/generate chunks, are locally target-fenced, and make no shared-world mutation. Backend transfer/disconnect clears the client-only view and terminates local ownership.

## 21. External-provider considerations
No protection-provider bypass is used because ES-P11 never changes world blocks. Placement reads authoritative real block state and refuses any occupied template cell or unsafe floor; missing durable audit storage fails creation closed.

## 22. Completion definition
All generation/cleanup safety criteria proven; one exact-head implementation PR merged normally; no valid review threads; exact containment verified; temporary implementation branch deleted; terminal package state published to `main` through the established documentation-only finalization pattern.

## 23. Resume state
Current worker owns PR #88 / `package/es-p11-fake-bases`. Product implementation and self-review are complete. The branch is in `REVIEW`; live GitHub head is authoritative because review-state documentation commits follow product hardening commit `c1fcf737aed2a9993d5c49a86457f999c1172752`.

## 24. Last completed checkpoint
Implemented and self-reviewed a fixed bounded 7x7 client-only template; one-loaded-chunk conflict/safe-floor planner; five-minute lifecycle with one-minute warning; Extend/Clear/Teleport/Status controls; strict staff-mode permissions and commit-time control revalidation; exact global/per-staff/target limits; distance/world/disconnect/staff-exit/plugin-disable/render/scheduler cleanup; authoritative real-block restoration; coordinate-free durable audit events through existing `audit_events`; and text/permission metadata. Review fixes closed approximate registration, canonical JSON, expiry-read consistency, worker-thread Bukkit access, virtual render/restore failure handling, target cross-chunk Folia reads, stale async authority, accepted-vs-committed control evidence, and post-render lifecycle scheduler failure. A complete pre-freeze head `a0fc7c63b547cfa84a89aa116c4297d2c0b25f36` passed Java 21 build/tests/MariaDB/Testcontainers/runtime-JAR and Wiki as diagnostic evidence, but it is not final evidence because later review fixes changed product code.

## 25. Remaining checklist
Publish this review checkpoint across registry/workspace/handoffs, mark PR #88 ready, obtain an actual CodeRabbit/reviewer pass rather than the draft-skip status, reconcile all live review threads, freeze one exact head, require Wiki/full Java 21 build-tests-MariaDB-Testcontainers-runtime-JAR/static/coverage gates on that exact head, reconcile fresh `main` and migration ownership, merge normally, verify containment, delete `package/es-p11-fake-bases`, publish terminal COMPLETE state, and stop.

## 26. Known blockers
None for implementation or hosted review. Private representative Java/Bedrock/distributed acceptance remains assigned to `ES-V02`; unchanged account-level private staging failures are external evidence and are not retried here without a material condition change.

## 27. Final evidence
Pending frozen exact head. Current non-final diagnostic evidence: complete pre-freeze head `a0fc7c63b547cfa84a89aa116c4297d2c0b25f36` passed Wiki run `31233405241` and Coverage run `31233405218` / job `93041499563`, including full Java 21 build/tests/MariaDB/Testcontainers/runtime-JAR/coverage upload; Codacy reported zero new issues. That head was deliberately not frozen because self-review subsequently found and fixed correctness races. Final reviewer/static/coverage/run identifiers must correspond to the later frozen head.

## 28. Merge and synchronization record
Pending: normal implementation merge/resulting `main`, exact containment, implementation branch deletion, and terminal documentation publication. External parity is not applicable.

Canonical implementation evidence: `reports/ES-P11-FAKE-BASE-IMPLEMENTATION.md`.
Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-07-es-p11-fake-bases.md`.
