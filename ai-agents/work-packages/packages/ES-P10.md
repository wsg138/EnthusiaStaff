# `ES-P10` — Cheat tester and fake-entity system

## 1. Package identity
`ES-P10`; Internal; primary `COMP-STAFF`; priority 80; conditional parallel safety only after staff-tool dispatch.

## 2. Status
`COMPLETE` as of 2026-08-07. PR #86 merged normally, exact containment was verified, the implementation branch was deleted, and terminal state publication is the final documentation-only checkpoint. Registry is authoritative.

## 3. Objective
Implement an auditable staff cheat-tester workflow and bounded fake-player/entity tooling with deterministic lifecycle and cleanup.

## 4. Why the package exists
The audit found only an inert staff item and no command, service, packet implementation, lifecycle, cleanup, or tests.

## 5. Included audit IDs
`AUD-TESTER-001`, `AUD-TESTER-002`.

## 6. Included behavior
Commands/permissions/configuration; real staff-tool dispatch; packet/API abstraction; fake entity creation/visibility/control; evidence/audit; timeout/cancel/disconnect/reload/shutdown cleanup; duplicate prevention; safe staff control.

## 7. Explicit exclusions
Fake bases (`ES-P11`); automatic punishment; unsupported unisolated NMS/packet internals; production use.

## 8. Dependencies
`ES-P04` is `COMPLETE`.

## 9. Component and repository boundaries
Root tester/staff/Paper/domain/tests/resources/docs only. No external source import, permanent component branch, or isolated PR.

## 10. Required branches
Temporary implementation branch `package/es-p10-cheat-testers` was used and is deleted after verified merge containment. The documentation-only terminal publication branch is not product work and must also be removed after its normal merge.

## 11. Required PRs
Implementation PR #86 to `wsg138/EnthusiaStaff:main`, merged normally as `e605d8ad6094b2ae6842044d209875e13c38906d`.

## 12. Implementation checklist
Complete: reconcile supported APIs; define threat/use cases and bounds; implement authorized workflow/lifecycle/cleanup; test termination/failure paths; document; harsh review; resolve live review findings; freeze and validate exact head; merge normally; verify containment; clean up implementation branch; publish terminal state.

## 13. Acceptance criteria
Satisfied for the development-package boundary: only authorized staff can create/control testers; entities are bounded and correctly scoped; ordinary players do not receive unintended staff-state disclosure; timeout/cancel/disconnect/reload/shutdown preserves durable recovery and exact-restoration guarantees; no automatic sanction occurs. Representative private Java/Bedrock/distributed acceptance remains assigned to `ES-V02` and is not claimed here.

## 14. Test requirements
Covered by command/permission, lifecycle, visibility/audience, duplicate/limit, disconnect/reload/shutdown, API-unavailable, evidence/audit, scheduler/thread, configuration, persistence, and Bedrock-safe text-fallback tests. Direct package tests include command filtering; tester type/settings and non-finite bounds; lifecycle journal/cancellation phase ordering; evidence formatting/bounds; fake-entity bookkeeping; provider-unavailable behavior; control-state authorization/capacity; strict nested configuration keys; journal-completion revision retry; staff-tool authorization; configuration reload boundaries; MariaDB global-fence/restart/audit/migration behavior; and durable inventory-lock participation.

## 15. Static-analysis requirements
Satisfied on frozen head `1997caa864847049d51bfc58402f019e0a0d65c6`: Codacy static check `93015840928` succeeded with zero issues. The oversized manager was structurally decomposed into focused fake-entity state/support and durable-completion collaborators rather than suppressed. All live PR review threads were resolved or outdated; zero valid unresolved threads remained at merge.

## 16. Documentation requirements
Complete: commands, permissions, configuration, operational limits, evidence/privacy, cleanup/recovery, repository-managed Wiki, `reports/ES-P10-CHEAT-TESTER-IMPLEMENTATION.md`, package registry, workspace state, and canonical handoff.

## 17. Security and privacy requirements
Strict authorization; bounded entities/evidence; no hidden staff/private data leakage; fail closed if packet/provider support is absent. Fake-entity evidence counts only target interactions; controlling-staff observer interaction is excluded from suspect evidence. `/cheattester status` requires tester permission and active staff mode before session identities are exposed.

## 18. Migration impact
ES-P10 owns immutable forward migration `V18__cheat_tester_session_journal.sql`. V1–V17 are untouched. V18 stores bounded recovery/evidence state, revision/lifecycle metadata, and a globally unique nullable active-target fence. Payload columns are sized for validated character bounds under utf8mb4. Clean-install, upgrade, checksum, restart, and uniqueness behavior are covered by repository migration/integration tests. Aggregate `main` now includes V18.

## 19. Bedrock considerations
Staff controls include a text fallback and do not rely on Java-only GUI interaction. Representative Java/Bedrock visibility/usability acceptance remains explicitly assigned to `ES-V02`; ES-P10 does not claim unavailable private staging as a pass.

