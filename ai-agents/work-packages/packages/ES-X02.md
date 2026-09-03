# `ES-X02` — EnthusiaCurrency destructive provider

## 1. Package identity
`ES-X02`; external/multi-repository; primary `COMP-STAFF`; other `COMP-CURRENCY`; priority 110.

## 2. Status
`COMPLETE`.

The earlier terminal publication through Staff PR #135 is historical. The later correctness follow-up passed every required gate and merged through Staff PR #137.

## 3. Objective
Implement transactional currency removal and exact restoration across EnthusiaStaff and EnthusiaCurrency.

## 4. Why the package exists
The audit required a supported first-party destructive-currency contract with exact failure/recovery semantics instead of reflective/read-only integration.

## 5. Included audit IDs
`AUD-ASSET-002` and the currency portion of `AUD-ASSET-005`.

## 6. Included behavior
Versioned provider API; operation-owned expiring leases; exact bank/inventory/Ender Chest snapshots and checksums; source-ordered removal; persistent bank revisions; stale/CAS protection; idempotent apply/restore; verified compensation or quarantine; provider absence/version mismatch fail-closed behavior; exact aggregate mirror/parity.

## 7. Explicit exclusions
Production balances; representative live destructive acceptance (`ES-V03`); market/reputation work; production deployment/cutover.

## 8. Dependencies
`ES-P08` was complete before selection.

## 9. Component and repository boundaries
`wsg138/EnthusiaStaff` plus `components/enthusia-currency/`, and standalone `wsg138/EnthusiaCurrency` only.

## 10. Required branches
The temporary `package/es-x02-currency-provider` branches were deleted after normal merge and containment verification in each repository.

## 11. Required PRs
Standalone Currency PRs #11, #12, #13, and corrective PR #14 merged normally. Staff PR #133 and state PR #135 are historical merges for the prior tree. Corrective Staff PR #137 merged normally as the current aggregate source of truth.

## 12. Implementation checklist
- [x] Preserve all previously completed API, persistence, lease, compensation, lifecycle, and integration work.
- [x] Validate a supplied removal plan before accepting committed replay.
- [x] Require an advanced bank revision before accepting an idempotent restore.
- [x] Add direct regression coverage for both defects and the valid idempotent cases.
- [x] Merge standalone Currency PR #14 normally as `2b4c8bf6d8e8ef1c8c6b042cd3147e66ffc660fe`.
- [x] Import the exact corrected standalone tree and prove candidate parity.
- [x] Pass local Java 21 component and full Staff validation, including MariaDB Testcontainers.
- [x] Push the reconciled Staff branch and open the follow-up PR.
- [x] Pass exact-head hosted build/coverage, Codacy, review-thread, Sentinel, and canonical Pi gates.
- [x] Merge the Staff follow-up normally and prove post-merge parity.
- [x] Republish terminal package state and clean the temporary implementation branch.

## 13. Acceptance criteria
Satisfied. The standalone and aggregate corrections merged normally, all required exact-head gates passed, feature-head containment is exact, and post-merge component parity is true.

## 14. Test requirements
Local evidence is green:

- standalone and aggregate component Java 21 Maven verification: 11 tests each;
- Staff Java 21 clean task graph: 218 suites / 936 tests;
- MariaDB Testcontainers subset: 48 suites / 189 tests;
- zero failures, errors, or skips;
- aggregate JaCoCo XML and both runtime JARs produced.

Hosted exact-head run `31697097557` passed the full Java 21 build/test/coverage workflow. Canonical Pi public run `31697114883` and correlated private run `31697709094` passed two guarded Paper/storage-ready cycles, shutdown checks, disposable-database cleanup, provider-leak inspection, and transfer cleanup.

## 15. Static-analysis requirements
Standalone PR #14 and Staff PR #137 each reported Codacy up-to-standards status with zero new issues. Focused PMD 7.26.0 and threshold-matched Lizard 1.23.0 report zero findings in both source copies. The full standalone Opengrep result is one unrelated pre-existing leaderboard finding; it remains visible and unsuppressed. No analyzer rule, issue, or first-party source path was suppressed.

## 16. Documentation requirements
Current routing, component metadata, superseded completion history, parity evidence, and the corrective handoff are published accurately.

