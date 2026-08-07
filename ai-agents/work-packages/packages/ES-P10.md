# `ES-P10` — Cheat tester and fake-entity system

## 1. Package identity
`ES-P10`; Internal; primary `COMP-STAFF`; priority 80; conditional parallel safety only after staff-tool dispatch.

## 2. Status
`ACTIVE` as of 2026-08-07; selected automatically by the sequential package worker after fresh live reconciliation. Registry is authoritative.

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
`ES-P04` must be `COMPLETE`.

## 9. Component and repository boundaries
Root tester/staff/Paper/domain/tests/resources/docs only. No external source import, permanent component branch, or isolated PR.

## 10. Required branches
Temporary `package/es-p10-cheat-testers`; delete after verified merge containment.

## 11. Required PRs
One PR to `wsg138/EnthusiaStaff:main`.

## 12. Implementation checklist
Reconcile supported APIs; define threat/use cases and bounds; implement authorized workflow/lifecycle/cleanup; test every termination/failure path; document; update state/handoff; review/freeze/validate/merge/cleanup.

## 13. Acceptance criteria
Only authorized staff can create/control testers; entities are bounded and correctly scoped; ordinary players cannot infer sensitive staff state beyond intended test behavior; every timeout/cancel/disconnect/reload/shutdown removes state; no auto-sanction occurs.

## 14. Test requirements
Command/permission, lifecycle, visibility/audience, duplicate/limit, disconnect/reload/shutdown, API unavailable, evidence/audit, scheduler/thread, and Bedrock-safe control tests.

## 15. Static-analysis requirements
All configured Java/static-analysis/review-bot gates; zero valid unresolved findings.

## 16. Documentation requirements
Commands, permissions, configuration, operational limits, evidence/privacy, cleanup/recovery, Wiki, registry/handoff.

## 17. Security and privacy requirements
Strict authorization; bounded entities/evidence; no hidden staff/private data leakage; fail closed if packet/provider support is absent.

## 18. Migration impact
No migration assumed. The live migration boundary at package selection is immutable V17; essential durable state would require a new immutable V18 migration plus clean-install, upgrade, and checksum tests.

## 19. Bedrock considerations
Staff controls require text fallback; fake-entity visibility semantics must be tested without assuming Java client UI; acceptance remains `ES-V02`.

## 20. Distributed-runtime considerations
Scope entities to owner runtime/server; prevent duplicate creation across processes; recover after disconnect/restart and server switching.

## 21. External-provider considerations
Use only verified supported packet APIs; isolate version-specific adapters; explicit unavailable behavior.

## 22. Completion definition
Tester/fake-entity scope fully implemented/tested/documented; one exact-head PR merges normally; zero valid threads; branch cleanup verified.

## 23. Resume state
Selected from legitimate `main` `83302749b3247f7a05157f1625fc99da6aa43736` by `ChatGPT sequential package worker`. Active branch: `package/es-p10-cheat-testers`. No competing ES-P10 branch, PR, or prior package handoff existed at selection. Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-07-es-p10-cheat-testers.md`.

## 24. Last completed checkpoint
Package claim and API preflight. `ES-P02` and `ES-P05` were freshly confirmed `PARKED_BLOCKED` by a private staging run on 2026-08-07 whose required Ubuntu job received runner ID `0`, no runner name, and no steps; Pi was skipped. `ES-P04` is complete, so ES-P10 was the lowest-priority dependency-complete `READY` package. ProtocolLib 5.4.0 is already the supported compile-only Paper packet dependency and exposes supported packet/listener wrappers required for a version-isolated fake-entity adapter; no NMS dependency will be introduced.

## 25. Remaining checklist
Implement the bounded tester domain/runtime, safe player-state mutation and restoration, fake-entity packet adapter and interaction capture, staff command/tool controls including text fallback, configuration, audit/evidence, lifecycle cleanup, tests, documentation, harsh review, exact-head validation, normal merge, containment verification, branch cleanup, and terminal state publication.

## 26. Known blockers
No package-start blocker. Private representative Java/Bedrock/distributed acceptance remains assigned to `ES-V02` and is not a prerequisite for beginning this ordinary development package. The account-level private Actions billing/spending-limit condition remains unchanged for parked ES-P02/ES-P05 and must not be treated as ES-P10 staging success.

## 27. Final evidence
Starting main: `83302749b3247f7a05157f1625fc99da6aa43736`. Starting migration boundary: V17. Protocol API decision: ProtocolLib 5.4.0 through a focused adapter; no unisolated NMS. Final exact heads, lifecycle matrix, tests/checks, review, coverage, and documentation evidence remain pending.

## 28. Merge and synchronization record
Pending: PR/merge/resulting main/containment/temporary branch deletion. External parity is not applicable.
