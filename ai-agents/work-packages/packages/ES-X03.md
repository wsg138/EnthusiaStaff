# `ES-X03` — EnthusiaMarket destructive provider

## 1. Package identity
`ES-X03`; External/multi-repository; primary `COMP-STAFF`; other `COMP-MARKET`; priority 120; conditional parallelism only without shared destructive-state overlap.

## 2. Status
`ACTIVE`; registry is authoritative.

## 3. Objective
Implement durable market restriction, reservation, confiscation, rollback, and exact restoration across EnthusiaStaff and EnthusiaMarket.

## 4. Why the package exists
The audit found only read-only/reflection integration and no transactional market operation or recovery path.

## 5. Included audit IDs
`AUD-ASSET-003` and market portions of `AUD-ASSET-005`.

## 6. Included behavior
Supported versioned provider contract; listing/reservation ownership; durable snapshots/operation IDs; restriction/confiscation; idempotent rollback/restoration; retry/restart/race handling; provider missing/version mismatch; matching aggregate copy and parity.

## 7. Explicit exclusions
Production listings; whole-market rollback; currency/reputation work; unverified reflection against provider internals.

## 8. Dependencies
`ES-P08` and `ES-X02` must be `COMPLETE`.

## 9. Component and repository boundaries
EnthusiaStaff integration/root plus `components/enthusia-market/`, and `wsg138/EnthusiaMarket`; no unrelated components, permanent branches, or isolated PR.

## 10. Required branches
Temporary `package/es-x03-market-provider` in both repos (or stricter compatible standalone convention); delete after verified merges.

## 11. Required PRs
Two same-ID cross-referenced PRs: standalone Market and aggregate EnthusiaStaff. No third/isolated PR.

## 12. Implementation checklist
Reconcile repos/AGENTS/heads/licenses; verify/import aggregate source; define contract/state machine; implement both sides; race/failure/restart tests; docs/metadata/state/handoff; review/freeze/validate both; merge; parity; cleanup.

## 13. Acceptance criteria
No listing/item loss or double action during concurrent sale/reservation/confiscation, timeout, crash, retry, or restoration; ownership/audit exact; provider absence safe; both PRs merged and parity true.

## 14. Test requirements
Both repos' suites plus listing/reservation races, partial failure/rollback, restart/retry, stale restore, provider absent/version mismatch, authorization/audit, and bounded query/work tests.

## 15. Static-analysis requirements
All configured checks/review bots in both repos; zero valid unresolved findings.

## 16. Documentation requirements
Contract/version, operation states, permissions, recovery/restoration, provider setup/failure, component metadata, package handoff, cross-links.

## 17. Security and privacy requirements
Financial-grade authorization/audit; no production listings/player rows; fail closed; bounded/redacted evidence.

## 18. Migration impact
Only new immutable migrations in owning repo after live boundary verification; clean/upgrade/checksum tests; no history edits.

## 19. Bedrock considerations
Staff controls need text fallback; item/listing identity must be platform-neutral; acceptance later.

## 20. Distributed-runtime considerations
Multiple processes, listing ownership, concurrent purchases, duplicate requests, reconnect, process death, and DB latency.

## 21. External-provider considerations
Use verified `wsg138/EnthusiaMarket` contracts and AGENTS/CI; no reflection or invented APIs.

## 22. Completion definition
Both exact-head PRs merge normally; checks/reviews pass; aggregate parity true; metadata/evidence recorded; temp branches handled. Private destructive acceptance remains `ES-V03`.

## 23. Resume state
Claimed on 2026-08-13 after live reconciliation. Staff and Market use temporary branch `package/es-x03-market-provider`, based respectively on `49e5aa999b43193181aafabbb75811c820fa03c7` and `bc24f1010642d6042307bc13a32fb33cc94e8883`. Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-13-es-x03-market-provider.md`.

## 24. Last completed checkpoint
Dependencies, standalone availability, default heads, branch/PR conflicts, repository rules, migration ceilings, and package boundaries were reconciled. Isolated same-ID branches were created from both exact default heads without disturbing unrelated work.

## 25. Remaining checklist
Define and test the provider contract/state machine; implement durable Market operations and acquisition fencing; integrate the Staff journal/coordinator and commands; import the exact standalone tree; document recovery and operator behavior; open cross-linked draft PRs; run all exact-head gates and reviews; merge normally; prove parity; clean temporary branches; publish terminal state.

## 26. Known blockers
No implementation blocker. Representative private destructive/load acceptance remains deferred to `ES-V03`; no production listings or player data are authorized.

## 27. Final evidence
Starting bases recorded above. Final heads, PRs, merges, contract version, checks/reviews, parity manifests, and hashes remain pending.

## 28. Merge and synchronization record
Unset. One-sided merge means `SYNC_PENDING`; completion requires both merges, parity, metadata, containment, and temp branch cleanup.
