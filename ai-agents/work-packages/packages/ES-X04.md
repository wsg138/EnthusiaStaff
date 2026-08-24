# `ES-X04` — EnthusiaCommend reputation provider

## 1. Package identity
`ES-X04`; External/multi-repository; primary `COMP-STAFF`; other `COMP-COMMEND`; priority 125; conditional parallelism only without shared destructive-state overlap.

## 2. Status
`BLOCKED` / `PARKED_BLOCKED`. Existing implementation is preserved and must be resumed only when one of the recorded evidence conditions becomes actionable.

## 3. Objective
Implement exact reputation mutation, reservation, audit, rollback, and restoration across EnthusiaStaff and EnthusiaCommend.

## 4. Why the package exists
The audit found provider calls but no registered durable moderation workflow for reputation destruction/restoration.

## 5. Included audit IDs
`AUD-ASSET-004` and reputation portions of `AUD-ASSET-005`.

## 6. Included behavior
Implemented versioned `ReputationModerationApi` v2; durable blacklist, reconciliation-hold, and idempotent-operation state; exact category/value snapshots with checksums; optimistic blacklist revisions; authorized/recoverable Staff sanction projection; central give-reputation blocking while preserving receive/view/existing score; provider-missing/incompatible fail-safe behavior; matching standalone/aggregate product copy.

This continuation also compacted committed operation persistence to one canonical snapshot, rejects mismatched committed before/after state, preserves read compatibility with the older matching two-snapshot branch format, and adds focused store regression tests.

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

Neither implementation PR is merged because required exact-head evidence remains incomplete.

## 12. Implementation checklist
Implementation is complete at the frozen product heads. Staff canonical `main` was reconciled at `cb19463f16e124564ccbc17034b4c18f5cd0281f` before blocker publication. D04 PR #151, D05/Discord work, website work, and staging-control-plane PR #156 were preserved and not absorbed.

Pre-merge product parity is exact under the canonical aggregate-only metadata exclusion: standalone root tree `16447cb9ad2f41597d2eb616caa00164b2d130ae`; aggregate Commend tree `c454006f6d3d732d2a212afcc980520ff1c54ec0`; identical Git object IDs for all shared product entries, with only aggregate `COMPONENT-METADATA.md` extra.

## 13. Acceptance criteria
No reputation loss/duplication under retries, concurrent mutation, timeout, crash, or restoration; exact category/value snapshots and audit; missing/incompatible provider safe; both PRs merged normally and post-merge parity true.

## 14. Test requirements
Both repositories' suites plus exact mutation/category handling, concurrent updates, partial failure/rollback, restart/retry, stale restore, provider mismatch, authorization/audit, and bounded work.

Staff exact-head Coverage/full-validation run `32763957896`, job `97549217101`, passed on `9d44bbcac4d3cb9a489e9c9f755e80ae7ace28b1` with Temurin Java `21.0.12+8`, full build/tests including MariaDB/Testcontainers, 27 provider API source types / 0 leaks, Paper SHA-256 `7dd515e21409abb8c8496701e22ced3bdf3e266af8bc5c5bb0e7c52302c1198a`, Velocity SHA-256 `e4c7e48b51a8681eaac5742de96a841462aaeabd74507dcf1c8e1b02faef7586`, JaCoCo 50.50% line / 41.12% branch / 52.93% instruction, validation artifact `9534065111` digest `sha256:132df7318d872c0f6e9863bd71fa3f8c69ee72478de742ff1d4f792ebf4fbd2f`, and successful Codacy coverage upload/final notification.

Staff exact-head Sentinel artifact run `32763957749`, job `97549055756`, passed with artifact `9533760777`, digest `sha256:285503604af4a7d2bd0bde450acf594909490767fdfc433a66e74ae9fe2d6d16`. Exact restart request comment `5400262894`, durable job `231`, reached terminal `PAPER_RESTART_OK` on the same frozen Staff SHA.

Commend observable run `32763949487`, job `97549027434`, passed Java 21 Maven `clean verify`, 110 tests, PMD, and artifact `9533731303`, but its raw checkout was synthetic merge commit `cf6f64dcff0639a724b07ef9c6bebac78429c86d`, not exact head `30ac1af...`, so it is not admissible exact-head evidence.

## 15. Static-analysis requirements
All configured applicable checks/review bots in both repositories; zero valid unresolved findings.

