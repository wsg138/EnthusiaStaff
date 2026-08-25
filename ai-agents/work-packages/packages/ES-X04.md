# `ES-X04` — EnthusiaCommend reputation provider

## 1. Package identity
`ES-X04`; External/multi-repository; primary `COMP-STAFF`; other `COMP-COMMEND`; priority 125; conditional parallelism only without shared destructive-state overlap.

## 2. Status
`BLOCKED` / `PARKED_BLOCKED`. Existing implementation is preserved and must be resumed only when the recorded canonical Pi artifact-storage blocker becomes actionable.

## 3. Objective
Implement exact reputation mutation, reservation, audit, rollback, and restoration across EnthusiaStaff and EnthusiaCommend.

## 4. Why the package exists
The audit found provider calls but no registered durable moderation workflow for reputation destruction/restoration.

## 5. Included audit IDs
`AUD-ASSET-004` and reputation portions of `AUD-ASSET-005`.

## 6. Included behavior
Implemented versioned `ReputationModerationApi` v2; durable blacklist, reconciliation-hold, and idempotent-operation state; exact category/value snapshots with checksums; optimistic blacklist revisions; authorized/recoverable Staff sanction projection; central give-reputation blocking while preserving receive/view/existing score; provider-missing/incompatible fail-safe behavior; matching standalone/aggregate product copy.

Committed operation persistence is compacted to one canonical snapshot, mismatched committed before/after state fails closed, older matching two-snapshot branch data remains readable, and focused store regression tests cover the compatibility boundary.

## 7. Explicit exclusions
Production reputation changes; leaderboard redesign; currency/market work; Discord bot or website work; LiteBans authority/cutover. Representative private destructive/load acceptance remains `ES-V03`.

## 8. Dependencies
`ES-P08` and `ES-X02` are `COMPLETE`.

## 9. Component and repository boundaries
EnthusiaStaff integration/root plus `components/enthusia-commend/`, and `wsg138/EnthusiaCommend`; no unrelated components, permanent branches, or replacement implementation PRs.

## 10. Required branches
Temporary `package/es-x04-commend-provider` exists in both repos and is intentionally preserved while blocked.

## 11. Required PRs
- Standalone Commend PR #12: open, non-draft, mergeable at last reconciliation; frozen head `325c304512187f274463c31f1649efe0ae56ab7d`.
- Aggregate Staff PR #152: open, non-draft, mergeable at last reconciliation; frozen head `7d525649e293e7af894587089e4e8a7e73597c9c`.

Neither implementation PR is merged because canonical Pi ended in a terminal failure on the exact frozen Staff head.

## 12. Implementation checklist
Product implementation is complete at the frozen heads. This continuation preserved all existing legitimate work and made one paired validation-only correction: `.github/workflows/build.yml` in Commend and its aggregate mirror now use `ref: ${{ github.event.pull_request.head.sha || github.sha }}` so PR validation checks out the exact source SHA instead of GitHub's synthetic merge ref. The paired workflow blobs are identical (`2b4e0addd91a6de10b2f750c4050f2963a33586b`).

Canonical Staff `main` was reconciled at `5c539c73b98bf8325b840e31a709477176077d27` before blocker publication. D04 PR #151, D05/Discord work, website work, and already-merged staging-control-plane work were not modified or absorbed.

## 13. Acceptance criteria
No reputation loss/duplication under retries, concurrent mutation, timeout, crash, or restoration; exact category/value snapshots and audit; missing/incompatible provider safe; both PRs merged normally and post-merge parity true.

## 14. Test requirements
Both repositories' suites plus exact mutation/category handling, concurrent updates, partial failure/rollback, restart/retry, stale restore, provider mismatch, authorization/audit, and bounded work.

Standalone Commend exact-head run `32797266212`, job `97651014296`, passed on `325c304512187f274463c31f1649efe0ae56ab7d`. Raw checkout proves the exact SHA; Temurin Java `21.0.12+8`; Maven `clean verify` passed with 110 tests, 0 failures/errors/skips; PMD passed; JAR artifact `9545261529`, digest `14704bdc74a6ae261226b098e4488dd75ff12152e06b2e962122ce04a153d9bb`.

Staff exact-head Coverage/full-validation run `32797272290`, job `97651031716`, passed on `7d525649e293e7af894587089e4e8a7e73597c9c` with Temurin Java `21.0.12+8`, full build/tests including MariaDB/Testcontainers, 27 provider API source types / 0 leaks, Paper SHA-256 `419ac4fe20584e5a0f4affbcffcfdc158123bf7e8ee5b1b9bf0f1a72287fa1b2`, Velocity SHA-256 `19ec9a590b631e30406a9709ef2080472e7fb63bf03ef607d643a5b80348ad94`, JaCoCo 50.49% line / 41.11% branch / 52.93% instruction, validation artifact `9545398757`, digest `c221e932be32de6052e6b010e2697c270f43e7c0ac3b61d37e3789d6cd584e19`, and successful Codacy coverage upload/final notification.

Staff exact-head Sentinel artifact run `32797272316`, job `97651031742`, passed with artifact `9545283063`, digest `3a5055882d3b3e5bb8496df0615ea4cfc34e885320267e037e0419c17344210e`. Exact restart request comment `5403790637`, durable job `246`, reached terminal `PAPER_RESTART_OK` on the same frozen Staff SHA after two readiness/start-stop cycles against one disposable state.

