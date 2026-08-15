# `ES-X03` — EnthusiaMarket destructive provider

## 1. Package identity
`ES-X03`; External/multi-repository; primary `COMP-STAFF`; other `COMP-MARKET`; priority 120; conditional parallelism only without shared destructive-state overlap.

## 2. Status
`BLOCKED` / `PARKED_BLOCKED`. Implementation, standalone exact-head CI, hosted Staff validation, and review are stabilized on the existing paired PRs. The remaining blocker is owner-controlled runtime readiness: both required exact-head Staff runtime gates timed out starting Paper on a thermally/resource-constrained host. Registry remains the canonical status index.

## 3. Objective
Implement durable market restriction, reservation, confiscation, rollback, and exact restoration across EnthusiaStaff and EnthusiaMarket.

## 4. Included behavior
Supported versioned provider contract; listing/reservation ownership; durable snapshots/operation IDs; restriction/confiscation; idempotent rollback/restoration; retry/restart/race handling; provider missing/version mismatch; matching aggregate copy and parity.

## 5. Explicit exclusions
Production listings; whole-market rollback; currency/reputation work; unverified reflection against provider internals; representative destructive/load/process-kill acceptance assigned to `ES-V03`.

## 6. Dependencies
`ES-P08` and `ES-X02` are `COMPLETE`.

## 7. Repository and privacy boundaries
Use only existing Market PR #3 and Staff PR #139 on `package/es-x03-market-provider`. Market may use only ordinary public repository CI. No private Pi/staging runner config, labels, private bridge/dispatch implementation, staging secrets/topology/credentials, private artifact-transfer mechanism, or Sentinel infrastructure may enter Market or BadgersMC repositories. Preserve `preserve/es-x03-post-candidate-556b4b4-20260814` while it contains unique unrelated work.

## 8. Frozen implementation heads
- Market `main`: `bc24f1010642d6042307bc13a32fb33cc94e8883`.
- Market PR #3: `addb0f53d4aeac3549ab9b3ee8af3a6950db201f`.
- Staff `main`: `0d82b0840ae837d4d923a2407b6e8a4190e4e448`.
- Staff PR #139: `5b003225b305db76b47db7d75cf5b6a2943934df`.
- Neither implementation PR merged.

The branch history retains valid X03 remediation while broad unrelated Market cleanup remains preserved separately. No force-push, rebase, squash, or destructive reset was used.

## 9. Final implementation/review fixes
- Stale blacklist restoration remains operation/revision fenced and replay-safe.
- Bounded MariaDB concurrency waits remain in place.
- `restoreBlacklist` was decomposed without suppressing Detekt after exact-head `ThrowsCount` feedback.
- Market CI now consumes BadgersMC LumaGuilds `v2.1.24`, verifies asset SHA-256 `54ad645587f2ce895738eff3ee05123eb19e5687d80fa6d657aa3092031004c2`, compiles current RoseChat, bounds the download, and validates Market normally.
- Market build/detekt/security/Wiki workflows explicitly check out the PR head instead of GitHub's synthetic merge ref.
- Zero valid unresolved Market or Staff inline review threads remain. Market final CodeRabbit status is green. Staff's >500-file aggregate diff cannot receive a full CodeRabbit review and no approval is claimed for one that did not execute.

## 10. Current synchronization evidence
Standalone and aggregate use identical blobs for the final touched Market files:
- `MarketRestrictionJournal.kt`: `83758cff61c998b8d56907b706a8339bddc78721`.
- `.github/workflows/build.yml`: `563ed55bb6f4496f2392f7bd82656922b6338c0a`.
- `.github/workflows/wiki-checks.yml`: `424b57cad79bee95f07cbde4546baed2fdda6453`.

`COMPONENT-METADATA.md` records Market `addb0f53...`, `SYNC_PENDING`, and `PENDING_FINAL_CANONICAL_HASH`. The old normalized hash `8d27f4d9...` belongs only to obsolete candidate `6240869` and is not current evidence. Final canonical comparator execution remains a post-merge requirement.

## 11. Exact-head Market validation
Market `addb0f53d4aeac3549ab9b3ee8af3a6950db201f`:
- Wiki Checks `31852806668`: `success`.
- build `31852806638`: `success`.
- build job `94931681707`: actual PR-head checkout proven; Java 21; pinned dependency digest verified; current RoseChat `shadowJar` passed; Market `test shadowJar jacocoTestReport` passed.
- detekt `94932532843`: `success`.
- security `94932532864`: `success`.

Historical startup failures, pre-fix Detekt failure, upstream dependency failures, and merge-ref-only runs remain non-passing/superseded history.

## 12. Exact-head Staff hosted validation
Staff `5b003225b305db76b47db7d75cf5b6a2943934df`:
- Validate Wiki `31852845661`: `success`.
- Coverage/full build `31852845645`: `success`; Java 21.0.12; 49-task build; aggregate JaCoCo lines 49.45%, branches 39.99%, instructions 51.96%; Paper and Velocity runtime creation and provider-leak inspection passed.
- Sentinel Restart Artifact `31852845696`: `success`.
- zero live inline review threads.

## 13. Required runtime blocker
Canonical Pi staging `31852844656` reached the verified exact Staff artifact and executed Paper. The first server start did not reach the trusted readiness marker within the repository-configured 240-second readiness window. Sanitized evidence showed severe thermal/resource pressure and no ES-X03 stack trace or migration failure before the timeout. No storage-ready cycle completed.

The independent Sentinel restart for the same exact Staff SHA, job `174`, also ended `RESTART_CYCLE_1_PAPER_START_TIMEOUT` after passing its thermal prerequisite. This corroborates the current host-readiness problem.

Because a runner was allocated and Paper executed, the owner-approved zero-execution infrastructure exception does not apply. These failures are not passes and are not waived. Extending the trusted readiness timeout solely to make the package pass is prohibited.

## 14. Migration impact
Market V001–V024 remain immutable; ES-X03 owns V025 only. Staff V1–V18 remain immutable; ES-X03 owns V19 only. No Flyway repair or historical migration rewrite occurred.

## 15. Completion definition
Both paired PRs must merge normally only after every required gate is green. After both merges, run `tools/component-sync/component_sync.py`, require exact aggregate/standalone parity, record the canonical SHA-256 and resulting default heads, update component/package state to terminal, and clean only safely contained temporary branches. Representative destructive/load/process-kill acceptance remains `ES-V03`.

## 16. Resume / unblock condition
`BLOCKED` / `PARKED_BLOCKED`. Do not create replacement product PRs and do not start another ES-X03 branch.

Resume only after live evidence shows the owner-controlled validation host's cooling/runtime capacity materially improved. Rerun the exact frozen Staff runtime gates at the unchanged head. If either still fails, diagnose the new runtime evidence; do not relabel it. If both pass, recheck both PR heads/default heads/mergeability/review state and proceed through the paired normal-merge/parity/finalization sequence.

## 17. Production boundary
No production listing, balance, item, private player row, database, deployment, cutover, or authority state changed. LiteBans remains authoritative and issue #43 remains deferred.