## 20. Distributed-runtime considerations
The V18 active-target fence prevents concurrent testing of one target across backend processes. Active durable tester rows participate in the existing inventory-lock contract so disconnect/restart recovery cannot race offline asset mutation. Login/respawn recovery resolves the globally active target row rather than only rows owned by the current backend, preserving exact restoration after backend loss. Client-side fake entities are backend-local and nonpersistent.

## 21. External-provider considerations
ProtocolLib 5.4.0 is the supported packet boundary already present in the Paper build. `FakeEntityAdapter` isolates packet behavior; absence, disable, linkage failure, spawn failure, or destroy failure is fail-closed. A destroy failure propagates after fail-closing so unverifiable cleanup cannot be incorrectly terminalized. No direct NMS dependency was introduced.

## 22. Completion definition
Satisfied. The tester/fake-entity scope owned by ES-P10 is implemented, tested, documented, reviewed, exact-head validated, normally merged, contained, and cleaned up. Deferred representative staging belongs to ES-V02 by contract and does not reopen ES-P10 absent a later validated defect.

## 23. Resume state
Terminal package; do not resume as ordinary ES-P10 work. Starting legitimate `main`: `83302749b3247f7a05157f1625fc99da6aa43736`. Frozen implementation head: `1997caa864847049d51bfc58402f019e0a0d65c6`. Product merge: `e605d8ad6094b2ae6842044d209875e13c38906d`. Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-07-es-p10-cheat-testers.md`.

## 24. Last completed checkpoint
The final static cleanup structurally extracted fake-entity support from `CheatTesterManager` and resolved all remaining Codacy findings. Frozen head `1997caa864847049d51bfc58402f019e0a0d65c6` then passed the complete unchanged-head hosted gate set. PR #86 merged normally as `e605d8ad6094b2ae6842044d209875e13c38906d`; compare from frozen head to merged `main` is one commit ahead, zero behind, with zero file differences; `package/es-p10-cheat-testers` was automatically deleted and returns 404.

## 25. Remaining checklist
None for ES-P10 product work. Finish the documentation-only terminal publication, verify its normal merge/containment/branch cleanup, persist `COMPLETE` to `main`, recompute dependency-derived readiness without activating ES-P11, and stop.

## 26. Known blockers
No ES-P10 product blocker. Exact frozen-head private staging run `31224339373` did not execute product validation: Ubuntu job `93015447468` had runner ID `0`, empty runner name, and `steps: []`; Pi `93015487565` was skipped. This is **NOT A PASS** and no ES-P10 infrastructure exception was claimed. Representative Java/Bedrock/distributed acceptance remains assigned to `ES-V02`. ES-P02 and ES-P05 remain separately parked on their own required private gates.

## 27. Final evidence
- Starting legitimate main: `83302749b3247f7a05157f1625fc99da6aa43736`.
- Frozen implementation head: `1997caa864847049d51bfc58402f019e0a0d65c6`.
- Wiki/metadata check `93015435354`: success.
- Java 21 workflow `31224336640`, unchanged-head rerun job `93016497496`: success for build/tests/MariaDB/Testcontainers/migrations/aggregate JaCoCo/runtime-JAR inspection/artifact/Codacy coverage upload.
- Validation artifact `9011777818`, SHA-256 `4557877edff2589065483f4d2d81f0c231511001ad04ea82d5b36cfbba1c762a`.
- Aggregate JaCoCo: 2,576 / 5,427 lines covered (47.47%); 796 / 1,887 branches covered (42.18%).
- Codacy static `93015840928`: success, zero issues.
- Coverage variation `93017546035`: success at `-0.98%` against `-1.0%` allowed.
- Diff coverage `93017545807`: success at `29.91%`, no gate defined.
- Review: zero valid unresolved live review threads at merge.
- First workflow attempt: unrelated MariaDB concurrent-update failure in already-merged punishment-request-alert integration test; no ES-P10 code changed; unchanged-head rerun succeeded and is the counted evidence.
- Exact private staging: run `31224339373`, Ubuntu `93015447468` zero-runner/no steps, Pi `93015487565` skipped — **NOT A PASS**, retained by ES-V02.
- Product merge: PR #86 -> `e605d8ad6094b2ae6842044d209875e13c38906d`.
- Containment: merged main is one merge commit ahead of frozen head, zero behind, zero file differences.
- Implementation branch cleanup: `package/es-p10-cheat-testers` deleted; branch lookup returns 404.

## 28. Merge and synchronization record
PR #86 merged normally as `e605d8ad6094b2ae6842044d209875e13c38906d`. Exact containment is verified and implementation-branch cleanup is complete. External mirror parity is not applicable. Dependency recomputation makes `ES-P11` `READY`, but ES-P11 was not activated by this worker.
