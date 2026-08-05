# `ES-X02` — EnthusiaCurrency destructive provider

## 1. Package identity
`ES-X02`; External/multi-repository; primary `COMP-STAFF`; other `COMP-CURRENCY`; priority 110; sequential around shared destructive journals.

## 2. Status
Initial `PLANNED`; registry is authoritative.

## 3. Objective
Implement transactional currency removal and exact restoration across EnthusiaStaff and EnthusiaCurrency.

## 4. Why the package exists
The audit marks currency destruction critical and provider-blocked: runtime paths lack proof and require a compatible first-party contract plus private acceptance later.

## 5. Included audit IDs
`AUD-ASSET-002` and currency portion of `AUD-ASSET-005`.

## 6. Included behavior
Versioned supported API; reservation/operation IDs; before/after balance snapshots; atomic removal; idempotent retry; rollback/restoration; provider present/missing/version mismatch; case/audit linkage; matching aggregate copy/parity.

## 7. Explicit exclusions
Production balances; live destructive testing (`ES-V03`); market/reputation; economy redesign outside required contract.

## 8. Dependencies
`ES-P08` must be `COMPLETE`.

## 9. Component and repository boundaries
EnthusiaStaff integration/root plus `components/enthusia-currency/`, and `wsg138/EnthusiaCurrency`; no unrelated components, permanent branches, or isolated PR.

## 10. Required branches
Temporary `package/es-x02-currency-provider` in both repos (or stricter compatible standalone convention); delete after verified merges.

## 11. Required PRs
Two same-ID cross-referenced PRs: standalone Currency and aggregate EnthusiaStaff. No third/isolated PR.

## 12. Implementation checklist
Reconcile both repos/AGENTS/heads; verify/import aggregate source; define contract/state machine; implement both sides; failure/concurrency/restart tests; docs/metadata/state/handoff; review/freeze/validate both; merge both; parity compare; cleanup.

## 13. Acceptance criteria
No balance loss/duplication under success, rejection, timeout, duplicate, crash, or restoration; snapshots/audit are exact; missing/incompatible provider fails safe; both PRs merge and aggregate copy matches standalone.

## 14. Test requirements
Both repos' suites plus reservation/idempotency, concurrent changes, partial failure/rollback, restart/retry, stale restoration, provider absent/version mismatch, authorization/audit, and bounded work tests.

## 15. Static-analysis requirements
All configured checks in both repos; zero valid human/CodeRabbit/Codacy findings.

## 16. Documentation requirements
Contract/version, commands/permissions, operation states, recovery/restoration, provider setup/missing behavior, component metadata, package handoff, PR cross-links.

## 17. Security and privacy requirements
Financial-grade authorization/audit; no real balances/player rows in artifacts; fail closed; bounded/redacted logging.

## 18. Migration impact
New immutable migration only in owning repo when essential after boundary verification; clean/upgrade/checksum tests; no history edits.

## 19. Bedrock considerations
Staff controls need text fallback and identity correctness; private client/destructive acceptance remains later.

## 20. Distributed-runtime considerations
Multiple processes, operation ownership, concurrent balance changes, duplicate messages, reconnect, process death, and DB latency.

## 21. External-provider considerations
Use the verified `wsg138/EnthusiaCurrency` API; no reflection/invented contract; standalone AGENTS/CI are mandatory.

## 22. Completion definition
Both exact-head PRs merge normally; all checks/reviews pass; parity true; metadata/merges/hashes recorded; temp branches cleaned. Private destructive acceptance remains `ES-V03`.

## 23. Resume state
Unassigned; no branch/PR/handoff. Start only after `ES-P08` and assignment.

## 24. Last completed checkpoint
Definition/metadata only; no implementation began.

## 25. Remaining checklist
All two-repo implementation, tests, review, merge, parity, and evidence remain.

## 26. Known blockers
Dependency `ES-P08`; private destructive environment deferred to `ES-V03`.

## 27. Final evidence
Unset: two bases/heads/PRs/merges, contract version, checks/reviews, parity manifests/hashes.

## 28. Merge and synchronization record
Unset. One-sided merge means `SYNC_PENDING`; completion requires both merges, parity, metadata, containment, and temporary branch cleanup.
