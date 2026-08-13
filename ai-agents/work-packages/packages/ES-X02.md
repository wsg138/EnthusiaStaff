# `ES-X02` — EnthusiaCurrency destructive provider

## 1. Package identity
`ES-X02`; External/multi-repository; primary `COMP-STAFF`; other `COMP-CURRENCY`; priority 110.

## 2. Status
`COMPLETE`.

## 3. Objective
Implement transactional currency removal and exact restoration across EnthusiaStaff and EnthusiaCurrency.

## 4. Why the package exists
The audit required a supported first-party destructive-currency contract with exact failure/recovery semantics instead of reflective/read-only integration.

## 5. Included audit IDs
`AUD-ASSET-002` and currency portion of `AUD-ASSET-005`.

## 6. Included behavior
Versioned provider API; operation-owned expiring leases; exact bank/inventory/Ender Chest snapshots and checksums; source-ordered removal; persistent bank revisions; stale/CAS protection; idempotent apply/restore; verified compensation or quarantine; provider absence/version mismatch fail-closed behavior; exact aggregate mirror/parity.

## 7. Explicit exclusions
Production balances; representative live destructive acceptance (`ES-V03`); market/reputation work; production deployment/cutover.

## 8. Dependencies
`ES-P08` was complete before selection.

## 9. Component and repository boundaries
`wsg138/EnthusiaStaff` plus `components/enthusia-currency/`, and standalone `wsg138/EnthusiaCurrency` only.

## 10. Required branches
Temporary `package/es-x02-currency-provider` branches were used in both repositories. Completion publication uses `status/es-x02-complete-20260813`; cleanup is verified after publication merge.

## 11. Required PRs
Standalone Currency PRs #11, #12, and #13 merged normally; aggregate Staff PR #133 merged normally. No squash/rebase/force-push/auto-merge was used.

## 12. Implementation checklist
- [x] Reconciled live repositories, AGENTS, package contract, registry, and validation policy.
- [x] Implemented the supported versioned Currency moderation API and exact transaction state machine.
- [x] Added persistent revisions, stale protection, exact restoration, durability verification, movement locking, recovery/quarantine behavior, and tests.
- [x] Resolved all valid standalone and aggregate review/static findings, including the Vault-missing lifecycle defect.
- [x] Passed exact-head standalone Java 21 build/tests/JAR/static/review gates.
- [x] Merged standalone Currency work normally to final `main` `b922c5af30860a6c205f9ee16b817349a7677cd0`.
- [x] Imported the exact merged standalone tree into Staff and proved object identity.
- [x] Passed exact-head Staff full build/tests/coverage, runtime-JAR/provider-leak, static, review, Sentinel artifact, and canonical private Pi staging gates.
- [x] Merged Staff PR #133 normally as `a3b6f2f7c1e9f6b7fe1667974aa0d050533605a9`.
- [x] Ran `tools/component-sync/component_sync.py compare` post-merge and proved exact parity.
- [x] Updated component metadata to `IN_SYNC` and published durable completion evidence.

## 13. Acceptance criteria
Satisfied. No valid unresolved provider, transaction, rollback, lifecycle, static, review, staging, or parity blocker remains for ES-X02.

## 14. Test requirements
Satisfied through standalone Maven verification and aggregate Staff multi-module/coverage plus canonical Pi boot/restart/database validation. Representative destructive production-like acceptance remains intentionally deferred to `ES-V03`.

## 15. Static-analysis requirements
Satisfied. Final standalone Codacy suite `85973637978` succeeded. Final aggregate Codacy check `94423669170` reported zero issues/annotations.

## 16. Documentation requirements
Satisfied: contract/provider behavior, startup boundary, operation semantics, component metadata, PR evidence, parity evidence, and canonical handoff are recorded.

## 17. Security and privacy requirements
Satisfied. No production balances, private databases, credentials, raw private evidence, or production authority changes were committed. Financial state fails closed on ambiguity/corruption.

## 18. Migration impact
Currency owns its SQLite revision-column upgrade; published migration history was not rewritten. Staff added no new migration in ES-X02.

## 19. Bedrock considerations
Staff-facing controls retain text fallback/platform-neutral identity. Representative client/destructive acceptance remains later.

## 20. Distributed-runtime considerations
Operation ownership, concurrency, duplicate calls, reconnect/process failure, asynchronous persistence, and restart recovery are handled through leases, checksums, persistent revisions, idempotency, durable journals, and quarantine outcomes.

## 21. External-provider considerations
Uses the verified first-party `wsg138/EnthusiaCurrency` contract only; no reflection/invented provider API remains in the destructive path.

## 22. Completion definition
Met: standalone and aggregate PRs merged normally, all required gates passed, post-merge parity true, metadata/evidence recorded, and cleanup verified after completion publication.

## 23. Resume state
Terminal. Do not reopen ES-X02 unless live GitHub reveals a new regression or an explicit new package/change request. New workers should reconcile the registry and choose the next dependency-safe package.

## 24. Last completed checkpoint
Post-merge parity passed between Staff aggregate merge `a3b6f2f7c1e9f6b7fe1667974aa0d050533605a9` and Currency main `b922c5af30860a6c205f9ee16b817349a7677cd0` with identical content hash `d6797acbd50bb6547ce724bff946974872795e9f2343c664c2c9e8bde28e5e2c` and zero added/missing/modified files.

## 25. Remaining checklist
None inside ES-X02. `ES-V03` separately owns representative destructive/latency/load acceptance.

## 26. Known blockers
None for ES-X02.

## 27. Final evidence
- Currency normal merges: PR #11 -> `6fd8947d3b2d2c470548f77f4fbf253fcc86b7e2`; PR #12 -> `7a9f67ed57de3d4eb7529c91a625efd017bfa88e`; PR #13/final main -> `b922c5af30860a6c205f9ee16b817349a7677cd0`.
- Final standalone validated head: `a968f04b09c11dc1816f2b802626adbcef0f73c8`; Java 21 run `31692395919` / job `94422400756`, 7 tests + shaded JAR; Codacy suite `85973637978`; zero review threads.
- Staff frozen product head: `fbba02d10301b6bc6d80ada4ad7113f80ff95514`.
- Aggregate Coverage run `31692612391` / job `94423135991`; Staff Codacy `94423669170`; Sentinel artifact run `31692612386` / job `94423077006`.
- Canonical Pi: public run `31692610056`; private run `31693194558` / job `94424932390` on trusted `Lincoln-PI-4` (`runner_id: 2`), result `PASS`, two starts/storage-ready cycles, clean shutdown/failure scans, disposable DB reset, sanitized evidence artifact `9178996362` digest `sha256:3bdf2a97d47678ffd9a2f5875268f451bc08a237b2b30b434add1c918dab4b72`; public bridge cleanup/result success.
- Staff PR #133 normal merge: `a3b6f2f7c1e9f6b7fe1667974aa0d050533605a9`.
- Parity evidence: `ai-agents/reports/package-handoffs/2026-08-13-es-x02-component-parity.json`; both hashes `d6797acbd50bb6547ce724bff946974872795e9f2343c664c2c9e8bde28e5e2c`; `parity: true`.
- Terminal handoff: `ai-agents/reports/package-handoffs/2026-08-13-es-x02-currency-provider-complete.md`.

## 28. Merge and synchronization record
Standalone final main `b922c5af30860a6c205f9ee16b817349a7677cd0`; aggregate product merge `a3b6f2f7c1e9f6b7fe1667974aa0d050533605a9`; post-merge parity true; component metadata `IN_SYNC`. Completion-publication merge and branch-cleanup facts are verified from live GitHub after the state PR merges.
