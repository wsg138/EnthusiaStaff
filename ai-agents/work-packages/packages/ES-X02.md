# `ES-X02` — EnthusiaCurrency destructive provider

## 1. Package identity
`ES-X02`; External/multi-repository; primary `COMP-STAFF`; other `COMP-CURRENCY`; priority 110; sequential around shared destructive journals.

## 2. Status
`BLOCKED` / `PARKED_BLOCKED`; implementation and every non-Pi gate are complete, but canonical private Pi staging has not received a trusted runner.

## 3. Objective
Implement transactional currency removal and exact restoration across EnthusiaStaff and EnthusiaCurrency.

## 4. Why the package exists
The audit marks currency destruction critical and provider-blocked: runtime paths require a compatible first-party contract plus private acceptance later.

## 5. Included audit IDs
`AUD-ASSET-002` and currency portion of `AUD-ASSET-005`.

## 6. Included behavior
Versioned supported API; reservation/operation IDs; exact bank/inventory/Ender Chest snapshots; source-ordered removal; persistent bank revisions; stale rejection; idempotent apply/restore; verified rollback or quarantine; provider present/missing/version mismatch; case/audit linkage; matching aggregate copy/parity.

## 7. Explicit exclusions
Production balances; representative live destructive testing (`ES-V03`); market/reputation; economy redesign outside required contract.

## 8. Dependencies
`ES-P08` is `COMPLETE`.

## 9. Component and repository boundaries
EnthusiaStaff integration/root plus `components/enthusia-currency/`, and `wsg138/EnthusiaCurrency`; no unrelated components, permanent branches, or isolated product PR.

## 10. Required branches
`package/es-x02-currency-provider` is preserved for open Staff PR #133. Standalone Currency work is already merged to `main`; branch cleanup waits for verified package completion. The blocker publication uses docs-only `status/es-x02-pi-runner-blocked-20260813`.

## 11. Required PRs
Standalone Currency PRs #11, #12, and #13 merged normally. Aggregate Staff product PR #133 is open, non-draft, mergeable, frozen at `fbba02d10301b6bc6d80ada4ad7113f80ff95514`, and intentionally unmerged while canonical Pi is unavailable.

## 12. Implementation checklist
- [x] Reconcile both repositories, AGENTS, live heads, and package registry.
- [x] Define and implement versioned moderation provider in standalone Currency.
- [x] Implement expiring operation-owned locks, exact snapshots/checksums, source-ordered removal, persistent revisions, stale/CAS protection, idempotent apply/restore, verified compensation/quarantine, and provider registration.
- [x] Fix all valid standalone and aggregate static/review findings, including async durability exactness, lease timing/overflow, movement-lock gaps, failed-CAS phantom state, aggregate PMD debt, and Vault-missing startup continuation.
- [x] Merge standalone Currency work normally through PRs #11/#12/#13; final `main` `b922c5af30860a6c205f9ee16b817349a7677cd0`.
- [x] Import exact merged standalone state into Staff and prove pre-merge Git-object identity.
- [x] Open aggregate Staff PR #133 and freeze exact product head `fbba02d10301b6bc6d80ada4ad7113f80ff95514`.
- [x] Pass exact aggregate hosted build/tests/coverage, runtime-JAR/provider-leak, static analysis, review, and Sentinel artifact gates.
- [ ] Execute canonical private Pi runtime/persistence/restart/cleanup validation. Current private job has no runner allocation.
- [ ] Merge Staff PR #133 normally.
- [ ] Prove post-merge standalone/aggregate parity with `component_sync.py`, update metadata to `IN_SYNC`, verify containment, and clean temporary branches.

## 13. Acceptance criteria
No balance loss/duplication under success, rejection, timeout, duplicate, crash, or restoration; snapshots/audit are exact; missing/incompatible provider fails safe; both repositories merge normally and aggregate copy matches standalone. Canonical Pi must actually execute successfully before aggregate merge.

## 14. Test requirements
Both repos' suites plus reservation/idempotency, concurrent changes, partial failure/rollback, restart/retry, stale restoration, provider absent/version mismatch, authorization/audit, and bounded work tests. Representative live destructive balance acceptance remains deferred to `ES-V03`.

## 15. Static-analysis requirements
Satisfied on current product heads: final standalone Codacy suite `85973637978` succeeded; final Staff Codacy check `94423669170` reports zero issues. No valid unresolved CodeRabbit/human review thread remains.

## 16. Documentation requirements
Contract/version, provider setup/missing behavior, operation states, recovery/restoration, component metadata, package handoff, and PR cross-links are present; final completion/parity metadata remains pending.

