# `ES-P08` — Item confiscation and restoration

## 1. Package identity
`ES-P08`; Internal; primary `COMP-STAFF`; priority 70; sequential around shared inventory journals/destructive recovery.

## 2. Status
Initial `PLANNED`; registry is authoritative.

## 3. Objective
Complete transactional case-linked item confiscation, snapshots, reservations, rollback, restoration, and owner recovery.

## 4. Why the package exists
The audit classified item destruction/restoration as critical: foundations exist but coordinator/codecs and live failure recovery are weakly proved.

## 5. Included audit IDs
`AUD-ASSET-001` and item-specific portions of `AUD-ASSET-005`.

## 6. Included behavior
Exact item/path selection including nested containers; durable before/after snapshots and operation IDs; reservations and duplicate prevention; checked/unchecked failure rollback; restart recovery/quarantine; authorized exact restoration; bounded work and audit.

## 7. Explicit exclusions
Currency (`ES-X02`), market (`ES-X03`), reputation (`ES-X04`), production inventories, whole-server rollback.

## 8. Dependencies
`ES-P07` must be `COMPLETE`.

## 9. Component and repository boundaries
Root inventory/asset/domain/persistence/Paper/tests/docs only. No external component source or permanent/isolated branch.

## 10. Required branches
Temporary `package/es-p08-item-confiscation`; delete after verified merge containment.

## 11. Required PRs
One PR to `wsg138/EnthusiaStaff:main`.

## 12. Implementation checklist
Reconcile journal/coordinator/codecs; define operation state machine; implement exact reservation/removal/rollback/restore/recovery; failure-inject tests; update docs/state/handoff; review/freeze/validate/merge/cleanup.

## 13. Acceptance criteria
No item duplication/loss across success, rejection, timeout, crash, retry, or restore; nested identity/path is exact; audit links case/actor/operation; owner recovery is explicit and idempotent; work is bounded.

## 14. Test requirements
Nested/large item codecs, online/offline paths, concurrent edits, partial mutation, transaction rollback, process interruption/restart, duplicate requests, stale restoration, quarantine/owner recovery, and authorization tests.

## 15. Static-analysis requirements
All configured Java/static-analysis/review-bot gates; zero valid unresolved findings.

## 16. Documentation requirements
Confiscation/restoration workflow, permissions, recovery/quarantine, limits, failure handling, Wiki, package state/handoff.

## 17. Security and privacy requirements
Financial-grade authorization/audit; no player inventory data in evidence; fail closed on uncertain state; immutable operation linkage.

## 18. Migration impact
A new migration may be required for state/audit fields; verify V16, add immutable post-V16 migration, test clean install/upgrade/checksum; never edit history.

## 19. Bedrock considerations
Staff command/text fallback must be usable; item semantics must not depend on Java-only UI; acceptance deferred to `ES-V02/V03`.

## 20. Distributed-runtime considerations
Ownership, leases, duplicate requests, backend switching, process death, and database latency must not duplicate or lose assets.

## 21. External-provider considerations
Design shared operation contracts without implementing currency/market/reputation providers in this package.

## 22. Completion definition
Item-specific criteria, tests, review and exact-head validation pass; one PR merges normally; branch cleanup verified; destructive staging remains `ES-V03`.

## 23. Resume state
Unassigned; no branch/PR/handoff. Start only after `ES-P07` and assignment.

## 24. Last completed checkpoint
Definition only; no product implementation began.

## 25. Remaining checklist
All implementation, failure injection, review, validation, merge, and evidence remain.

## 26. Known blockers
Dependency `ES-P07`; private destructive acceptance intentionally deferred to `ES-V03`.

## 27. Final evidence
Unset: state-machine matrix, exact heads, migration/tests/checks, review and recovery evidence.

## 28. Merge and synchronization record
Unset: feature head, merge, resulting main, containment, temporary branch deletion; parity not applicable.
