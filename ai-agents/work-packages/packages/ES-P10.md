# `ES-P10` — Cheat tester and fake-entity system

## 1. Package identity
`ES-P10`; Internal; primary `COMP-STAFF`; priority 80; conditional parallel safety only after staff-tool dispatch.

## 2. Status
Initial `PLANNED`; registry is authoritative.

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
No migration assumed; essential durable state requires a new immutable post-V16 migration and upgrade/checksum tests.

## 19. Bedrock considerations
Staff controls require text fallback; fake-entity visibility semantics must be tested without assuming Java client UI; acceptance remains `ES-V02`.

## 20. Distributed-runtime considerations
Scope entities to owner runtime/server; prevent duplicate creation across processes; recover after disconnect/restart and server switching.

## 21. External-provider considerations
Use only verified supported packet APIs; isolate version-specific adapters; explicit unavailable behavior.

## 22. Completion definition
Tester/fake-entity scope fully implemented/tested/documented; one exact-head PR merges normally; zero valid threads; branch cleanup verified.

## 23. Resume state
Unassigned; no branch/PR/handoff. Start only after `ES-P04` and assignment.

## 24. Last completed checkpoint
Definition only; no product implementation began.

## 25. Remaining checklist
All design, implementation, tests, review, validation, merge, and evidence remain.

## 26. Known blockers
Dependency `ES-P04`; packet API decision must use supported repository/runtime evidence.

## 27. Final evidence
Unset: API decision, exact heads, lifecycle matrix, tests/checks, review, docs.

## 28. Merge and synchronization record
Unset: merge/resulting main/containment/temporary branch deletion; parity not applicable.
