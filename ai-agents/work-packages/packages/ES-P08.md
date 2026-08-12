# `ES-P08` — Item confiscation and restoration

## 1. Package identity
`ES-P08`; Internal; primary `COMP-STAFF`; priority 70; sequential around shared inventory journals/destructive recovery.

## 2. Status
`ACTIVE` / `ACTIONABLE_CONTINUATION`; implementation is `VALIDATION_READY` on `package/es-p08-item-confiscation`. Exact package start: reconciled `main` `7c032c6af32f7281f518a01ed6dc3b0252cabb5b`. Implementation PR: #128.

## 3. Objective
Complete transactional case-linked item confiscation, snapshots, reservations, rollback, restoration, and owner recovery.

## 4. Why the package exists
The audit classified item destruction/restoration as critical: foundations existed but owner recovery from ambiguous quarantined item operations and direct item-specific failure evidence were incomplete.

## 5. Included audit IDs
`AUD-ASSET-001` and item-specific portions of `AUD-ASSET-005`.

## 6. Included behavior
Exact item/path selection including nested containers; durable before/after snapshots and operation IDs; reservations and duplicate prevention; checked/unchecked failure rollback; restart recovery/quarantine; authorized exact restoration; explicit owner-authorized retry; bounded work and audit.

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
- [x] Define and document the item-operation/recovery state machine against current code.
- [x] Implement explicit Founder-only, audited, idempotent recovery for quarantined item confiscation/restoration operations without guessing live state.
- [x] Complete exact reservation/removal/rollback/restore/recovery correctness fixes found by review.
- [x] Add failure-injection, duplicate/restart/recovery, authorization, nested/large-codec, stale-state, transaction-rollback, ambiguity, and target-binding tests required by this package.
- [x] Update operator/Wiki/package documentation and canonical active state/handoff.
- [x] Harsh full-diff review; resolve all valid manual/Codacy/CI findings. CodeRabbit's attempted substantive review is quota-limited and is not counted as a pass; no review threads were created.
- [ ] Freeze one exact head and complete hosted/static/runtime-JAR/Wiki/Sentinel/Pi validation required by policy.
- [ ] Merge normally, prove containment/divergence, clean the temporary branch, publish terminal state, and stop.

## 13. Acceptance criteria
No item duplication/loss across success, rejection, timeout, crash, retry, or restore; nested identity/path is exact; audit links case/actor/operation; owner recovery is explicit and idempotent; work is bounded.

## 14. Test requirements
Nested/large item codecs, online/offline paths, concurrent edits, partial mutation, transaction rollback, process interruption/restart, duplicate requests, stale restoration, quarantine/owner recovery, multiple-candidate ambiguity, target corruption, and authorization tests.

## 15. Static-analysis requirements
All configured Java/static-analysis/review gates; zero valid unresolved findings. Four Codacy findings found on an earlier implementation head were fixed: two repeated test state literals and two conditional magic-number literals. No superseded check is final evidence.

## 16. Documentation requirements
Confiscation/restoration workflow, permissions, recovery/quarantine, limits, failure handling, Wiki, package state/handoff.

## 17. Security and privacy requirements
Financial-grade authorization/audit; no player inventory data in recovery evidence; fail closed on uncertain state; immutable operation linkage. Recovery authorization requires both Bukkit `enthusiastaff.owner.recovery` and service-level Founder `RESTORE_ASSETS` authority.

## 18. Migration impact
V18 remains the current immutable Flyway boundary. ES-P08 adds no migration because the existing `recovery_quarantine` schema already provides resolver/time/resolution metadata. V1–V18 remain byte-immutable.

## 19. Bedrock considerations
Recovery is a text command and does not depend on a Java-only UI. Broader Java/Bedrock acceptance remains deferred to `ES-V02`/`ES-V03`.

## 20. Distributed-runtime considerations
Ownership, leases, duplicate requests, backend switching, process death, database latency, case-target binding, and fencing divergence fail closed. Owner authorization never applies an inventory image directly; normal claim/checksum/revision recovery must acquire a newer fence and prove live state.

## 21. External-provider considerations
Shared item operation contracts remain isolated; no currency/market/reputation provider source is implemented in this package.

## 22. Completion definition
Item-specific criteria, tests, review and exact-head validation pass; implementation PR merges normally; branch cleanup verified; destructive production-like staging remains `ES-V03` and does not authorize production use.

## 23. Resume state
PR #128 on `package/es-p08-item-confiscation` is the only actionable continuation. Implementation is complete and validation-ready. Existing confiscation/restoration foundations were retained; the package adds a dedicated recovery store/coordinator/command rather than expanding the already-large mutation coordinator.

## 24. Last completed checkpoint
Implementation/review checkpoint: Founder-only `/case recoveritems <case-id>` can only requeue one coherent unresolved `CONFISCATION`/`RESTORE_CONFISCATED` pair from `QUARANTINED` to `PENDING`. Persistence independently rechecks case-target/profile binding, patch/operation profile/state/fence coherence, unresolved quarantine identity/resource key, and absence of a competing live lease. The same transaction resolves quarantine metadata and requires exactly one append-only recovery audit write. Normal fenced recovery remains the only path that can apply/finalize inventory. A failed retry reopens quarantine resolution metadata while prior authorization remains in audit. Multiple unresolved same-case item operations are rejected without guessing.

## 25. Test/review checkpoint
New unit/integration coverage proves non-Founder denial, missing-storage fail-closed behavior, exact actor/case/time delegation, generic-operation exclusion, no profile-revision mutation during authorization, duplicate replay, competing-lease rejection, paired-state rollback, case-target corruption rollback, same-case multi-scope ambiguity, re-quarantine/re-recovery, and audit count/idempotency across newer fencing tokens. Existing package-adjacent suites continue to cover exact restoration binding, duplicate finalization, failed/quarantined reservation cancellation, restored-once semantics, nested item paths, aggregate inventory-image size limits, generic paired journal fencing, and restart-style recovery.

Harsh manual review found two substantive implementation issues and fixed them before freeze: case-target corruption was changed from being filtered out to explicit fail-closed divergence, and privileged recovery audit writes were strengthened from optional `INSERT IGNORE` behavior to an exactly-one transactional insert. CodeRabbit did not complete a substantive review because the repository review quota was exhausted; its superficial success status is not treated as review evidence. No CodeRabbit review threads exist.

## 26. Remaining checklist
Publish the validation-ready canonical state, capture that resulting literal feature SHA as the frozen head, run every required hosted/static/Wiki/runtime-JAR/Sentinel/Pi gate on that exact SHA, merge PR #128 normally, prove containment/divergence, delete the package branch, publish terminal canonical state, and stop. Do not activate ES-X02 in this worker.

## 27. Known blockers
No implementation blocker is known. Representative destructive/load/private-data acceptance intentionally remains `ES-V03`; issue #43 and production cutover remain outside this package.

## 28. Final evidence
Unset until the validation-ready state publication creates one immutable frozen head and that literal SHA completes all required final gates. Earlier passing, cancelled, superseded, or diagnostic runs are not final ES-P08 evidence.

## 29. Merge and synchronization record
Start: `main` `7c032c6af32f7281f518a01ed6dc3b0252cabb5b`; feature branch `package/es-p08-item-confiscation`; implementation PR #128. `main` remained at the exact start SHA through the final implementation review, so no upstream merge/rebase was necessary. Final frozen feature head, merge commit, resulting main, containment, divergence, and branch deletion remain unset until verified.
