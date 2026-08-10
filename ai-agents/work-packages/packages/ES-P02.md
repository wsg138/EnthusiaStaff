# `ES-P02` — Runtime database recovery and Velocity reload

## 1. Package identity
`ES-P02`; Internal; primary `COMP-STAFF`; priority 20; not parallel-safe around lifecycle and configuration.

## 2. Status
`COMPLETE`.

Implementation PR #70 merged normally into `main` as `df9f4bf39ceda3911b7c084ac0c2caa188b82c7c` from frozen, reviewed, and exactly validated head `90f78f902a25039515d883ca96a1b72c2265418d`.

## 3. Objective completed
Paper and Velocity now recover from bounded transient database-bootstrap failures without requiring a process restart, and Velocity configuration reload is atomic, validated, rollback-safe, authorized, and explicit about restart-required settings.

## 4. Included behavior
- Bounded Paper and Velocity database bootstrap retry/recovery.
- Cleanup-before-retry and deterministic shutdown fencing.
- Executor/scheduler rejection handling without overlapping bootstrap attempts.
- Atomic Velocity reload with validation, rollback, restart-required reporting, and authorization.
- Sanitized runtime health and operator feedback.
- Focused lifecycle, race, retry, reconnect, repeated-reload, invalid-candidate, and shutdown tests.
- Operator documentation in `docs/runtime-database-recovery.md`.

## 5. Scope boundaries preserved
No production database access, private data access, Flyway repair/history rewrite, unrelated configuration redesign, deployment, authority activation, issue #43 acceptance, shadow-period acceptance, production migration, cutover, rollback, or second implementation package occurred. LiteBans remains authoritative.

## 6. Dependency and routing state
`ES-P01` is complete. ES-P02 completion makes `ES-P07` dependency-complete and therefore `READY`, but the next sequential worker must first resume existing `ES-P05` work as the higher-priority `ACTIONABLE_CONTINUATION`. This ES-P02 worker does not activate or modify either package.

## 7. Branch and merge record
- Implementation branch: `package/es-p02-runtime-db-recovery`.
- Required PR: #70.
- Synchronized base before final validation: `d036908a90b8c0c9ee64d08366d6e8e4b60841e0`.
- Frozen implementation head: `90f78f902a25039515d883ca96a1b72c2265418d`.
- Normal merge commit: `df9f4bf39ceda3911b7c084ac0c2caa188b82c7c`.
- Merge commit parents are the synchronized `main` head and exact frozen implementation head.
- Post-merge compare from `90f78f...` to `df9f4bf...` reports `status=ahead`, `ahead_by=1`, `behind_by=0`, proving the feature head is contained in `main` with no unique implementation commit left behind.
- No open pull request depends on the implementation branch.

## 8. Final review and static analysis
- All three substantive CodeRabbit review threads are resolved; valid unresolved thread count is zero.
- Confirmed review defects repaired during the package included Paper terminal-health overwrite, Velocity health lost-update/stale-mode races, overlapping Velocity bootstrap transitions, and additional manually found lifecycle/concurrency windows.
- Codacy Static Code Analysis check `93313758400` on exact head `90f78f...` completed `SUCCESS` with zero new issues/annotations.
- The final source commit added only a method-local PMD suppression and rationale for a parameterized SLF4J enum log; no runtime behavior changed.
- A later automatic CodeRabbit attempt was rate-limited and produced no new code finding; it is not represented as an independent passing review.

## 9. Fresh exact-head hosted validation
Coverage workflow run `31342778279`, job `93319183473`, tested exact head `90f78f902a25039515d883ca96a1b72c2265418d` and completed `SUCCESS`.

It passed Java 21 setup, full repository build/tests, MariaDB/Testcontainers and migration checks, runtime-JAR creation/inspection, aggregate JaCoCo generation/enforcement, validation artifact upload, and Codacy coverage upload.

Validation artifact:
- name: `java-21-validation`
- artifact ID: `9046404003`
- digest: `sha256:fc61861c9e1a49270d8686350f791f4ad5d63ef1c4e94f5e81aad89ab6e4f598`

## 10. Canonical Pi acceptance
Fresh public Pi Staging run `31342778432` tested the same exact source head through the canonical public-to-private bridge.

Public side:
- trusted Paper build job `93319183919`: `SUCCESS`;
- bridge job `93319918461`: `SUCCESS`;
- token validation, exact public artifact download, bounded transient transfer publication, private dispatch/correlation, private verdict, transient transfer removal, and final staging result all passed.

