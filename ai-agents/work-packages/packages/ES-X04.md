# `ES-X04` — EnthusiaCommend reputation provider

## 1. Package identity
`ES-X04`; External/multi-repository; primary `COMP-STAFF`; other `COMP-COMMEND`; priority 125; conditional parallelism only without shared destructive-state overlap.

## 2. Status
Initial `PLANNED`; registry is authoritative.

## 3. Objective
Implement exact reputation mutation, reservation, audit, rollback, and restoration across EnthusiaStaff and EnthusiaCommend.

## 4. Why the package exists
The audit found provider calls but no registered durable moderation workflow for reputation destruction/restoration.

## 5. Included audit IDs
`AUD-ASSET-004` and reputation portions of `AUD-ASSET-005`.

## 6. Included behavior
Versioned exact-mutation API; durable operation/reservation; before/after snapshots; authorized mutation; idempotent retry/rollback/restoration; concurrency/restart/provider mismatch; case/audit linkage; matching aggregate copy/parity.

## 7. Explicit exclusions
Production reputation changes; leaderboard redesign; currency/market work.

## 8. Dependencies
`ES-P08` and `ES-X02` must be `COMPLETE`.

## 9. Component and repository boundaries
EnthusiaStaff integration/root plus `components/enthusia-commend/`, and `wsg138/EnthusiaCommend`; no unrelated components, permanent branches, or isolated PR.

## 10. Required branches
Temporary `package/es-x04-commend-provider` in both repos (or stricter compatible standalone convention); delete after verified merges.

## 11. Required PRs
Two same-ID cross-referenced PRs: standalone Commend and aggregate EnthusiaStaff. No third/isolated PR.

## 12. Implementation checklist
Reconcile repos/AGENTS/heads; verify/import aggregate source; define exact contract/state machine; implement both sides; concurrency/failure/restart tests; docs/metadata/state/handoff; review/freeze/validate both; merge; parity; cleanup.

## 13. Acceptance criteria
No reputation loss/duplication under retries, concurrent mutation, timeout, crash, or restoration; exact category/value snapshots and audit; missing/incompatible provider safe; both PRs merged and parity true.

## 14. Test requirements
Both repos' suites plus exact mutation/category handling, concurrent updates, partial failure/rollback, restart/retry, stale restore, provider mismatch, authorization/audit, and bounded work.

## 15. Static-analysis requirements
All configured checks/review bots in both repos; zero valid unresolved findings.

## 16. Documentation requirements
Contract/version, mutation/restoration semantics, permissions/recovery, provider setup/failure, component metadata, package handoff, cross-links.

## 17. Security and privacy requirements
Strict authorization/audit; no production reputation/player rows; fail closed; redacted evidence.

## 18. Migration impact
Only new immutable migrations in owning repo after boundary verification; clean/upgrade/checksum tests; no history edits.

## 19. Bedrock considerations
Staff controls need text fallback and identity correctness; acceptance later.

## 20. Distributed-runtime considerations
Multiple processes, revision conflicts, duplicate requests, reconnect, process death, and DB latency.

## 21. External-provider considerations
Use verified `wsg138/EnthusiaCommend` API and its AGENTS/CI; no reflection or invented behavior.

## 22. Completion definition
Both exact-head PRs merge normally; all checks/reviews pass; parity true; metadata/evidence recorded; temp branches handled. Private acceptance remains `ES-V03`.

## 23. Resume state
Unassigned; no branch/PR/handoff. Start only after dependencies and assignment.

## 24. Last completed checkpoint
Definition/metadata only; no implementation began.

## 25. Remaining checklist
All two-repo implementation, tests, review, merge, parity, and evidence remain.

## 26. Known blockers
Dependencies; private destructive environment deferred to `ES-V03`.

## 27. Final evidence
Unset: two bases/heads/PRs/merges, contract version, checks/reviews, parity manifests/hashes.

## 28. Merge and synchronization record
Unset. One-sided merge means `SYNC_PENDING`; completion requires both merges, parity, metadata, containment, and temp branch cleanup.
