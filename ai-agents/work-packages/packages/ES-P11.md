# `ES-P11` — Fake-base generation and cleanup

## 1. Package identity
`ES-P11`; Internal; primary `COMP-STAFF`; priority 90; sequential after tester system.

## 2. Status
`COMPLETE` — implementation PR #88 merged normally as `6cd293d9f1abc3ca6ca8b70e953da936f4a22ab0` from frozen head `a3192dd5f684d402b79dfee2de3f32e18af7c9c4`; exact containment and implementation-branch cleanup are verified. This documentation-only publication records terminal package state and does not activate another package.

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
Root fake-base/world-safety/tester/tests/resources/docs only. No external component or parity branch was required.

## 10. Required branches
Implementation branch `package/es-p11-fake-bases` was deleted only after verified normal-merge containment. Terminal publication uses the temporary documentation-only branch `package/es-p11-terminal-state`, which must be deleted after its verified merge.

## 11. Required PRs
Implementation PR #88: `ES-P11: fake-base generation and cleanup`, merged to `main` with a normal merge commit. Terminal package-state publication is documentation-only.

## 12. Implementation checklist
- [x] Fresh live GitHub reconciliation and dependency/branch/migration ownership check.
- [x] Resume the only live `ACTIONABLE_CONTINUATION` from legitimate `main` `68a6d936066383f5b8139304f40b2d01d0dfe036`.
- [x] Reconcile Paper/Folia world APIs and define templates/bounds/safety/recovery.
- [x] Implement bounded client-only generation and cleanup state machine.
- [x] Add direct template/placement/lifecycle/audit/metadata tests plus MariaDB/Testcontainers audit-ledger integration coverage.
- [x] Document commands, permissions, limits, recovery, privacy, migration boundary, and ES-V02 acceptance boundary.
- [x] Harsh self-review and repair concurrency, Folia ownership, authorization, render/restore, scheduler, audit, thread-affinity, permission, and test findings.
- [x] Obtain actual CodeRabbit reviews and resolve every valid finding, including render/close cleanup and refusal to extend an already-expired operation.
- [x] Resolve configured Codacy static findings from 23 to zero without weakening world-safety/authorization boundaries.
- [x] Freeze exact state-inclusive implementation head `a3192dd5f684d402b79dfee2de3f32e18af7c9c4`.
- [x] Pass exact-head Wiki, Java 21 build/tests, MariaDB/Testcontainers, migration, runtime-JAR, artifact, Codacy static/coverage gates.
- [x] Record an actual exact-head reviewer completion with zero valid unresolved findings; do not misrepresent CodeRabbit's quota-limited final rerun as passed.
- [x] Reconcile fresh `main`, PR/branch, issue #43, and migration ownership before merge.
- [x] Merge PR #88 normally as `6cd293d9f1abc3ca6ca8b70e953da936f4a22ab0`; verify two-parent shape and exact feature-head containment.
- [x] Verify implementation branch deletion after containment.
- [x] Publish terminal state without activating another package.

## 13. Acceptance criteria
Generation cannot overwrite protected/real data; every operation is bounded/owned/audited; cleanup is idempotent after all termination modes; expired operations cannot be revived by Extend; abandoned state is detected/recovered; ordinary players cannot access operator controls or persistent artifacts.

## 14. Test requirements
Placement/conflict/bounds, chunk/world ownership, cancellation/timeout/completion, partial generation, reload/restart/process interruption, duplicate cleanup, permission, scheduler/thread behavior, warning/extension state, and extension refusal at/after expiry. Live Java/Bedrock/distributed presentation remains ES-V02 by contract.

## 15. Static-analysis requirements
Satisfied on frozen head `a3192dd5f684d402b79dfee2de3f32e18af7c9c4`: configured static/coverage checks passed and no valid unresolved review finding remained.

