# `ES-X03` — EnthusiaMarket destructive provider

## 1. Package identity
`ES-X03`; External/multi-repository; primary `COMP-STAFF`; other `COMP-MARKET`; priority 120; conditional parallelism only without shared destructive-state overlap.

## 2. Status
`BLOCKED` / `PARKED_BLOCKED`. Implementation and review are stabilized on the existing paired PRs, but exact-head ordinary repository-owned GitHub Actions validation is unavailable in `wsg138/EnthusiaMarket`. Registry is authoritative.

## 3. Objective
Implement durable market restriction, reservation, confiscation, rollback, and exact restoration across EnthusiaStaff and EnthusiaMarket.

## 4. Why the package exists
The audit found only read-only/reflection integration and no transactional market operation or recovery path.

## 5. Included audit IDs
`AUD-ASSET-003` and market portions of `AUD-ASSET-005`.

## 6. Included behavior
Supported versioned provider contract; listing/reservation ownership; durable snapshots/operation IDs; restriction/confiscation; idempotent rollback/restoration; retry/restart/race handling; provider missing/version mismatch; matching aggregate copy and parity.

## 7. Explicit exclusions
Production listings; whole-market rollback; currency/reputation work; unverified reflection against provider internals; representative destructive/load/process-kill acceptance assigned to `ES-V03`.

## 8. Dependencies
`ES-P08` and `ES-X02` are `COMPLETE`.

## 9. Component and repository boundaries
EnthusiaStaff integration/root plus `components/enthusia-market/`, and `wsg138/EnthusiaMarket`; no unrelated components, permanent branches, or isolated PR. The external Market repository may use only its ordinary repository-owned/public CI. Enthusia private staging/Pi infrastructure must remain outside Market and BadgersMC repositories.

## 10. Required branches
Existing `package/es-x03-market-provider` remains open in both repos until verified paired merges. Market preservation branch `preserve/es-x03-post-candidate-556b4b4-20260814` retains unrelated post-candidate cleanup history and must not be deleted while unique work remains only there.

## 11. Required PRs
Existing paired PRs only: Market PR `wsg138/EnthusiaMarket#3` and Staff PR `wsg138/EnthusiaStaff#139`. Both remain open and unmerged. No third product PR was created.

## 12. Implementation checkpoint
Continuation started from Staff `main` `49e5aa999b43193181aafabbb75811c820fa03c7`, Staff PR head `e6ad4cb4bf7d91ecdfaa43b3e278992c919347b2`, Market `main` `bc24f1010642d6042307bc13a32fb33cc94e8883`, and Market PR head `556b4b42e0d730f74c8f5423de4453c6cd8946b4`.

The 16 Market commits after reviewed candidate `62408695063d03303026766befb065a0f1f51044` were classified. `825fc2cf5aa4981a8eb6c73c385e1118cb50f618` is retained as valid ES-X03 API/static remediation. Broad historical Market complexity/refactor cleanup beginning at `45d6bf8c8ace0af4de41810388365d8f54fa1f94` through `556b4b42e0d730f74c8f5423de4453c6cd8946b4` is outside X03 scope, was removed from the candidate using an ordinary forward commit, and is preserved intact on the preservation branch. No force-push, rebase, squash, or destructive reset was used.

Current scoped Market head is `aa7cf6025bd8634c1106e6457cd49e7baa182f51`. Current synchronized Staff implementation head is `fb0afbec22b68bdfb9ba910737f8ff254d23c4ce`.

## 13. Acceptance criteria
No listing/item loss or double action during concurrent sale/reservation/confiscation, timeout, crash, retry, or restoration; ownership/audit exact; provider absence safe; both PRs merged and parity true.

## 14. Test requirements
Both repos' suites plus listing/reservation races, partial failure/rollback, restart/retry, stale restore, provider absent/version mismatch, authorization/audit, and bounded query/work tests. Final exact-head Market execution is currently blocked by unavailable ordinary Actions; older candidate tests are historical evidence only.

## 15. Static-analysis requirements
All configured checks/review bots in both repos; zero valid unresolved findings. Standalone Market baseline at `6240869` was Lizard 40 repository / 35 production, PMD 0, Trivy 0, and one pre-existing unpinned Codacy action finding. Retained `825fc2c` pins that action and removes X03 API analyzer findings. Historical aggregate-import Market debt is not relabeled clean or used to justify a general refactor.

## 16. Documentation requirements
Contract/version, operation states, permissions, recovery/restoration, provider setup/failure, component metadata, package handoff, cross-links. Current PRs and component metadata document the blocker and parity state.

## 17. Security and privacy requirements
Financial-grade authorization/audit; no production listings/player rows; fail closed; bounded/redacted evidence. No private Pi/staging runner configuration, labels, Staff-Staging identifiers, bridge/dispatch, private staging secrets/topology/credentials, artifact-transfer mechanisms, or private Sentinel infrastructure were added to Market or BadgersMC repositories.

## 18. Migration impact
Market V001–V024 remain immutable; ES-X03 owns V025 only. Staff V1–V18 remain immutable; ES-X03 owns V19 only. No Flyway repair or historical migration rewrite. The pre-existing Market V001 MariaDB clean-install indexed-`TEXT` limitation remains visible and separate; V024→V025 is the applicable upgrade boundary.

