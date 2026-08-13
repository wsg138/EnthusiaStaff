# `ES-X02` — EnthusiaCurrency destructive provider

## 1. Package identity
`ES-X02`; external/multi-repository; primary `COMP-STAFF`; other `COMP-CURRENCY`; priority 110.

## 2. Status
`IN_PROGRESS` / `ACTIONABLE_CONTINUATION`.

The earlier terminal publication through Staff PR #135 is historical. A later targeted review found two valid correctness defects, so ES-X02 was reopened before any downstream provider package began.

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
The same temporary `package/es-x02-currency-provider` lineage is reopened in Staff for the aggregate correction. The standalone branch was deleted after Currency PR #14 merged and containment was verified.

## 11. Required PRs
Standalone Currency PRs #11, #12, #13, and corrective PR #14 merged normally. Staff PR #133 and state PR #135 are historical merges for the prior tree. One follow-up Staff PR is required for the exact PR #14 tree.

## 12. Implementation checklist
- [x] Preserve all previously completed API, persistence, lease, compensation, lifecycle, and integration work.
- [x] Validate a supplied removal plan before accepting committed replay.
- [x] Require an advanced bank revision before accepting an idempotent restore.
- [x] Add direct regression coverage for both defects and the valid idempotent cases.
- [x] Merge standalone Currency PR #14 normally as `2b4c8bf6d8e8ef1c8c6b042cd3147e66ffc660fe`.
- [x] Import the exact corrected standalone tree and prove candidate parity.
- [x] Pass local Java 21 component and full Staff validation, including MariaDB Testcontainers.
- [ ] Push the reconciled Staff branch and open the follow-up PR.
- [ ] Pass exact-head hosted build/coverage, Codacy, review, Sentinel, and canonical Pi gates.
- [ ] Merge the Staff follow-up normally and prove post-merge parity.
- [ ] Republish terminal package state and clean the temporary branch.

## 13. Acceptance criteria
Not yet satisfied. The corrected product logic and local validation are complete; exact-head hosted/runtime gates, normal Staff merge, and post-merge parity remain.

## 14. Test requirements
Local evidence is green:

- standalone and aggregate component Java 21 Maven verification: 11 tests each;
- Staff Java 21 clean task graph: 218 suites / 936 tests;
- MariaDB Testcontainers subset: 48 suites / 189 tests;
- zero failures, errors, or skips;
- aggregate JaCoCo XML and both runtime JARs produced.

Hosted exact-head and canonical Pi reruns remain required because product code changed after the prior frozen head.

## 15. Static-analysis requirements
Standalone PR #14 Codacy reported zero new issues and up-to-standards status. Focused PMD 7.26.0 and threshold-matched Lizard 1.23.0 report zero findings in both source copies. The full standalone Opengrep result is one unrelated pre-existing leaderboard finding; it remains visible and unsuppressed. Staff hosted Codacy remains pending on the follow-up head.

## 16. Documentation requirements
Current routing, component metadata, superseded completion history, and this follow-up handoff must remain accurate through the final merge and parity proof.

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
Standalone and aggregate corrections merged normally; all required exact-head gates passed; post-merge parity true; component metadata/evidence updated; temporary branches verified clean and deleted.

## 23. Resume state
Continue exactly this ES-X02 follow-up. Do not select ES-X03 or ES-X04 until completion is republished from the corrected aggregate-main tree.

## 24. Last completed checkpoint
Standalone PR #14 merged as `2b4c8bf6d8e8ef1c8c6b042cd3147e66ffc660fe`. Corrected aggregate product commit `260098756bf1fd658199bb9b54c7ea2848817fd7` passed local validation before reconciliation with current Staff `main`. Candidate parity hash is `c5820e3121372f81c8611de9b6015f77e28f5c2160037da035f650660ed090eb` on both trees.

## 25. Remaining checklist
Finish branch reconciliation and publication; pass exact-head hosted/static/review/Sentinel/Pi gates; merge normally; prove post-merge parity; update `IN_SYNC` metadata and terminal records.

## 26. Known blockers
No implementation blocker. External hosted and private staging jobs must run on the final follow-up PR head.

## 27. Evidence
- Prior standalone merges: #11 -> `6fd8947d3b2d2c470548f77f4fbf253fcc86b7e2`; #12 -> `7a9f67ed57de3d4eb7529c91a625efd017bfa88e`; #13 -> `b922c5af30860a6c205f9ee16b817349a7677cd0`.
- Corrective standalone PR #14: commit `fd5ea106f4dc27160810b96a82059bc282cdf3f1`; merge `2b4c8bf6d8e8ef1c8c6b042cd3147e66ffc660fe`; hosted verify and Codacy passed; zero review threads.
- Historical Staff product merge: PR #133 -> `a3b6f2f7c1e9f6b7fe1667974aa0d050533605a9`.
- Historical completion publication: PR #135 -> `0c34478db01cfc9f6f181e47d9fe055e0df84f19`.
- Current handoff: `ai-agents/reports/package-handoffs/2026-08-13-es-x02-currency-provider-followup.md`.

## 28. Merge and synchronization record
Current standalone `main`: `2b4c8bf6d8e8ef1c8c6b042cd3147e66ffc660fe`. Corrected aggregate-main SHA is unset. Component metadata is `SYNC_PENDING`; exact candidate parity is true, while required post-merge parity remains pending.