## 15. Static-analysis requirements
All configured applicable checks/review bots in both repositories; zero valid unresolved findings.

Standalone Commend Codacy reports 0 new issues on the final PR head. Staff aggregate Codacy still displays 100 first-import issues (8 high, 92 medium) under `components/enthusia-commend/`; evidence-backed scoped diagnostics report `staff_x04=0` for the actual Staff X04 integration/contracts/test scope, while the authoritative standalone component scan is clean. No Codacy configuration, rule, threshold, exclusion, or gate was weakened to obtain this disposition.

Review state: Staff PR #152 has zero live inline review threads; all six Commend PR #12 correctness/data-integrity threads are resolved; CodeRabbit status is successful on the final heads.

## 16. Documentation requirements
Contract/version, mutation/restoration semantics, permissions/recovery, provider setup/failure, component metadata, package handoff, cross-links, and canonical blocker state are documented. Current durable blocker handoff: `ai-agents/reports/package-handoffs/2026-08-24-es-x04-commend-provider-artifact-quota-blocked.md`.

## 17. Security and privacy requirements
Strict authorization/audit; fail closed on corrupt or unresolved moderation state; no production reputation/player rows, private data, credentials, deployment, or authority change in this package work.

## 18. Migration impact
No production migration is introduced or executed by X04. Migration history is not rewritten. D04's separate V20 work remains independent and unmerged; canonical `main` remains at V19.

## 19. Bedrock considerations
Staff controls retain identity correctness and text fallback requirements. Representative cross-platform acceptance remains in the later validation packages where originally assigned.

## 20. Distributed-runtime considerations
Implementation handles revision conflicts, duplicate/idempotent requests, restart recovery, provider disappearance/mismatch, reconnect recovery, and bounded periodic reconciliation. Representative destructive/load acceptance remains `ES-V03`.

## 21. External-provider considerations
The verified `wsg138/EnthusiaCommend` API and repository rules are authoritative. No reflection or invented provider behavior is used. Standalone exact-head repository validation is now directly proven and passing.

## 22. Completion definition
Both exact-head PRs must satisfy their own repository rules and all applicable hosted/static/review/runtime gates; canonical Pi must terminate successfully with required persisted evidence for the final Staff source; both PRs then merge normally; post-merge parity must be true; metadata/evidence recorded; temporary branches handled. Private representative destructive/load acceptance remains `ES-V03`.

## 23. Resume state
Resume the existing PRs; do not create replacement implementation branches or duplicate PRs. Frozen heads are Commend `325c304512187f274463c31f1649efe0ae56ab7d` and Staff `7d525649e293e7af894587089e4e8a7e73597c9c`.

## 24. Last completed checkpoint
Product implementation, paired workflow synchronization, standalone exact-head Java 21 build/test/PMD, full Staff exact-head hosted validation, Staff exact-head Sentinel artifact build, and Staff exact-head Sentinel restart are complete.

Canonical Pi public run `32797271342` correlated private run `32797866588`, job `97652750867`, on trusted runner `Lincoln-PI-4`. Exact source/provenance retrieval and the guarded disposable Paper boot/restart runtime test passed. The private upload step then failed because GitHub Actions artifact storage quota is exhausted; public transient-transfer cleanup succeeded and the canonical terminal conclusion is `failure`.

## 25. Remaining checklist
1. Restore enough GitHub Actions artifact storage/quota in the private staging repository for the required sanitized Pi evidence upload to succeed.
2. Run a fresh canonical Pi check on exact frozen Staff head `7d525649e293e7af894587089e4e8a7e73597c9c` (or reconcile and freeze a newer legitimate head if one exists) and require a terminal canonical PASS with persisted evidence and successful cleanup.
3. Reconcile live `main`, both implementation heads, review threads, static state, and any gate invalidated by intervening source/workflow changes.
4. Merge Commend PR #12 and Staff PR #152 with normal merge commits only.
5. Verify default-branch containment and post-merge standalone↔aggregate product parity, update component metadata, safely delete temporary branches, publish X04 `COMPLETE`, and stop.

## 26. Known blockers
- Canonical Pi is terminal `failure` on final Staff head because the required private sanitized evidence artifact upload failed with: `Artifact storage quota has been hit. Unable to upload any new artifacts. Usage is recalculated every 6-12 hours.`
- No owner-approved policy exception exists. Successful runtime execution is not substituted for the failed required evidence gate.

Standalone exact-head validation, Staff exact-head full validation, scoped static disposition, review, Sentinel artifact build, and Sentinel restart are no longer X04 blockers.

## 27. Final evidence
Canonical blocked handoff: `ai-agents/reports/package-handoffs/2026-08-24-es-x04-commend-provider-artifact-quota-blocked.md`. Staff PR #152 comment `5403897920` records final-head hosted/Sentinel/Pi evidence; Commend PR #12 comment `5403801756` records standalone exact-head evidence. No production state or authority changed.

## 28. Merge and synchronization record
No implementation merge occurred. Existing paired implementation branches and PRs are preserved. The last paired change is the identical exact-head workflow checkout fix; completion still requires a successful canonical Pi terminal result, normal implementation merges, and post-merge parity/metadata/containment/branch cleanup. Current classification is `PARKED_BLOCKED`.