Correlated private side:
- repository: `wsg138/EnthusiaStaff-Staging`;
- run `31343077935`;
- job `93319937672`;
- trusted runner `Lincoln-PI-4`, runner ID `2`;
- exact artifact/provenance verification: `SUCCESS`;
- guarded disposable Paper boot/restart test: `SUCCESS`;
- sanitized evidence upload: `SUCCESS`.

Sanitized evidence artifact:
- artifact ID `9046774374`;
- digest `sha256:d51574d879a0c3271947d9d1422bc27b24d006703a0e659b7132c0228c4d1ac6`;
- runtime JAR SHA-256 `f6e7b3362b8c284a7759c72620f98f5e64db6720e23aabeb115f7ef205c4836b`.

Acceptance summary:
- `result=PASS`;
- Paper `1.21.11` build `132`;
- two server starts completed;
- two storage-ready cycles completed;
- first cycle applied/verified Flyway V1–V18;
- second cycle verified schema version 18 with no migration required;
- both cycles entered `SHADOW_MIGRATION` as required;
- first storage bootstrap completed in 17 seconds and second in 8 seconds;
- both command-mode validations passed;
- both shutdowns exited cleanly with code 0 and completed save markers;
- guarded pre-reset passed;
- guarded final reset passed and removed 69 disposable objects;
- both critical failure scans were empty;
- unrelated helper services were preserved;
- public transfer cleanup passed.

Transient transfer identity for the successful run:
- release ID `367616174`;
- tag `es-r01-staging-31342778432-1`;
- asset ID `508040821`;
- asset `enthusiastaff-staging-90f78f902a25-31342778432-1.zip`;
- transfer SHA-256 `2620fbb2dc6934112ba3b34b4ad7f59b3ee67068fea4eb98162bef129d36c550`.

## 11. Superseded failure evidence
Two earlier exact-head attempts are retained as failure evidence and are not represented as passes:

1. Private run `31340883461`, job `93314336115` verified the exact artifact and executed Paper, but the first JVM was killed by signal 9 under severe shared-host memory pressure before readiness. Sanitized evidence contained no EnthusiaStaff/Flyway failure and ended at Hikari startup. This was an executed environmental failure, not an infrastructure exception.
2. A rerun of the same public workflow run created `run_attempt=2` while `download-artifact` reused the attempt-1 manifest. Private run `31341552172`, job `93316070062`, correctly rejected the transfer because the manifest build attempt did not match; Paper was skipped. A fresh PR `reopened` event was used instead of weakening provenance or directly dispatching private staging.

A concurrent SEN-P02 worker later repaired/reaped stale profile-fixture runtimes and completed its own shared-runner validation. This ES-P02 worker did not cancel, preempt, merge, or otherwise interfere with unrelated Sentinel work.

## 12. Migration impact
ES-P02 adds no migration. V18 is the current highest migration and V1–V18 remain immutable. Flyway repair remains prohibited.

## 13. Security and privacy
No credential, private database row, private message, raw address, production route, or reconstructable private evidence was committed. Operator output is sanitized. Reload requires `enthusiastaff.reload`.

## 14. Acceptance criteria
All ES-P02 implementation, review, hosted-validation, canonical staging, restart/persistence, cleanup, containment, and merge criteria are satisfied. Representative distributed Java/Bedrock acceptance remains assigned to later validation packages where already documented; it is not claimed by ES-P02.

## 15. Completion checklist
- [x] Reconciled live GitHub and current package routing.
- [x] Preserved and synchronized the existing implementation branch without rebase/force-push.
- [x] Completed Paper/Velocity recovery and Velocity reload behavior.
- [x] Added focused lifecycle/concurrency tests and operator documentation.
- [x] Resolved all valid review/static-analysis findings.
- [x] Froze exact head `90f78f...`.
- [x] Passed fresh exact-head hosted validation and artifact gates.
- [x] Passed fresh exact-head canonical public→private Pi staging.
- [x] Proved two storage-ready Paper cycles, Flyway V18 persistence, clean shutdown/reap, and guarded cleanup.
- [x] Merged PR #70 normally as `df9f4bf...`.
- [x] Verified exact feature-head containment and no open dependent PR.
- [ ] Delete `package/es-p02-runtime-db-recovery` when the available GitHub control surface permits safe ref deletion.
- [x] Publish this documentation-only terminal state through the ES-P02 completion-record PR.

The implementation branch is safe to delete because it is fully contained and has no open dependent PR. If branch-ref deletion is unavailable to the current connector, that tool limitation is cleanup-only and does not change package completion or product evidence.

## 16. Handoff
[`2026-08-09-es-p02-runtime-database-recovery-complete.md`](../../reports/package-handoffs/2026-08-09-es-p02-runtime-database-recovery-complete.md)