## 16. Documentation requirements
Commands/permissions/templates/limits, world-safety and recovery runbook, exclusions, Wiki, package state/handoff, and implementation evidence are present. Terminal merge/containment evidence is recorded here and in the workspace/handoff records.

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
Satisfied: generation/cleanup safety criteria proven; one exact-head implementation PR merged with a normal two-parent merge commit; exact feature-head containment verified; live merge result recorded; temporary implementation branch deleted; terminal package state published through the established documentation-only finalization pattern.

## 23. Resume state
Terminal `COMPLETE`. No ES-P11 implementation work remains. Reopen only if later ES-V02 representative Java/Bedrock/distributed acceptance exposes a genuine ES-P11 defect. This worker must not claim the next package.

## 24. Frozen exact-head evidence
Frozen implementation head: `a3192dd5f684d402b79dfee2de3f32e18af7c9c4`.

- Wiki run `31241442832` / job `93063008371`: success.
- Java 21 build/coverage run `31241442786` / job `93063008372`: exact SHA checkout, clean build/tests, MariaDB/Testcontainers integration, migration validation, runtime-JAR inspection, artifact publication, aggregate JaCoCo, and Codacy upload; `BUILD SUCCESSFUL`.
- Aggregate coverage: 47.01% lines / 38.14% branches / 49.64% instructions.
- Paper runtime JAR: 9,123,435 bytes, SHA-256 `0c3d66fb328a041c650968f39d83d4015340142b438200417db30005fa3448fb`; provider API leaks 0.
- Velocity runtime JAR: 7,863,915 bytes, SHA-256 `79fda01365bae9cfdc9faf75e2c1bfbc068bd43eec82d84c065cbf5a25bbfad9`; provider API leaks 0.
- Artifact `9017217821`, digest `sha256:9077a5e6054002663cc0588b7cb87b32de7869ec2881996488e8c06b500b3397`.
- Codacy static `93063097134`: success, zero annotations.
- Codacy Diff Coverage `93063654061`: success at 26.67%, with no configured diff-coverage gate.
- Codacy Coverage Variation `93063654099`: success at -0.45% against the -1.0% target.
- Exact-head manual reviewer completion review `4888204151`: PASS. CodeRabbit's requested final rerun was quota-limited; it is not claimed as an exact-head CodeRabbit pass. All review threads were resolved/outdated.

## 25. Private acceptance boundary
Exact frozen-head public wrapper run `31241441649` / check `93063005569` dispatched private staging run `31241446283`; required Ubuntu job `93063018565` had runner ID `0`, empty runner name, `steps: []`, and the Billing & plans payment/spending-limit rejection; Pi job `93063023369` was skipped. This is **NOT A PASS**, is not an ES-P11 completion gate, and is not an infrastructure exception. Representative Java/Bedrock/distributed acceptance remains `ES-V02`.

## 26. Merge and synchronization record
- Pre-merge legitimate `main`: `68a6d936066383f5b8139304f40b2d01d0dfe036`.
- Frozen implementation head: `a3192dd5f684d402b79dfee2de3f32e18af7c9c4`.
- Implementation PR #88 normal merge commit/resulting `main`: `6cd293d9f1abc3ca6ca8b70e953da936f4a22ab0`.
- Merge commit parents: `68a6d936066383f5b8139304f40b2d01d0dfe036` and `a3192dd5f684d402b79dfee2de3f32e18af7c9c4`.
- Containment: resulting `main` is one commit ahead, zero behind, with zero file differences from the frozen implementation head.
- `package/es-p11-fake-bases` was deleted after verification and returns 404.
- External parity: not applicable for internal `COMP-STAFF` scope.

## 27. Remaining package work
None. ES-V02 remains separately deferred and must not be relabeled as ES-P11 completion evidence. Issue #43 remains deferred; no production/cutover authority changed.

Canonical implementation evidence: `reports/ES-P11-FAKE-BASE-IMPLEMENTATION.md`.
Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-07-es-p11-fake-bases.md`.