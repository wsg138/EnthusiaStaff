# `ES-P02` — Runtime database recovery and Velocity reload

## 1. Package identity
`ES-P02`; Internal; primary `COMP-STAFF`; priority 20; not parallel-safe around lifecycle and configuration.

## 2. Status
`COMPLETE`.

Frozen exact implementation head `90f78f902a25039515d883ca96a1b72c2265418d` passed all required hosted, static-analysis, review, and canonical Pi staging gates and merged normally through PR #70 as `df9f4bf39ceda3911b7c084ac0c2caa188b82c7c`.

## 3. Objective
Recover from transient Paper and Velocity database bootstrap failures without process restart and provide safe Velocity configuration reload.

## 4. Why the package exists
`AUD-RUNTIME-001/002`, configuration reload, health, latency, and legacy policy reload share lifecycle composition and had to be fixed coherently.

## 5. Included audit IDs
`AUD-RUNTIME-001`, `AUD-RUNTIME-002`, `AUD-CONFIG-002`, `AUD-CONFIG-003`, `AUD-CONFIG-004`, `AUD-PERF-005`, relevant `AUD-ESC-005`.

## 6. Included behavior completed
- Bounded Paper and Velocity bootstrap retry and recovery.
- Cleanup-before-retry and stale-attempt fencing.
- Deterministic shutdown and safe worker/scheduler rejection behavior.
- Atomic Velocity reload with validation and rollback.
- Explicit restart-required reporting for resource-bound settings.
- Sanitized atomic health and operator feedback.
- Worker and scheduler rejection, retry, reconnect-cycle, repeated reload, invalid candidate, shutdown, and race tests.
- Operator recovery documentation.

## 7. Explicit exclusions preserved
Production database access; private production data; Flyway repair or history rewrite; unrelated configuration redesign; provider invention; deployment; authority activation; issue #43 acceptance; production shadow period; production migration; cutover; rollback; ES-X05; and every other implementation package.

## 8. Dependencies
`ES-P01` is `COMPLETE`.

## 9. Component and repository boundaries
Root EnthusiaStaff runtime, configuration, tests, and documentation only. No external component import, permanent component branch, or isolated component PR.

## 10. Branch and PR record
- Implementation branch: `package/es-p02-runtime-db-recovery`.
- Implementation PR: #70 to `wsg138/EnthusiaStaff:main`.
- Implementation branch is deleted after verified containment and returns 404.
- Documentation-only finalization branch/PR records terminal state; it contains no product code, tests, migrations, workflows, or runtime configuration.

## 11. Heads
- Starting historical package `main`: `d94d0219a598c9afb7e19c4ea9fddafd554d6469`.
- Current-main synchronization base for the resumed continuation: `d036908a90b8c0c9ee64d08366d6e8e4b60841e0`.
- Frozen exact implementation head: `90f78f902a25039515d883ca96a1b72c2265418d`.
- Normal implementation merge commit: `df9f4bf39ceda3911b7c084ac0c2caa188b82c7c`.

## 12. Implementation checklist
- [x] Reconcile live GitHub, registry, routing, migration boundary, issue #43, prior handoff, branch, PR, reviews, and checks.
- [x] Reclassify the package as `ACTIONABLE_CONTINUATION` after ES-R01 materially changed the former staging blocker.
- [x] Preserve and normally synchronize the existing package branch with current `main` without rebase or force-push.
- [x] Implement bounded Paper bootstrap recovery and shutdown behavior.
- [x] Implement bounded Velocity bootstrap recovery and partial-resource cleanup.
- [x] Implement atomic Velocity reload, rollback, restart-required reporting, and authorization.
- [x] Add focused lifecycle, rejection, cleanup, retry, reload, and race tests.
- [x] Add operator and recovery documentation.
- [x] Harshly review the complete final diff and repair valid findings.
- [x] Resolve all valid review threads; zero unresolved substantive threads remain.
- [x] Eliminate all valid Codacy static-analysis findings without weakening analyzer scope.
- [x] Freeze exact head `90f78f902a25039515d883ca96a1b72c2265418d`.
- [x] Pass Java 21 build/tests, MariaDB/Testcontainers, migration integrity, runtime-JAR/provider-leak checks, aggregate coverage, and artifact publication on the frozen head.
- [x] Pass Codacy static and coverage checks on the frozen head.
- [x] Pass canonical public→private Pi staging on the frozen head, including exact provenance, two Paper cycles, restart/persistence, cleanup, evidence upload, and public transfer cleanup.
- [x] Merge PR #70 normally.
- [x] Verify containment/divergence and safe implementation-branch deletion.
- [x] Preserve production, LiteBans, issue #43, migration-history, and private-data exclusions.
- [x] Publish terminal package state and dependency-derived routing without activating another package.

## 13. Acceptance criteria
All ES-P02 acceptance criteria are satisfied. Implementation, review, exact-head hosted validation, static analysis, canonical staging, merge, containment, cleanup, and terminal-state publication are complete.

## 14. Tests
Paper coverage includes scheduler phase separation, transient recovery, initial worker rejection, exhaustion, shutdown before retry, cleanup-before-retry, retired callbacks, recovery failure, cleanup rejection, and retry payloads.

Velocity coverage includes transient recovery, exhaustion, permanent failure, shutdown, manual retry without overlap, worker and scheduler rejection, atomic reload, restart-required rejection, invalid candidates, publication rollback, repeated reload, shutdown races, immutable health snapshots, and atomic issue merges.

