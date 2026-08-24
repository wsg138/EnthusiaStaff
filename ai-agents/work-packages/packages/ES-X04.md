# `ES-X04` — EnthusiaCommend reputation provider

## 1. Package identity
`ES-X04`; External/multi-repository; primary `COMP-STAFF`; other `COMP-COMMEND`; priority 125; conditional parallelism only without shared destructive-state overlap.

## 2. Status
`ACTIVE`; `ACTIONABLE_CONTINUATION`. This worker owns X04 until terminal completion or a genuine recorded blocker.

## 3. Objective
Implement exact reputation mutation, reservation, audit, rollback, and restoration across EnthusiaStaff and EnthusiaCommend.

## 4. Why the package exists
The audit found provider calls but no registered durable moderation workflow for reputation restriction/restoration.

## 5. Included audit IDs
`AUD-ASSET-004` and reputation portions of `AUD-ASSET-005`.

## 6. Included behavior
Versioned exact-mutation API; durable operation/reservation; before/after snapshots; authorized mutation; idempotent retry/rollback/restoration; concurrency/restart/provider mismatch; case/audit linkage; matching aggregate copy/parity.

## 7. Explicit exclusions
Production reputation changes; leaderboard redesign; currency/market work; representative destructive/load acceptance owned by `ES-V03`.

## 8. Dependencies
`ES-P08` and `ES-X02` are `COMPLETE`. `ES-X03` is not a dependency and remains independently parked on its own runtime-host condition.

## 9. Component and repository boundaries
EnthusiaStaff integration/root plus `components/enthusia-commend/`, and `wsg138/EnthusiaCommend`; no unrelated components, permanent branches, or isolated PR.

## 10. Required branches
- `wsg138/EnthusiaStaff`: `package/es-x04-commend-provider`
- `wsg138/EnthusiaCommend`: `package/es-x04-commend-provider`

Both exist and are preserved.

## 11. Required PRs
- EnthusiaCommend PR #12: https://github.com/wsg138/EnthusiaCommend/pull/12
- EnthusiaStaff PR #152: https://github.com/wsg138/EnthusiaStaff/pull/152

Both remain draft while implementation and exact-head validation continue.

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
No migration is currently required by the provider implementation. Any later migration must be new/immutable after boundary verification; no history edits.

## 19. Bedrock considerations
Staff controls need text fallback and identity correctness; representative acceptance remains later platform staging.

## 20. Distributed-runtime considerations
The Staff sanction is the authoritative MariaDB-backed state. Provider reconciliation uses optimistic blacklist revisions, exact reputation checksums, deterministic operation identities, post-commit notification, join recovery, and periodic retry for tracked targets.

## 21. External-provider considerations
`wsg138/EnthusiaCommend` has no repository `AGENTS.md`; live Maven/Actions configuration governs its standalone validation. The provider is registered through Bukkit ServicesManager with API version 2; no reflection is used.

## 22. Completion definition
Both exact-head PRs merge normally; all required checks/reviews pass; parity true; metadata/evidence recorded; temp branches handled. Private representative destructive/load acceptance remains `ES-V03` and is not falsely claimed here.

## 23. Resume state
Resume the live X04 branches and PRs above. Staff `main` was `f129226ac017c97fc4126629dd0f47bff729abd6` at the current reconciliation; its intervening changes after the X04 merge base were Discord package documentation only and did not overlap X04 product paths. D04 PR #151 and parked X03 PR #139 are independent and must not be modified.

## 24. Last completed checkpoint
Standalone API v2 and durable provider state are implemented. Exact reputation category/value/score checksums fence apply/remove operations; corrupt persisted moderation state fails provider startup closed; central reputation writes reject blacklisted givers while received/viewed/existing reputation remains unchanged. Staff now projects the authoritative `REPUTATION_BLACKLIST` sanction through the provider and attaches the existing sanction lookup to `PunishmentService`. A harsh-review repair replaced permanent rejection at the provider journal limit with bounded oldest-operation eviction; monotonic blacklist revisions keep evicted ancient retries stale.

## 25. Remaining checklist
Finish current harsh-review repairs and tests; synchronize/import the full standalone tree under `components/enthusia-commend/`; merge live Staff `main` normally into the X04 branch before final freeze; complete both repositories' exact-head build/static/review gates; run applicable Staff Sentinel/Pi gates; prove standalone↔aggregate parity; publish terminal package/registry/workspace/handoff evidence; normal-merge both PRs; verify post-merge parity/containment; safely delete temporary branches.

## 26. Known blockers
None currently. Missing or failed validation remains non-passing evidence; `ES-V03` owns representative destructive/load acceptance.

## 27. Current evidence
Before the latest standalone journal repair, Commend exact head `6d477b2a400da2047bbb4bdebafbf24f792250c4` passed hosted workflow `32681862577` / job `97299811877` including Temurin Java 21, `mvn clean verify`, PMD, and JAR artifact creation; CodeRabbit status was success and live inline review threads were zero. Those results are historical after the newer standalone commit and will be rerun at the final frozen head. No final Staff exact-head validation is claimed yet.

## 28. Merge and synchronization record
Not merged. Aggregate source import/parity is still pending. One-sided merge is forbidden from being called complete; completion requires both normal merges, parity, metadata, containment, and temporary-branch cleanup.
