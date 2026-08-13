# `ES-X02` — EnthusiaCurrency destructive provider

## 1. Package identity
`ES-X02`; External/multi-repository; primary `COMP-STAFF`; other `COMP-CURRENCY`; priority 110; sequential around shared destructive journals.

## 2. Status
`BLOCKED` / `PARKED_BLOCKED`; standalone implementation exists but static-analysis findings cannot yet be individually dispositioned.

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
`package/es-x02-currency-provider` exists in both repos. Preserve both while blocked; delete only after verified package completion.

## 11. Required PRs
Standalone Currency PR #11 exists. The matching aggregate Staff product PR is intentionally not opened yet because policy requires standalone merge before importing its exact merged state.

## 12. Implementation checklist
- [x] Reconcile both repositories, AGENTS, live heads, and package registry.
- [x] Reserve same-ID package branches in both repositories.
- [x] Define/import versioned moderation contract into standalone Currency.
- [x] Implement operation-owned expiring movement leases.
- [x] Implement exact bank/inventory/Ender Chest snapshot/checksum and source-ordered planning.
- [x] Persist bank revision and use compare-and-set stale-state protection.
- [x] Implement idempotent apply and exact restore with monotonic revision.
- [x] Register provider through Bukkit ServicesManager and fail closed from Staff gateway when missing/version-mismatched.
- [x] Add lock/allocation/persistent-revision regression tests and documentation.
- [x] Fix manual-review rollback defect so unverifiable compensation quarantines instead of claiming rollback.
- [x] Exact current Currency head passes configured Java 21 Maven verification.
- [ ] Inspect/disposition every Codacy finding and reach zero valid unresolved static findings.
- [ ] Freeze final standalone head and finish zero-finding review gate.
- [ ] Execute required canonical Pi validation for the frozen executable scope.
- [ ] Merge standalone PR normally.
- [ ] Import exact merged standalone state into aggregate mirror and update component metadata.
- [ ] Open/cross-link aggregate Staff product PR, run aggregate exact-head gates, and merge normally.
- [ ] Prove post-merge standalone/aggregate parity and clean package branches.

## 13. Acceptance criteria
No balance loss/duplication under success, rejection, timeout, duplicate, crash, or restoration; snapshots/audit are exact; missing/incompatible provider fails safe; both PRs merge and aggregate copy matches standalone.

## 14. Test requirements
Both repos' suites plus reservation/idempotency, concurrent changes, partial failure/rollback, restart/retry, stale restoration, provider absent/version mismatch, authorization/audit, and bounded work tests. Exact current standalone hosted suite passed; remaining runtime/aggregate proof has not run.

## 15. Static-analysis requirements
All configured checks in both repos; zero valid human/CodeRabbit/Codacy findings. Current blocker: Codacy PR #11 summary reports 29 new findings (2 critical, 1 high, 26 medium) but the current GitHub evidence path does not expose individual findings for disposition.

## 16. Documentation requirements
Contract/version, commands/permissions, operation states, recovery/restoration, provider setup/missing behavior, component metadata, package handoff, PR cross-links.

## 17. Security and privacy requirements
Financial-grade authorization/audit; no real balances/player rows in artifacts; fail closed; bounded/redacted logging.

## 18. Migration impact
No new migration was added. Existing SQLite balances schema is upgraded in owning-repo initialization with a persistent revision column; history is not rewritten.

## 19. Bedrock considerations
Staff controls retain text fallback and identity correctness; private client/destructive acceptance remains later.

## 20. Distributed-runtime considerations
Operation ownership, concurrent balance changes, duplicate calls, reconnect, process death, and DB latency are handled through lease ownership, checksums, persistent revisions, idempotency, and quarantine outcomes; private acceptance remains later.

## 21. External-provider considerations
Uses the verified `wsg138/EnthusiaCurrency` API; no reflection/invented runtime contract. Standalone AGENTS/CI remain mandatory.

## 22. Completion definition
Both exact-head PRs merge normally; all required checks/reviews pass; parity true; metadata/merges/hashes recorded; temporary branches cleaned. Private representative destructive acceptance remains `ES-V03`.

## 23. Resume state
Resume standalone Currency PR #11 at exact current package head `5d9dfc7f03d33ee2147141fef4c777ba0e67d939`. Do not import/merge the aggregate mirror yet. First obtain individual Codacy finding details, disposition each, fix all valid findings, and rerun static/review gates. The package is parked while that evidence remains inaccessible.

## 24. Last completed checkpoint
Standalone provider implementation plus compensation repair is durable on PR #11. Exact-head Currency CI run `31657088614` passed Java 21 `mvn -B -ntp verify` on `5d9dfc7...`.

## 25. Remaining checklist
Codacy dispositions; final harsh review/freeze; canonical Pi; standalone normal merge; exact aggregate import/metadata/PR; aggregate hosted/Pi gates as applicable; aggregate normal merge; post-merge parity; containment and branch cleanup.

## 26. Known blockers
Codacy reports 29 unresolved new PR findings, including 2 critical and 1 high, while the available GitHub evidence does not provide individual finding details. Exact unblock: make those finding details accessible, resolve every valid issue or record concrete invalid dispositions, and rerun static analysis on the frozen current head. CodeRabbit also reported a temporary review-rate limit; it is not called a pass and is not used to waive manual review.

## 27. Current evidence
- Staff package start `main`: `4831b1442e572914c86fd8e202e7de6f546868e2`.
- Currency package start `main`: `922223cfff8c325e36f58b6af6adf6d74e4a5417`.
- Currency implementation branch: `package/es-x02-currency-provider`.
- Staff reserved package branch: `package/es-x02-currency-provider` (no aggregate product import yet).
- Currency PR: #11, open/non-draft.
- Current Currency head: `5d9dfc7f03d33ee2147141fef4c777ba0e67d939`.
- Hosted exact-head run: `31657088614`, success, Java 21 `mvn -B -ntp verify`.
- Manual review repair: unverifiable compensation now returns `QUARANTINE_REQUIRED`; exact-head CI passed after repair.
- Codacy summary: 29 unresolved new findings (2 critical, 1 high, 26 medium); not passed.
- CodeRabbit: final automated review unavailable due temporary rate limit; not counted as a pass.
- Canonical Pi/staging: not executed; no pass claimed. Representative live destructive balances remain deferred to `ES-V03`.
- Handoff: `ai-agents/reports/package-handoffs/2026-08-12-es-x02-currency-provider-blocked.md`.

## 28. Merge and synchronization record
No product merge has occurred. One-sided merge is intentionally avoided while standalone static review is blocked. Completion still requires both normal merges, exact post-merge parity, metadata, containment, and temporary branch cleanup.
