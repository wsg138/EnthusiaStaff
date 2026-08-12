# `ES-P08` — Item confiscation and restoration

## 1. Package identity
`ES-P08`; Internal; primary `COMP-STAFF`; priority 70; sequential around shared inventory journals/destructive recovery.

## 2. Status
`ACTIVE` / `ACTIONABLE_CONTINUATION`; claimed 2026-08-11 from reconciled `main` `7c032c6af32f7281f518a01ed6dc3b0252cabb5b` on `package/es-p08-item-confiscation`.

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
`ES-P07` is `COMPLETE`.

## 9. Component and repository boundaries
Root inventory/asset/domain/persistence/Paper/tests/docs only. No external component source or permanent/isolated branch.

## 10. Required branches
Temporary `package/es-p08-item-confiscation`; delete after verified merge containment.

## 11. Required PRs
One implementation PR to `wsg138/EnthusiaStaff:main`; a post-merge docs-only finalization PR is permitted only if needed to publish facts that cannot truthfully exist before the implementation merge.

## 12. Implementation checklist
- [x] Reconcile live journal/coordinator/codecs, package dependencies, current migration boundary, GitHub branches/PRs, and production boundary.
- [ ] Define and document the item-operation/recovery state machine against current code.
- [ ] Implement explicit Founder-only, audited, idempotent recovery for quarantined item confiscation/restoration operations without guessing live state.
- [ ] Complete exact reservation/removal/rollback/restore/recovery correctness fixes found by review.
- [ ] Add failure-injection, duplicate/restart/recovery, authorization, nested/large-codec, stale-state, transaction-rollback, and target-binding tests required by this package.
- [ ] Update operator/Wiki/package documentation and canonical state/handoff.
- [ ] Harsh full-diff review; resolve all valid human/CodeRabbit/Codacy/CI findings.
- [ ] Freeze one exact head and complete hosted/static/runtime-JAR/Wiki/Sentinel/Pi validation required by policy.
- [ ] Merge normally, prove containment/divergence, clean the temporary branch, publish terminal state, and stop.

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
Live reconciliation confirms V18 is the current immutable Flyway boundary. ES-P08 must not edit V1–V18. Add V19 only if a schema change becomes necessary, then test clean install/upgrade/checksum integrity; never rewrite history.

## 19. Bedrock considerations
Staff command/text fallback must be usable; item semantics must not depend on Java-only UI; broader acceptance remains deferred to `ES-V02`/`ES-V03`.

## 20. Distributed-runtime considerations
Ownership, leases, duplicate requests, backend switching, process death, and database latency must not duplicate or lose assets.

## 21. External-provider considerations
Design shared operation contracts without implementing currency/market/reputation providers in this package.

## 22. Completion definition
Item-specific criteria, tests, review and exact-head validation pass; implementation PR merges normally; branch cleanup verified; destructive production-like staging remains `ES-V03` and does not authorize production use.

## 23. Resume state
Assigned to the sequential package worker on `package/es-p08-item-confiscation`; exact package start `7c032c6af32f7281f518a01ed6dc3b0252cabb5b`. No pre-existing ES-P08 branch or PR was found. Current source already has durable confiscation/restoration foundations; the confirmed documented gap is the absence of a privileged quarantine-resolution workflow, while wrong-player restoration is fail-closed during durable preparation.

## 24. Last completed checkpoint
2026-08-11 live reconciliation and package claim: no incomplete-package PR/branch continuation exists; ES-X01 remains parked because a supported RoseChat standalone repository/source contract is unresolved; V18 is current; issue #43 remains open/deferred; LiteBans remains authoritative.

## 25. Remaining checklist
Implementation/recovery completion, failure injection and package tests, documentation, review, exact-head validation, normal merge, containment, branch cleanup, and terminal canonical publication remain.

## 26. Known blockers
No implementation blocker is known. Representative broader destructive/load/private-data acceptance intentionally remains `ES-V03`; issue #43 and production cutover are outside this package.

## 27. Final evidence
Unset until one immutable head completes required build/test/static/review/Sentinel/Pi evidence. Earlier repository/package checks are diagnostic only and will not be reused as ES-P08 final proof.

## 28. Merge and synchronization record
Start: `main` `7c032c6af32f7281f518a01ed6dc3b0252cabb5b`; feature branch `package/es-p08-item-confiscation`. Final feature head, merge commit, resulting main, containment, divergence, and branch deletion remain unset until verified.