## 17. Security and privacy requirements
Financial-grade authorization/audit; no real balances/player rows in artifacts; fail closed; bounded/redacted logging. No private databases, production rows, secrets, or reconstructable private evidence were committed.

## 18. Migration impact
No new Staff migration. Existing Currency SQLite balance schema is upgraded by the owning repository with a persistent revision column; history is not rewritten.

## 19. Bedrock considerations
Staff controls retain text fallback and identity correctness; representative client/destructive acceptance remains later.

## 20. Distributed-runtime considerations
Operation ownership, concurrent balance changes, duplicate calls, reconnect, process death, and DB latency are handled through lease ownership, checksums, persistent revisions, idempotency, and quarantine outcomes; private representative acceptance remains `ES-V03`.

## 21. External-provider considerations
Uses the verified first-party `wsg138/EnthusiaCurrency` API; no reflection or invented runtime contract. Final standalone main is `b922c5af30860a6c205f9ee16b817349a7677cd0`.

## 22. Completion definition
Standalone and aggregate product PRs merge normally; all required checks/reviews/Pi pass; post-merge parity is true; metadata/merges/hashes recorded; temporary branches cleaned. Private representative destructive acceptance remains `ES-V03`.

## 23. Resume state
Resume Staff PR #133 at exact frozen product head `fbba02d10301b6bc6d80ada4ad7113f80ff95514`. First reconcile public Pi run `31692610056` and private run `31693194558` / job `94424932390`. If the private job has actually allocated and completed since this publication, inspect exact logs/evidence and require every applicable runtime, persistence/restart, cleanup, and public-transfer assertion. If it remains unallocated, do not rerun the same path until the trusted runner condition changes.

## 24. Last completed checkpoint
Every non-Pi gate is green. Coverage run `31692612391` passed the full aggregate Java 21 suite and runtime-JAR/provider-leak inspection; Staff Codacy `94423669170` is zero-issue; review debt is zero; Sentinel artifact run `31692612386` passed. The canonical public Pi build/bridge dispatched the exact private run, but no private runner has allocated.

## 25. Remaining checklist
Actual private Pi execution and public bridge cleanup/final result; normal Staff merge; post-merge `component_sync.py` parity; metadata `IN_SYNC`; containment and branch cleanup; canonical `COMPLETE` publication.

## 26. Known blockers
Private staging job `94424932390` in run `31693194558` is queued with `runner_id: 0`, empty runner name, and zero steps for required labels `self-hosted/Linux/ARM64/enthusia-staging`. No owner-approved infrastructure exception exists for ES-X02. Do not call this a Pi pass or product failure.

## 27. Current evidence
- Staff package start: `4831b1442e572914c86fd8e202e7de6f546868e2`.
- Currency package start: `922223cfff8c325e36f58b6af6adf6d74e4a5417`.
- Currency normal merges: #11 `6fd8947d3b2d2c470548f77f4fbf253fcc86b7e2`; #12 `7a9f67ed57de3d4eb7529c91a625efd017bfa88e`; #13 / final main `b922c5af30860a6c205f9ee16b817349a7677cd0`.
- Final standalone validation head: `a968f04b09c11dc1816f2b802626adbcef0f73c8`; exact branch-head CI run `31692395919` / job `94422400756`; 7 tests + shaded JAR; Codacy suite `85973637978`; CodeRabbit success; zero review threads.
- Staff PR #133 frozen head: `fbba02d10301b6bc6d80ada4ad7113f80ff95514`; mergeable, non-draft.
- Staff Coverage: run `31692612391` / job `94423135991`; 48.98% lines / 40.05% branches / 51.52% instructions; Paper SHA-256 `a142d0c30cbe4d085dea0901287f1d1bf9d84cb2143a0322091afb908342c6a6`; Velocity SHA-256 `c891d4744ed142edffa0352b4c20f39428fbc379c46313dbbe234878345ec1c7`; validation artifact `9178197820`.
- Staff static/review: Codacy `94423669170` zero issues; zero valid unresolved review threads; final CodeRabbit status success/rate-limited with no new finding.
- Sentinel artifact: run `31692612386` / job `94423077006`, artifact `9178016407`, success.
- Canonical Pi public run `31692610056`: exact public build success and private dispatch complete. Private run `31693194558` / job `94424932390`: queued, `runner_id: 0`, empty runner, zero steps; no Pi pass claimed.
- Handoff: `ai-agents/reports/package-handoffs/2026-08-13-es-x02-currency-provider-pi-blocked.md`.

## 28. Merge and synchronization record
Standalone is merged. Aggregate remains intentionally unmerged at `fbba02d...` pending actual canonical Pi. Pre-merge object identity is proven; post-merge parity and final metadata are pending and must not be predeclared.