## 15. Review findings and disposition
CodeRabbit identified three substantive historical defects: a Paper terminal-health overwrite, a Velocity lost-update/stale-mode health race, and overlapping Velocity bootstrap transitions. They were fixed. Manual review also repaired additional concurrency/static-analysis concerns during the continuation.

At merge:
- all three substantive review threads were resolved;
- valid unresolved review-thread count was zero;
- Codacy Static Code Analysis check `93313758400` was `SUCCESS` with zero new issues/annotations.

The latest automatic CodeRabbit attempt was rate-limited and is not represented as a fresh independent pass; it produced no new code finding.

## 16. Exact-head hosted validation
Coverage run `31342778279`, job `93319183473`, tested exact head `90f78f902a25039515d883ca96a1b72c2265418d` and completed `SUCCESS`.

It completed Java 21, the configured full repository build/tests, MariaDB/Testcontainers and migration checks, runtime-JAR inspection, aggregate JaCoCo generation/enforcement, validation artifact upload, and Codacy coverage upload.

`java-21-validation` artifact:
- ID `9046404003`;
- digest `sha256:fc61861c9e1a49270d8686350f791f4ad5d63ef1c4e94f5e81aad89ab6e4f598`.

Fresh Codacy coverage checks:
- Diff Coverage `93319857969`: `SUCCESS`, 47.12%, no configured gate;
- Coverage Variation `93319858087`: `SUCCESS`, +0.13% against the -1.0% target.

## 17. Canonical Pi staging
Public Pi Staging run `31342778432` tested frozen head `90f78f902a25039515d883ca96a1b72c2265418d`.

- public trusted Paper build job `93319183919`: `SUCCESS`;
- public bridge job `93319918461`: `SUCCESS`;
- correlated private run `31343077935`;
- private job `93319937672`: `SUCCESS` on trusted `Lincoln-PI-4`;
- exact artifact/provenance verification: PASS;
- guarded disposable database preparation: PASS;
- Paper cycle 1/storage readiness/Flyway V1–V18: PASS;
- first clean shutdown/reap: PASS;
- Paper cycle 2/restart-persistence: PASS;
- second clean shutdown/reap: PASS;
- final guarded database cleanup: PASS;
- sanitized evidence upload: PASS;
- public transient-transfer cleanup: PASS.

Sanitized evidence recorded `server_starts_completed=2`, `storage_ready_cycles_completed=2`, and `failure_count=0`.

Private evidence artifact:
- ID `9046774374`;
- digest `sha256:d51574d879a0c3271947d9d1422bc27b24d006703a0e659b7132c0228c4d1ac6`.

Runtime Paper JAR SHA-256: `f6e7b3362b8c284a7759c72620f98f5e64db6720e23aabeb115f7ef205c4836b`.

Earlier same-source attempts remain failure evidence, not passing evidence: private run `31340883461` hit a shared-host SIGKILL before readiness, and private run `31341552172` correctly rejected a rerun-attempt manifest mismatch before Paper executed. The fresh run-attempt-1 route above is the terminal canonical pass.

No private workflow was directly dispatched and no unrelated Sentinel/Pi job was cancelled or preempted.

## 18. Documentation
`docs/runtime-database-recovery.md` documents bounded retries, cleanup, status, Velocity reload, restart-required settings, operator recovery, privacy/security, and the V18 migration boundary.

## 19. Security and privacy
No secret, credential, production route, production database row, private player data, raw address, or production environment was accessed or committed. Operator output is sanitized. Reload requires `enthusiastaff.reload`. Sensitive authority remains fail-closed; LiteBans remains authoritative.

## 20. Migration impact
No migration. `V18` remains the highest migration and V1–V18 remain byte-immutable. Flyway repair remains prohibited.

## 21. Bedrock considerations
No Floodgate identity or player-facing protocol contract changed. Broader representative Java/Bedrock distributed acceptance remains assigned to ES-V02 and is not claimed by ES-P02.

## 22. Distributed-runtime considerations
Each process owns one fenced bootstrap coordinator. Attempts and retries cannot overlap within a process. Publication, cleanup, shutdown, stale callbacks, and manual retry are fenced. Broader distributed acceptance remains assigned to ES-V02.

## 23. External-provider considerations
Provider and listener settings validate explicitly. Missing optional integrations degrade with component health. No provider API or repository was invented.

## 24. Merge and cleanup record
PR #70 merged normally as `df9f4bf39ceda3911b7c084ac0c2caa188b82c7c` with parents pre-merge `main` `d036908a90b8c0c9ee64d08366d6e8e4b60841e0` and frozen head `90f78f902a25039515d883ca96a1b72c2265418d`.

The merge commit tree equals the frozen implementation-head tree. Compare shows the merge commit one commit ahead and zero behind the frozen head with no merge-only product diff. `package/es-p02-runtime-db-recovery` is deleted and returns 404. External parity is not applicable.

## 25. Production boundary
Issue #43 remains open/deferred. LiteBans remains authoritative. No production deployment, production-data access, shadow window, authority activation, migration, or cutover occurred.

## 26. Dependency-derived routing
ES-P02 completion makes `ES-P07 — Inventory and Ender editing runtime completion` dependency-complete and `READY`.

`ES-P05 — Report evidence and staff workflow completion` remains an existing `ACTIONABLE_CONTINUATION`. Canonical selection gives actionable continuation work priority over a new READY package, so ES-P05 is the exact next normal sequential package after this finalization. It is not activated or modified here.

## 27. Handoff
[`2026-08-09-es-p02-runtime-db-recovery-complete.md`](../../reports/package-handoffs/2026-08-09-es-p02-runtime-db-recovery-complete.md)
