# `ES-X04` — EnthusiaCommend reputation provider

## 1. Package identity
`ES-X04`; External/multi-repository; primary `COMP-STAFF`; other `COMP-COMMEND`; priority 125; conditional parallelism only without shared destructive-state overlap.

## 2. Status
`BLOCKED` / `PARKED_BLOCKED`. Existing implementation is preserved as an actionable continuation when the recorded evidence blockers change.

## 3. Objective
Implement exact reputation mutation, reservation, audit, rollback, and restoration across EnthusiaStaff and EnthusiaCommend.

## 4. Why the package exists
The audit found provider calls but no registered durable moderation workflow for reputation destruction/restoration.

## 5. Included audit IDs
`AUD-ASSET-004` and reputation portions of `AUD-ASSET-005`.

## 6. Included behavior implemented
Versioned `ReputationModerationApi` v2; durable blacklist and idempotent operation records; exact category/value snapshots with checksums; optimistic blacklist revisions; durable reconciliation holds; authorized/recoverable Staff sanction projection; central give-reputation blocking while preserving receive/view/existing score; provider-missing/incompatible fail-safe behavior; matching standalone/aggregate product copy.

This continuation also compacted committed operation persistence to one canonical snapshot, rejects mismatched committed before/after state, preserves read compatibility with the older matching two-snapshot branch format, and adds three focused store regression tests.

## 7. Explicit exclusions
Production reputation changes; leaderboard redesign; currency/market work; Discord bot or website work; LiteBans authority/cutover. Representative private destructive/load acceptance remains `ES-V03`.

## 8. Dependencies
`ES-P08` and `ES-X02` are `COMPLETE`.

## 9. Component and repository boundaries
EnthusiaStaff integration/root plus `components/enthusia-commend/`, and `wsg138/EnthusiaCommend`; no unrelated components, permanent branches, or isolated implementation PR.

## 10. Required branches
Temporary `package/es-x04-commend-provider` exists in both repos and is intentionally preserved while blocked.

## 11. Required PRs
- Standalone Commend PR #12: open, non-draft, mergeable at last reconciliation; frozen head `30ac1afbb6b45e958c6972330c42a870d619d530`.
- Aggregate Staff PR #152: open, non-draft, mergeable at last reconciliation; frozen head `9d44bbcac4d3cb9a489e9c9f755e80ae7ace28b1`.

Neither PR is merged because required exact-head evidence is incomplete.

## 12. Implementation checkpoint
- Staff canonical `main` reconciled at `cb19463f16e124564ccbc17034b4c18f5cd0281f` before blocker publication.
- D04 PR #151, D05 PR #153, website work, and staging-control-plane PR #156 were preserved and not absorbed.
- Pre-merge product parity is exact under the canonical aggregate-only metadata exclusion: standalone root tree `16447cb9ad2f41597d2eb616caa00164b2d130ae`; aggregate Commend tree `c454006f6d3d732d2a212afcc980520ff1c54ec0`; identical Git object IDs for all shared product entries, with only aggregate `COMPONENT-METADATA.md` extra.

## 13. Acceptance criteria
No reputation loss/duplication under retries, concurrent mutation, timeout, crash, or restoration; exact category/value snapshots and audit; missing/incompatible provider safe; both PRs merged normally and post-merge parity true.

## 14. Test requirements
Both repos' suites plus exact mutation/category handling, concurrent updates, partial failure/rollback, restart/retry, stale restore, provider mismatch, authorization/audit, and bounded work.

## 15. Static-analysis requirements
All configured applicable checks/review bots in both repos; zero valid unresolved findings.

Current Staff Codacy summary is **not passing**: `100` new issues (`8` high, `92` medium). These must be repaired or individually invalidated with evidence and followed by a clean exact-head result before merge.

## 16. Review result
- Staff PR #152: zero live inline review threads.
- Commend PR #12: all six correctness/data-integrity inline threads resolved after repairs.
- CodeRabbit status is successful on the frozen Staff head.
- No valid unresolved inline correctness finding is currently known, but static-analysis findings above remain non-passing.

## 17. Standalone validation state
Commend workflow run `32763949487`, job `97549027434`, completed successfully with Java 21, Maven `clean verify`, 110 tests / 0 failures / 0 errors / 0 skipped, PMD success, and JAR artifact `9533731303` with ZIP digest `sha256:0455841ff353def42d339316a7484b2bec42ed1e3430484e01dcf594aac3fbd7`.