Staff Codacy is **not passing** on frozen head `9d44bbc...`: the current PR summary reports 100 new issues (`8` high, `92` medium; ErrorProne, Complexity, and Performance). Coverage itself is within its configured target, but those static findings must be repaired or evidence-backed invalidated and followed by a clean exact-head static result before merge.

Review state: Staff PR #152 has zero live inline review threads; all six Commend PR #12 correctness/data-integrity threads are resolved; CodeRabbit status is successful on the frozen Staff head.

## 16. Documentation requirements
Contract/version, mutation/restoration semantics, permissions/recovery, provider setup/failure, component metadata, package handoff, cross-links, and canonical blocker state are documented. The durable blocker handoff is `ai-agents/reports/package-handoffs/2026-08-24-es-x04-commend-provider-blocked.md`.

## 17. Security and privacy requirements
Strict authorization/audit; fail closed on corrupt or unresolved moderation state; no production reputation/player rows, private data, credentials, deployment, or authority change in this package work.

## 18. Migration impact
No production migration is introduced or executed by X04. Migration history is not rewritten. D04's separate V20 work remains independent and unmerged.

## 19. Bedrock considerations
Staff controls retain identity correctness and text fallback requirements. Representative cross-platform acceptance remains in the later validation packages where originally assigned.

## 20. Distributed-runtime considerations
Implementation handles revision conflicts, duplicate/idempotent requests, restart recovery, provider disappearance/mismatch, reconnect recovery, and bounded periodic reconciliation. Representative destructive/load acceptance remains `ES-V03`.

## 21. External-provider considerations
The verified `wsg138/EnthusiaCommend` API and repository rules are authoritative. No reflection or invented provider behavior is used. Standalone repository validation is independent from Staff validation and remains incomplete for exact-head purposes.

## 22. Completion definition
Both exact-head PRs must satisfy their own repository rules and all applicable hosted/static/review/runtime gates; canonical Pi must be verified for the final Staff source; both PRs then merge normally; post-merge parity must be true; metadata/evidence recorded; temporary branches handled. Private representative destructive/load acceptance remains `ES-V03`.

## 23. Resume state
Resume the existing PRs; do not create replacement implementation branches or duplicate PRs. Current frozen heads are Commend `30ac1afbb6b45e958c6972330c42a870d619d530` and Staff `9d44bbcac4d3cb9a489e9c9f755e80ae7ace28b1`.

## 24. Last completed checkpoint
Product implementation, pre-merge provider synchronization, full Staff exact-head hosted validation, Staff exact-head Sentinel artifact build, and Staff exact-head Sentinel restart are complete. Staff Sentinel job `231` terminated `PAPER_RESTART_OK` on the frozen Staff head.

## 25. Remaining checklist
1. Make canonical Pi safely discoverable/executable through the trusted public control plane and verify exact Staff source, correlated private `Lincoln-PI-4` execution, runtime/restart/provenance/cleanup assertions, sanitized evidence, and public transfer cleanup.
2. Obtain a directly inspectable standalone exact-head Java 21 build/test/static result for Commend rather than a synthetic merge-ref-only result.
3. Resolve or evidence-back invalidate every applicable Codacy/static finding and require a clean exact-head static result.
4. Reconcile live `main`, both implementation heads, and all review threads; rerun any gate invalidated by source/workflow/test changes.
5. Merge Commend PR #12 and Staff PR #152 with normal merge commits only.
6. Verify resulting default-branch containment and post-merge standalone↔aggregate product parity, update component metadata, safely delete temporary branches, publish X04 `COMPLETE`, and stop.

## 26. Known blockers
- Standalone observable validation is merge-ref-only rather than admissible exact-head evidence.
- Staff Codacy reports 100 new static issues and is not up to standards.
- Canonical Pi exact-head public/private evidence is unavailable through the current trusted control plane while independent fix PR #156 remains open/unmerged at latest reconciliation head `e822df6ded598f74776858afab36a2768c7d7c95`.

Staff exact-head Coverage/full validation, Sentinel artifact build, and Sentinel restart pass and are no longer blockers. Missing, stale, queued, superseded, merge-ref-only, or different-SHA evidence is not relabeled as passing.

## 27. Final evidence
Canonical blocked handoff: `ai-agents/reports/package-handoffs/2026-08-24-es-x04-commend-provider-blocked.md`. Implementation PR descriptions record the same frozen heads and blockers. No production state or authority changed.

## 28. Merge and synchronization record
No implementation merge occurred. Pre-merge shared product bytes are synchronized under the aggregate metadata exclusion, but completion still requires normal merges and post-merge parity. Current classification is `PARKED_BLOCKED`.
