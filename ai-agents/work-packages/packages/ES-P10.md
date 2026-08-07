# `ES-P10` — Cheat tester and fake-entity system

## 1. Package identity
`ES-P10`; Internal; primary `COMP-STAFF`; priority 80; conditional parallel safety only after staff-tool dispatch.

## 2. Status
`MERGE_PENDING` as of 2026-08-07. Product implementation, tests, configuration, Wiki/operator documentation, V18 persistence, and pre-freeze harsh review are complete on `package/es-p10-cheat-testers`; exact-head merge gates are the remaining package work. Registry is authoritative.

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
Temporary `package/es-p10-cheat-testers`; delete after verified merge containment.

## 11. Required PRs
One PR to `wsg138/EnthusiaStaff:main`: PR #86.

## 12. Implementation checklist
Completed: reconcile supported APIs; define threat/use cases and bounds; implement authorized workflow/lifecycle/cleanup; test termination/failure paths; document; update state/handoff; harsh review. Remaining: freeze the final tracked head, satisfy all applicable exact-head gates, merge normally, verify containment, publish terminal records, and clean up the temporary branch.

## 13. Acceptance criteria
Only authorized staff can create/control testers; entities are bounded and correctly scoped; ordinary players cannot infer sensitive staff state beyond intended test behavior; timeout/cancel/disconnect/reload/shutdown leaves no untracked mutable state; no auto-sanction occurs.

## 14. Test requirements
Command/permission, lifecycle, visibility/audience, duplicate/limit, disconnect/reload/shutdown, API unavailable, evidence/audit, scheduler/thread, and Bedrock-safe control tests. Direct package tests now include command filtering, tester type/settings, lifecycle journal/cancellation phase ordering, evidence formatting/bounds, provider-unavailable behavior, staff-tool authorization, configuration reload boundaries, and MariaDB global-fence/restart/audit/migration behavior.

## 15. Static-analysis requirements
All configured Java/static-analysis/review-bot gates; zero valid unresolved findings. Pre-freeze Codacy findings were reduced structurally rather than waived: command/snapshot/configuration complexity was extracted, the tester runtime was split into session/probe/evidence/mutation-guard responsibilities, and the durable journal store was simplified without changing transaction semantics.

## 16. Documentation requirements
Commands, permissions, configuration, operational limits, evidence/privacy, cleanup/recovery, Wiki, registry/handoff. Repository-managed Wiki and `reports/ES-P10-CHEAT-TESTER-IMPLEMENTATION.md` document the implemented boundary.

## 17. Security and privacy requirements
Strict authorization; bounded entities/evidence; no hidden staff/private data leakage; fail closed if packet/provider support is absent. Fake-entity evidence counts only target interactions; controlling-staff observer interaction is excluded from suspect evidence.

## 18. Migration impact
ES-P10 owns immutable forward migration `V18__cheat_tester_session_journal.sql`. V1–V17 remain untouched. V18 stores bounded recovery/evidence state, revision/lifecycle metadata, and a globally unique nullable active-target fence. Clean-install, upgrade, checksum, restart, and uniqueness behavior are covered by the repository migration/integration suite.

## 19. Bedrock considerations
Staff controls include a text fallback and do not rely on Java-only GUI interaction. Representative Java/Bedrock visibility/usability acceptance remains assigned to `ES-V02`; ES-P10 does not claim that private acceptance.

## 20. Distributed-runtime considerations
The V18 active-target fence prevents concurrent testing of one target across backend processes. Active durable tester rows participate in the existing inventory-lock contract so disconnect/restart recovery cannot race offline asset mutation. Client-side fake entities are backend-local and nonpersistent.

## 21. External-provider considerations
ProtocolLib 5.4.0 is the supported packet boundary already present in the Paper build. `FakeEntityAdapter` isolates packet behavior; absence, disable, or runtime failure is fail-closed. No direct NMS dependency was introduced.

## 22. Completion definition
Tester/fake-entity scope fully implemented/tested/documented; one exact-head PR merges normally; zero valid threads; branch cleanup verified. This definition is not satisfied until PR #86 merges and terminal publication/cleanup complete.

## 23. Resume state
Selected from legitimate `main` `83302749b3247f7a05157f1625fc99da6aa43736` by `ChatGPT sequential package worker`. Active branch: `package/es-p10-cheat-testers`. PR #86 remains the only ES-P10 implementation PR. Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-07-es-p10-cheat-testers.md`.

## 24. Last completed checkpoint
Implementation and harsh pre-freeze review are complete. The package implements the four release probes, optional client-side fake entities, `/cheattester` and staff-tool controls, bounded/restart-only configuration, exact snapshot/restoration, V18 journal recovery, global duplicate fencing, evidence-only audit, and ProtocolLib fail-closed behavior. Review found and fixed a cancellation/journal race so cancellation before/during durable start cannot produce post-cancel mutation or an orphan ACTIVE row. Review also excluded controlling-staff fake-entity interactions from suspect evidence. Static-analysis-driven extraction split the large runtime into focused responsibilities.

## 25. Remaining checklist
Freeze the final content head after package-state publication; require successful exact-head hosted build/tests/MariaDB/Testcontainers/migration/runtime-JAR/Wiki/static/coverage gates; require zero valid unresolved review threads; record the private Pi/distributed zero-runner result as **NOT A PASS** and deferred to `ES-V02`; mark PR #86 ready; merge normally; verify exact containment; delete the implementation branch; publish terminal package/registry/workspace/handoff state without activating ES-P11; stop.

## 26. Known blockers
No product-code blocker is known. Private representative Java/Bedrock/distributed acceptance remains assigned to `ES-V02` and is not a prerequisite for this ordinary development package. The private Actions account-level Billing & plans condition continues to produce runner ID `0` / no-step staging attempts; such attempts are unavailable infrastructure evidence, never a pass. ES-P02 and ES-P05 remain separately parked on their own required private gates.

## 27. Final evidence
Starting main: `83302749b3247f7a05157f1625fc99da6aa43736`. Starting migration boundary: V17; ES-P10 adds V18. Protocol API decision: ProtocolLib 5.4.0 through a focused adapter; no unisolated NMS. Final frozen-head check IDs, merge SHA, containment, and branch deletion belong in the terminal publication after validation/merge so the evidence can refer to the exact unchanged product head.

## 28. Merge and synchronization record
PR #86 is `MERGE_PENDING`. Merge/resulting main/containment/temporary branch deletion are pending. External parity is not applicable.