This is historical/non-passing exact-head evidence because its raw checkout proves synthetic merge commit `cf6f64dcff0639a724b07ef9c6bebac78429c86d` was tested instead of exact standalone head `30ac1afbb6b45e958c6972330c42a870d619d530`. The workflow now triggers package-branch pushes with read-only permissions, but the connected commit-workflow listing available to this worker exposes PR-triggered runs rather than a retrievable exact-head push run. No exact-head standalone PASS is claimed.

## 18. Aggregate hosted/runtime state
Frozen Staff head `9d44bbcac4d3cb9a489e9c9f755e80ae7ace28b1`:
- Sentinel exact-artifact run `32763957749`, job `97549055756`: PASS for exact checkout; Temurin Java `21.0.12+8`; Paper `shadowJar` PASS; artifact `9533760777`, digest `sha256:285503604af4a7d2bd0bde450acf594909490767fdfc433a66e74ae9fe2d6d16`.
- Exact Sentinel runtime is still non-passing/missing. Visible `PAPER_RESTART_OK` evidence on PR #152 belongs only to superseded heads `515aac798c505b97d024118e3e327452c28ed67a` (job 226) and `da0dae3bee959a34928b9eecf9aa5755cb08d097` (job 227).
- Coverage/full-validation run `32763957896`, job `97549217101`, was still executing when the blocker record was prepared and is not counted as PASS until terminal exact-head logs are inspected.

## 19. Canonical Pi state
Canonical Pi is required and **not passed**. The existing `pull_request_target` Pi path cannot be discovered/verified through the connected commit-workflow listing, and no stable exact-head public/private Pi run correlation is recorded on PR #152.

Independent PR #156 (`Fix canonical Pi staging PR command and exact-head status`) is intended to repair this trusted control-plane evidence gap. At blocker publication it remained open and unmerged at `279dbc12d802a347e97ff0c19666fd564f5dec8e`. This worker did not modify or merge that independent work.

Sentinel does not substitute for canonical Pi. No owner-approved infrastructure exception is claimed.

## 20. Completion definition
Both exact-head PRs must satisfy their own repository rules and all applicable hosted/static/review/runtime gates; canonical Pi must be verified for the final Staff source; both PRs then merge normally; post-merge parity must be true; metadata/evidence recorded; temporary branches handled. Private representative destructive/load acceptance remains `ES-V03`.

## 21. Resume state
Resume the existing PRs; do not create replacement implementation branches or duplicate PRs.

Exact unblock checklist:
1. canonical trusted Pi control-plane observability/execution becomes available and a correlated exact-head public/private Pi run can be inspected;
2. a directly inspectable exact-head standalone Commend Java 21 build/test/static result passes;
3. all applicable Codacy/static findings are resolved or invalidated with evidence and a clean final exact-head result exists;
4. Staff Coverage/full validation and Sentinel runtime `PAPER_RESTART_OK` pass on the final exact source if still applicable;
5. live `main`, both provider trees, and review state are reconciled; any changed executable head reruns invalidated gates;
6. both implementation PRs merge with normal merge commits only;
7. post-merge standalone↔aggregate parity, component metadata, containment, and safe branch cleanup are verified before publishing `COMPLETE`.

## 22. Known blockers
- standalone observable validation is merge-ref-only rather than admissible exact-head evidence;
- Staff Codacy reports 100 new issues and is not up to standards;
- no Sentinel runtime `PAPER_RESTART_OK` exists for frozen Staff head;
- final Staff Coverage run is not yet terminal at publication time;
- canonical Pi exact-head public/private evidence is unavailable through the current trusted control plane while independent fix PR #156 remains unmerged.

Any one of these is sufficient to prevent merge; none is relabeled as passing.

## 23. Final evidence
Canonical blocked handoff: `ai-agents/reports/package-handoffs/2026-08-24-es-x04-commend-provider-blocked.md`.

## 24. Merge and synchronization record
No implementation merge occurred. Pre-merge shared product bytes are synchronized under the aggregate metadata exclusion, but completion still requires normal merges and post-merge parity. Current classification is `PARKED_BLOCKED`.