## 17. Security and privacy requirements
No production balances, private databases, credentials, raw private evidence, or production authority changes may be committed. Financial state must fail closed on ambiguity, corruption, stale state, or unverified recovery.

## 18. Migration impact
Currency owns its SQLite revision-column upgrade; published migration history was not rewritten. Staff adds no migration in this follow-up.

## 19. Bedrock considerations
Staff-facing controls retain text fallback and platform-neutral identity. Representative client/destructive acceptance remains later.

## 20. Distributed-runtime considerations
Operation ownership, concurrency, duplicate calls, reconnect/process failure, asynchronous persistence, and restart recovery remain handled through leases, checksums, persistent revisions, idempotency, durable journals, and quarantine outcomes.

## 21. External-provider considerations
Uses the verified first-party `wsg138/EnthusiaCurrency` contract only; no reflection or invented provider API remains in the destructive path.

## 22. Completion definition
Satisfied: standalone and aggregate corrections merged normally; all required exact-head gates passed; post-merge parity is true; component metadata/evidence is updated; temporary implementation branches are verified contained and deleted.

## 23. Resume state
Terminal. Do not resume ES-X02 unless a later review or validation package exposes a new defect. Dependency recomputation makes ES-X03 and ES-X04 `READY`; this worker does not activate either package.

## 24. Last completed checkpoint
Corrective Staff head `88bd314da7224a64e6912ab2faa76f9548180584` passed hosted/static/runtime gates and merged normally through PR #137 as `2150ac1d01849bd67ee97478f64cbcba31e5dc7f`. Post-merge parity against Currency `2b4c8bf6d8e8ef1c8c6b042cd3147e66ffc660fe` is exact at hash `c5820e3121372f81c8611de9b6015f77e28f5c2160037da035f650660ed090eb`.

## 25. Remaining checklist
None for ES-X02. Representative destructive/latency/load acceptance remains explicitly assigned to ES-V03.

## 26. Known blockers
None for ES-X02.

## 27. Evidence
- Prior standalone merges: #11 -> `6fd8947d3b2d2c470548f77f4fbf253fcc86b7e2`; #12 -> `7a9f67ed57de3d4eb7529c91a625efd017bfa88e`; #13 -> `b922c5af30860a6c205f9ee16b817349a7677cd0`.
- Corrective standalone PR #14: commit `fd5ea106f4dc27160810b96a82059bc282cdf3f1`; merge `2b4c8bf6d8e8ef1c8c6b042cd3147e66ffc660fe`; hosted verify and Codacy passed; zero review threads.
- Historical Staff product merge: PR #133 -> `a3b6f2f7c1e9f6b7fe1667974aa0d050533605a9`.
- Historical completion publication: PR #135 -> `0c34478db01cfc9f6f181e47d9fe055e0df84f19`.
- Corrective Staff PR #137: frozen head `88bd314da7224a64e6912ab2faa76f9548180584`; normal merge `2150ac1d01849bd67ee97478f64cbcba31e5dc7f`; exact containment; deleted implementation branch.
- Hosted corrected-tree evidence: Coverage/full build `31697097557`; Sentinel artifact `31697114562`; Codacy zero new issues/up to standards; zero review threads. CodeRabbit was rate-limited and no approval is claimed.
- Canonical Pi evidence: public run `31697114883`; private run `31697709094`; sanitized artifact `9180223345`, digest `sha256:c6218f816349256f0160d2e7cd46bf6ff0892c1736effbe4936763c2d9e15bf3`; two server/storage-ready cycles; clean shutdowns and database/transfer cleanup; `failure_count=0`.
- Corrected post-merge parity: `ai-agents/reports/package-handoffs/2026-08-13-es-x02-corrected-component-parity.json`.
- Current handoff: `ai-agents/reports/package-handoffs/2026-08-13-es-x02-currency-provider-followup.md`.

## 28. Merge and synchronization record
Current standalone `main`: `2b4c8bf6d8e8ef1c8c6b042cd3147e66ffc660fe`. Corrected aggregate product merge: `2150ac1d01849bd67ee97478f64cbcba31e5dc7f`. Component metadata is `IN_SYNC`. Post-merge compare reports parity true with identical hash `c5820e3121372f81c8611de9b6015f77e28f5c2160037da035f650660ed090eb` and no added, missing, or modified files.
