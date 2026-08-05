# `ES-P05` — Report evidence and staff workflow completion

## 1. Package identity
`ES-P05`; Internal; primary `COMP-STAFF`; priority 50; conditionally parallel only with proven non-overlap.

## 2. Status
Initial `PLANNED`; registry is authoritative.

## 3. Objective
Complete provider-independent report submission, evidence, queue/detail, notes/status, stale-state, privacy, and staff workflow behavior.

## 4. Why the package exists
Report command/GUI runtime paths and evidence handling need one bounded package before Discord delivery and RoseChat PM integration.

## 5. Included audit IDs
`AUD-REPORT-001`, `AUD-REPORT-002`, provider-independent portions of `AUD-REPORT-003`.

## 6. Included behavior
Prove target resolution, cooldown/merge/duplicate prevention; queue/detail GUI and command fallback; notes/status revisions/stale-state; chat/coordinate/client evidence; privacy/retention boundaries; attachment behavior must be explicitly implemented or excluded by documented decision.

## 7. Explicit exclusions
RoseChat private-message evidence (`ES-X01`); Discord route delivery (`ES-P06`); production evidence/routes.

## 8. Dependencies
`ES-P03` and `ES-P04` must be `COMPLETE`.

## 9. Component and repository boundaries
Root report/domain/persistence/Paper/tests/docs only. No RoseChat source import, permanent component branch, or isolated PR.

## 10. Required branches
Temporary `package/es-p05-report-workflow`; delete after verified merge containment.

## 11. Required PRs
One PR to `wsg138/EnthusiaStaff:main`.

## 12. Implementation checklist
Reconcile; trace submission/query/state/evidence; implement missing provider-independent behavior; test commands/GUI/persistence/privacy/races/restart; update docs/state/handoff; review/freeze/validate/merge/cleanup.

## 13. Acceptance criteria
Submission and staff review are usable via GUI and text fallback; duplicate/cooldown/merge rules are durable; status/note changes are revision-safe; evidence is bounded, attributable, retained/purged correctly, and privacy-safe.

## 14. Test requirements
Command/GUI wiring, offline target, cooldown/replay, stale revision, concurrent staff, restart, evidence bounds/purge, failure rollback, and Bedrock fallback tests plus full applicable suites.

## 15. Static-analysis requirements
All configured Java/static-analysis/review-bot gates with zero valid unresolved findings.

## 16. Documentation requirements
Report commands, permissions, workflow, evidence/privacy/retention, fallbacks, troubleshooting, Wiki, registry, package, handoff.

## 17. Security and privacy requirements
Least-privilege evidence access; no PM/provider invention; redact coordinates/client evidence where required; no private rows in artifacts.

## 18. Migration impact
No migration assumed; essential schema additions require a new post-V16 immutable migration and clean/upgrade/checksum tests.

## 19. Bedrock considerations
Complete text fallback and readable messages; live client acceptance remains `ES-V02`.

## 20. Distributed-runtime considerations
Durable duplicate prevention, cross-backend query/state ownership, concurrent reviewers, reconnect, and restart.

## 21. External-provider considerations
RoseChat-specific PM capture is excluded and must remain explicit/unavailable until `ES-X01`.

## 22. Completion definition
Provider-independent scope proven, documented, exactly reviewed, and merged in one normal PR; no valid threads; branch cleanup verified.

## 23. Resume state
Unassigned; no branch/PR/handoff. Start only after dependencies and assignment.

## 24. Last completed checkpoint
Definition only; no product implementation began.

## 25. Remaining checklist
All implementation, tests, review, validation, merge, and evidence remain.

## 26. Known blockers
Dependencies; RoseChat PM evidence intentionally routed to `ES-X01`.

## 27. Final evidence
Unset: exact head, coverage matrix, DB/runtime tests, checks, review, and privacy evidence.

## 28. Merge and synchronization record
Unset: record merge/containment/temporary branch cleanup; parity not applicable.
