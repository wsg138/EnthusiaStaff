# `ES-P11` — Fake-base generation and cleanup

## 1. Package identity
`ES-P11`; Internal; primary `COMP-STAFF`; priority 90; sequential after tester system.

## 2. Status
`ACTIVE` — selected 2026-08-07 after fresh live reconciliation from legitimate `main` `68a6d936066383f5b8139304f40b2d01d0dfe036`.

## 3. Objective
Implement bounded, auditable fake-base generation, access control, lifecycle, and complete cleanup without risking real-world or player data.

## 4. Why the package exists
`AUD-TESTER-003` is not started and needs a separate world-safety design after generic tester infrastructure.

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
PR #88: `ES-P11: fake-base generation and cleanup` targeting `main`; currently draft while implementation/review/validation continue.

## 12. Implementation checklist
- [x] Fresh live GitHub reconciliation and dependency/branch/migration ownership check.
- [x] Activate package from exact legitimate `main`.
- [x] Reconcile Paper/Folia world APIs; define templates/bounds/safety/recovery.
- [x] Implement generation and cleanup state machine.
- [ ] Add complete failure/lifecycle/access/scheduler tests. Pure template, placement, lifecycle, and audit-model tests are committed; runtime wiring/permission/shutdown coverage remains.
- [ ] Document commands/limits/recovery and update requirement evidence.
- [ ] Review, freeze exact head, validate, merge normally, verify containment, publish terminal state, delete temporary branch.

## 13. Acceptance criteria
Generation cannot overwrite protected/real data; every operation is bounded/owned/audited; cleanup is idempotent after all termination modes; abandoned state is detected/recovered; ordinary players cannot access operator controls or persistent artifacts.

## 14. Test requirements
Placement/conflict/bounds, chunk/world ownership, cancellation/timeout/completion, partial generation, reload/restart/process interruption, duplicate cleanup, permission, and scheduler/thread tests.

## 15. Static-analysis requirements
All configured analysis/review gates with zero valid unresolved findings.

## 16. Documentation requirements
Commands/permissions/templates/limits, world-safety and recovery runbook, exclusions, Wiki, package state/handoff.

## 17. Security and privacy requirements
Strict staff authorization; no player-base targeting or private-coordinate evidence; fail closed when ownership/safety is uncertain.

## 18. Migration impact
No migration added. ES-P11 uses the existing `audit_events` ledger for coordinate-free lifecycle evidence and keeps virtual-operation state in memory because client-only blocks cannot persist a process/server interruption. V18 remains immutable.

## 19. Bedrock considerations
Staff controls have command/text fallback through `/cheattester base`; the suspect-facing implementation uses Paper's supported multi-block-change API rather than Java-only NMS/ProtocolLib packet internals. Representative Java/Bedrock acceptance remains ES-V02.

## 20. Distributed-runtime considerations
Operations require the target to be online on the local backend, stay within one already-loaded target chunk, never load/generate chunks, are locally target-fenced, and make no shared-world mutation. Backend transfer/disconnect clears the client-only view and terminates local ownership.

## 21. External-provider considerations
No protection-provider bypass is used because ES-P11 never changes world blocks. Placement reads authoritative real block state and refuses any occupied template cell or unsafe floor; missing durable audit storage fails creation closed.

## 22. Completion definition
All generation/cleanup safety criteria proven; one exact-head PR merged normally; no valid threads; temporary branch cleanup verified.

## 23. Resume state
Current worker owns PR #88 / `package/es-p11-fake-bases`. Implementation head at this checkpoint is `9a0b698d71c1a620f328634b3f0c3b33cb0fc00f`; newer test/documentation commits may follow and live GitHub remains authoritative.

## 24. Last completed checkpoint
Implemented a fixed bounded 7x7 client-only template, one-loaded-chunk conflict/safe-floor planner, five-minute lifecycle with four-minute warning, Extend/Clear/Teleport controls, strict staff-mode permissions, exact global/per-staff registration bounds, distance/world/disconnect/staff-exit/plugin-disable cleanup, real-block restoration, and coordinate-free durable audit events using the existing audit ledger. Direct pure tests cover template bounds, placement conflicts/chunk/world-height constraints, lifecycle warning/expiry/extension/idempotency, and audit-model bounds. Draft PR #88 is open. First pre-test workflow attempts were superseded/cancelled by subsequent commits and are not validation evidence.

## 25. Remaining checklist
Complete runtime permission/scheduler/shutdown tests, resolve compile/test findings, document Wiki/runbook/requirements/audit evidence, harsh self-review, freeze exact head, run required exact-head hosted/static/review gates, merge normally, verify containment, publish terminal state, and delete the temporary branch.

## 26. Known blockers
None for implementation. Private representative Java/Bedrock/distributed acceptance remains assigned to `ES-V02`; unchanged account-level private staging failures are external evidence and are not retried here without a material condition change.

## 27. Final evidence
Pending final exact head. Current design/API evidence: Paper 1.21.11 supported `Player.sendMultiBlockChange`, entity scheduler, region scheduler, and async teleport APIs; V18 remains migration boundary; client-only virtual blocks are restored from authoritative real block data. CI/review evidence is not yet frozen.

## 28. Merge and synchronization record
Pending: merge/resulting main/containment/temporary branch deletion; parity not applicable.

Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-07-es-p11-fake-bases.md`.
