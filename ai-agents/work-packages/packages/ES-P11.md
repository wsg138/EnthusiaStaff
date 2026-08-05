# `ES-P11` — Fake-base generation and cleanup

## 1. Package identity
`ES-P11`; Internal; primary `COMP-STAFF`; priority 90; sequential after tester system.

## 2. Status
Initial `PLANNED`; registry is authoritative.

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
`ES-P10` must be `COMPLETE`.

## 9. Component and repository boundaries
Root fake-base/world-safety/tester/tests/resources/docs only. No external component, permanent branch, or isolated PR.

## 10. Required branches
Temporary `package/es-p11-fake-bases`; delete after verified merge containment.

## 11. Required PRs
One PR to `wsg138/EnthusiaStaff:main`.

## 12. Implementation checklist
Reconcile world APIs; define templates/bounds/safety/recovery; implement generation and cleanup state machine; failure-inject tests; document and update state/handoff; review/freeze/validate/merge/cleanup.

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
No migration assumed; durable operation state, if required, uses a new immutable post-V16 migration with upgrade/checksum tests.

## 19. Bedrock considerations
Staff control must have command/text fallback; player-facing fake content must not depend on Java-only packets unless safely isolated.

## 20. Distributed-runtime considerations
Server/world ownership, duplicate generation, chunk lifecycle, restart, and cross-backend control must be fenced.

## 21. External-provider considerations
Use only verified world/protection APIs; missing integrations must fail safe and never bypass placement protections.

## 22. Completion definition
All generation/cleanup safety criteria proven; one exact-head PR merged normally; no valid threads; temporary branch cleanup verified.

## 23. Resume state
Unassigned; no branch/PR/handoff. Start only after `ES-P10` and assignment.

## 24. Last completed checkpoint
Definition only; no product work began.

## 25. Remaining checklist
All design, implementation, tests, review, validation, merge, and evidence remain.

## 26. Known blockers
Dependency `ES-P10`; any required protection-provider contract must be verified before use.

## 27. Final evidence
Unset: world-safety design, exact heads, lifecycle/failure matrix, checks, review, docs.

## 28. Merge and synchronization record
Unset: merge/resulting main/containment/temporary branch deletion; parity not applicable.
