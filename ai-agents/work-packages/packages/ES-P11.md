# `ES-P11` — Fake-base generation and cleanup

## 1. Package identity
`ES-P11`; Internal; primary `COMP-STAFF`; priority 90; sequential after tester system.

## 2. Status
`REVIEW` — implementation, direct/integration tests, Wiki/runbook, harsh self-review, and actual CodeRabbit finding reconciliation are complete on PR #88. The latest valid review defect was fixed at `3564d6942e669f9c79c7c952a32285f98f46fcf3`; one final state checkpoint and exact-head gate cycle remain before normal merge.

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
Active temporary branch: `package/es-p11-fake-bases`; delete only after verified normal-merge containment.

## 11. Required PRs
Implementation PR #88: `ES-P11: fake-base generation and cleanup` targeting `main`. It is non-draft; merge remains forbidden until the final frozen-head gates are clean.

## 12. Implementation checklist
- [x] Fresh live GitHub reconciliation and dependency/branch/migration ownership check.
- [x] Activate/resume package from legitimate `main` `68a6d936066383f5b8139304f40b2d01d0dfe036`.
- [x] Reconcile Paper/Folia world APIs; define templates/bounds/safety/recovery.
- [x] Implement bounded client-only generation and cleanup state machine.
- [x] Add direct template/placement/lifecycle/audit/metadata tests plus MariaDB/Testcontainers audit-ledger integration coverage.
- [x] Document commands, permissions, limits, recovery, privacy, migration boundary, and ES-V02 acceptance boundary.
- [x] Harsh self-review and repair concurrency, Folia ownership, authorization, render/restore, scheduler, audit, thread-affinity, permission, and test findings.
- [x] Obtain actual CodeRabbit reviews and resolve every valid finding, including render/close cleanup and refusal to extend an already-expired operation.
- [ ] Publish the final state checkpoint, freeze its exact head, complete exact-head hosted/static/reviewer gates, reconcile fresh `main`, merge normally, verify containment/merge shape, delete implementation branch, and publish terminal state.

## 13. Acceptance criteria
Generation cannot overwrite protected/real data; every operation is bounded/owned/audited; cleanup is idempotent after all termination modes; expired operations cannot be revived by Extend; abandoned state is detected/recovered; ordinary players cannot access operator controls or persistent artifacts.

## 14. Test requirements
Placement/conflict/bounds, chunk/world ownership, cancellation/timeout/completion, partial generation, reload/restart/process interruption, duplicate cleanup, permission, scheduler/thread behavior, warning/extension state, and extension refusal at/after expiry. Live Java/Bedrock/distributed presentation remains ES-V02 by contract.

## 15. Static-analysis requirements
All configured analysis/review gates with zero valid unresolved findings on one frozen exact head before merge.

## 16. Documentation requirements
Commands/permissions/templates/limits, world-safety and recovery runbook, exclusions, Wiki, package state/handoff, and implementation evidence report are present. Final exact-head/merge evidence is recorded during terminal publication.

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
All generation/cleanup safety criteria proven; one exact-head implementation PR merged with a normal two-parent merge commit; exact feature-head containment verified; live merge result recorded; temporary implementation branch deleted; terminal package state published to `main` through the established documentation-only finalization pattern.

## 23. Resume state
Current worker owns PR #88 / `package/es-p11-fake-bases`. Product and review hardening are complete through `3564d6942e669f9c79c7c952a32285f98f46fcf3`. A final state-only checkpoint follows this commit and becomes the candidate frozen head unless a gate finds another valid defect. Legitimate `main` was still `68a6d936066383f5b8139304f40b2d01d0dfe036` at the last reconciliation.

## 24. Last completed checkpoint
The second actual CodeRabbit review found that Extend could revive an operation after its deadline but before the lifecycle tick closed it. `3564d6942e669f9c79c7c952a32285f98f46fcf3` now rejects Extend when `now` is equal to or later than `expiresAt` and adds direct deadline/after-deadline coverage. The same checkpoint expanded `WORKSPACE-STATE.md` to require the full canonical exact-head validation list and explicit post-merge two-parent/main-SHA/containment/live-evidence verification before branch cleanup. Earlier review fixes remain intact: exact admission, canonical JSON, warning/extension serialization, worker/region-thread Bukkit safety, stale Folia/authority guards, accepted-vs-committed evidence, scheduler failure cleanup, render/close race recovery, permission inheritance, template-height coupling, semantic metadata tests, and audit assertion hardening.

## 25. Remaining checklist
Publish this reconciled state checkpoint; freeze that exact SHA; require exact-head Wiki, full Java 21 build/tests/MariaDB/Testcontainers/migration/runtime-JAR/artifact/coverage, Codacy static/coverage, CodeRabbit/reviewer completion, and zero valid unresolved findings; reconcile fresh `main`, PR/branch/issue/migration state; merge PR #88 normally; verify exact merge commit/two parents/resulting `main`/feature-head containment/live evidence; only then delete `package/es-p11-fake-bases`; publish terminal COMPLETE state and stop.

## 26. Known blockers
None for ES-P11 implementation or hosted validation. Representative Java/Bedrock/distributed private/Pi acceptance remains assigned to `ES-V02` and is not an ES-P11 completion gate. Automatic private attempts under the unchanged Billing & plans condition are **NOT A PASS** and must be recorded as unavailable evidence, not as successful staging or an owner-approved ES-P11 exception.

## 27. Final evidence
Pending the post-checkpoint frozen exact head. Earlier passing heads are diagnostic only because later review fixes changed tracked content. Exact final run/check/review IDs must correspond to the later frozen SHA.

## 28. Merge and synchronization record
Pending: normal implementation merge/resulting `main`, two-parent merge verification, exact containment, live post-merge evidence, implementation branch deletion, and terminal documentation publication. External parity is not applicable.

Canonical implementation evidence: `reports/ES-P11-FAKE-BASE-IMPLEMENTATION.md`.
Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-07-es-p11-fake-bases.md`.