## 19. Bedrock considerations
Staff controls need text fallback; item/listing identity must be platform-neutral; representative acceptance remains later work.

## 20. Distributed-runtime considerations
Multiple processes, listing ownership, concurrent purchases, duplicate requests, reconnect, process death, and DB latency. Current provider code uses durable moderation/player fences, full immutable replay identity, optimistic revisions/checksums, bounded snapshot/list/executor work, and explicit shutdown/timeout conflict handling.

## 21. External-provider considerations
The provider is a Bukkit service for trusted installed same-JVM plugins. EnthusiaStaff authenticates/authorizes the human operator before calling it; Market independently validates operation identity, target, stall, case, checksum, revision, and durable state transition. A caller-supplied token inside the same JVM is not treated as a sandbox boundary.

## 22. Completion definition
Both exact-head PRs must merge normally only after required checks/reviews pass; aggregate parity must be recomputed with `tools/component-sync/component_sync.py`; metadata/evidence must be terminal; temporary branches may be cleaned only after containment. Private destructive acceptance remains `ES-V03`.

## 23. Resume state
`BLOCKED` / `PARKED_BLOCKED`. Resume the existing PRs only after ordinary repository-owned GitHub Actions execution becomes available for `wsg138/EnthusiaMarket`. Do not create replacement product PRs or begin another ES-X03 branch.

## 24. Last completed checkpoint
Post-candidate scope was reconciled and preserved; all live Market inline review threads are resolved after current-code verification; valid late findings were fixed, including stale blacklist snapshot restoration fencing and bounded MariaDB concurrency waits; aggregate provider bytes are synchronized to Market `aa7cf6025bd8634c1106e6457cd49e7baa182f51` under canonical exclusions.

Current parity evidence: `src/` tree `49a69707e465e9befeb6fb16d93ef64c629cb3bb`, `src/main/` `eafeefa085cd99463e898f445713535c5d4433cf`, and `src/test/` `2c3d1d612b0a89ca7c9f27758bb928f3c74a7d71` are identical in standalone and aggregate. All other product blobs/subtrees match; `gradlew` bytes match and its aggregate mode difference is intentionally ignored by the canonical content comparator. The old normalized hash `8d27f4d9c64ca52feecd1df6200a45314610fa0df4b27da9d39b444152007c3b` belongs only to obsolete candidate `6240869` and is not current evidence.

## 25. Remaining checklist
1. Restore/enable ordinary Market GitHub Actions execution or expose an existing repository-owned workflow through connected tooling.
2. Freeze the exact resulting Market head and run required Java 21 build/test, disposable Docker/MariaDB provider tests, detekt/static/security, Wiki/docs, and runtime-artifact inspection.
3. Apply only valid in-scope findings; if executable content changes, resynchronize Staff and invalidate/re-run affected gates.
4. Recompute final canonical aggregate/standalone SHA-256 parity.
5. Finish/re-run all required exact-head Staff hosted/static/runtime/review/Sentinel gates. Private Pi staging is not substituted for missing Market CI; representative destructive runtime acceptance remains ES-V03.
6. Merge both implementation PRs with normal merge commits in the live-policy order, verify parents/containment/default heads, prove post-merge parity, update component metadata to `IN_SYNC`, publish terminal canonical state, then clean only safely contained temporary branches.

## 26. Known blockers
Hard blocker: `wsg138/EnthusiaMarket` currently has zero GitHub Actions runs in its repository history, including no run for exact head `aa7cf6025bd8634c1106e6457cd49e7baa182f51`. The connected GitHub worker cannot dispatch a workflow, and the current repository-owned build workflow has no manual dispatch trigger. Missing exact-head ordinary Market validation cannot be called passing.

## 27. Current evidence
Historical reviewed candidate `6240869`: Java 21 clean Market graph 11 tasks; 120 suites / 637 tests, zero failures/errors; separate disposable MariaDB 11.8.3 provider run all 5 tests passed; runtime JAR 4,138,102 bytes SHA-256 `ba821a7fdc509f2a94ba155d911351c04ab540c15f8da21e5f1c31dd333f9d6f`. These results are not reused as final-head validation after later executable/test changes.

Historical Staff provider-integrated evidence: 39-task Java 21 graph; 222 suites / 951 tests; 50 integration suites / 192 tests with no skips/failures; Paper SHA-256 `e275fd6912dd8b282d65ea735a72eb4f258a8e4e7ed5b9224abe44cb5be35d15`; Velocity SHA-256 `85fee16bbdaf4eb8916f1a64506dd4dcd3b3b195a383ab1adb5d7c3c632affac`; provider API leakage 0. Fresh Staff exact-head hosted runs are recorded on PR #139 and remain secondary to the Market hard blocker.

## 28. Merge and synchronization record
No ES-X03 implementation merge occurred. Market `main` remains `bc24f1010642d6042307bc13a32fb33cc94e8883`; Staff implementation base remains `49e5aa999b43193181aafabbb75811c820fa03c7`. Both implementation PRs stay open. One-sided merge is forbidden while blocked. Current aggregate product content matches standalone candidate `aa7cf6025bd8634c1106e6457cd49e7baa182f51`; final canonical hash/post-merge parity remain pending.
