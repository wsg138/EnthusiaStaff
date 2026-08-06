# `ES-P02` — Runtime database recovery and Velocity reload

## 1. Package identity
`ES-P02`; Internal; primary `COMP-STAFF`; priority 20; not parallel-safe around lifecycle/configuration.

## 2. Status
`BLOCKED`. Product implementation, tests, documentation, review repair, Java 21 build/test/coverage, Codacy, runtime-JAR, and provider-leak validation passed for exact product head `b63fa1fa09ae4a9ea90988143ecda2cc7decbe14`. Merge is blocked because the required exact-head Pi staging workflow failed and ES-P02 has no package-specific owner-approved infrastructure exception.

## 3. Objective
Recover from transient Paper/Velocity database bootstrap failures without process restart and provide safe Velocity configuration reload.

## 4. Why the package exists
`AUD-RUNTIME-001/002`, configuration reload, health, latency, and legacy policy reload share lifecycle composition and must be fixed coherently.

## 5. Included audit IDs
`AUD-RUNTIME-001`, `AUD-RUNTIME-002`, `AUD-CONFIG-002`, `AUD-CONFIG-003`, `AUD-CONFIG-004`, `AUD-PERF-005`, relevant `AUD-ESC-005`.

## 6. Included behavior completed
- Bounded Paper and Velocity bootstrap retry/recovery.
- Cleanup-before-retry and deterministic shutdown.
- Atomic Velocity reload with validation and rollback.
- Explicit restart-required reporting for resource-bound settings.
- Sanitized health and operator feedback.
- Worker/scheduler rejection, retry, reconnect-cycle, repeated reload, invalid candidate, shutdown, and race tests.
- Automatic sequential package-selection documentation correction.

## 7. Explicit exclusions preserved
Production DB access; private data; Flyway repair/history rewrite; unrelated configuration redesign; provider invention; deployment; authority activation; issue #43; shadow period; production migration; cutover; rollback; ES-X05; any second package.

## 8. Dependencies
`ES-P01` is `COMPLETE`.

## 9. Component and repository boundaries
Root EnthusiaStaff runtime/configuration/tests/docs only. No external component import, permanent component branch, or isolated PR.

## 10. Required branch
`package/es-p02-runtime-db-recovery`; retained because PR #70 is unmerged and contains unique work.

## 11. Required PR
PR `#70` to `wsg138/EnthusiaStaff:main`; open, non-draft, and not merged.

## 12. Implementation checklist
- [x] Reconcile live GitHub, registry, routing, migration boundary, issue #43, and prior handoff.
- [x] Select ES-P02 through automatic sequential rules.
- [x] Create the documented package branch from exact `main` `d94d0219a598c9afb7e19c4ea9fddafd554d6469`.
- [x] Correct obsolete explicit-assignment orchestration documents.
- [x] Open PR #70 and maintain package records.
- [x] Implement bounded Paper bootstrap recovery and shutdown behavior.
- [x] Implement bounded Velocity bootstrap recovery and partial-resource cleanup.
- [x] Implement atomic Velocity reload, rollback, restart-required reporting, and authorization.
- [x] Add focused lifecycle, rejection, cleanup, retry, reload, and race tests.
- [x] Add operator and recovery documentation.
- [x] Harshly review the complete diff and repair confirmed findings.
- [x] Resolve all review threads; zero unresolved threads remain.
- [x] Pass exact-product-head Java 21 build, all tests, MariaDB/Testcontainers, migration integrity, coverage threshold, runtime-JAR, and provider-leak checks.
- [x] Pass exact-product-head Codacy with zero annotations.
- [ ] Obtain successful exact-head Pi build/boot/restart evidence, or obtain explicit ES-P02 owner approval for a valid infrastructure exception.
- [ ] Revalidate the unchanged product head after the blocker clears.
- [ ] Merge normally, verify containment/divergence, clean the branch, finalize records, update dependencies, and stop.

## 13. Acceptance criteria status
All code-level acceptance criteria are implemented and validated. Package completion remains blocked by the required Pi staging gate.

## 14. Tests added
Paper tests cover scheduler phase separation, transient recovery, initial worker rejection, exhaustion, shutdown before retry, cleanup-before-retry, retired entity callbacks, recovery failure, cleanup worker rejection, and retry payloads.

Velocity bootstrap tests cover transient recovery, exhaustion, permanent failure, shutdown, manual retry without overlap, worker rejection, and scheduler rejection. Reload tests cover atomic application, restart-required rejection, invalid candidate rollback, publication rollback, repeated reload, and shutdown races. Runtime-health tests cover immutable snapshots, atomic issue merges, and preservation of the current mode during issue updates.

## 15. Review findings and fixes
CodeRabbit identified three confirmed defects: Paper terminal-health overwrite, Velocity lost-update/stale-mode health publication, and overlapping Velocity bootstrap transitions. All were fixed. Manual harsh review also fixed a permanent-failure notification race and a remaining stale-mode issue-update race. Codacy findings were addressed by refactoring and narrow method-scoped suppressions with explanations. Current unresolved review-thread count is zero.

A fresh full CodeRabbit review of later heads could not be started because the review quota was exhausted; no stale review is represented as current full-bot proof. The current CodeRabbit check is successful, and manual full-diff review found no remaining confirmed product defect.

## 16. Documentation
`docs/runtime-database-recovery.md` documents bounded retries, cleanup, status, Velocity reload, restart-required settings, operator recovery, privacy/security, V16 immutability, and later validation boundaries.

## 17. Security and privacy
No secret, credential, production route, database row, private player data, raw address, or production environment was accessed or committed. Operator output is sanitized. Reload requires `enthusiastaff.reload`. Sensitive authority remains fail-closed; LiteBans remains authoritative.

## 18. Migration impact
No migration. V16 remains highest. V1–V16 remain byte-immutable. Flyway repair remains prohibited.

## 19. Bedrock considerations
No Floodgate identity or player-facing protocol contract changed. Java/Bedrock staging acceptance remains assigned to `ES-V02` and is not claimed here.

## 20. Distributed-runtime considerations
Each process owns one fenced bootstrap coordinator. Attempts and retries cannot overlap within a process. Publication, cleanup, shutdown, stale callbacks, and manual retry are fenced. Distributed staging acceptance is not claimed.

## 21. External-provider considerations
Provider and listener settings validate explicitly. Missing optional integrations degrade with component health. No provider API or repository was invented.

## 22. Exact product-head validation
Product head: `b63fa1fa09ae4a9ea90988143ecda2cc7decbe14`.

- Coverage workflow run `31072792371`, job `92524077883`: `SUCCESS`.
- The successful job verified expected SHA, used Java 21, ran tests and generated coverage, enforced changed-code coverage, built plugin/extension runtime JARs, verified packaged JARs and API/provider leaks, and dispatched follow-up Pi staging.
- Codacy Static Code Analysis: `SUCCESS`, zero issues/annotations.
- CodeRabbit check: `SUCCESS`.
- Review threads: zero unresolved.
- Snyk: skipped; not represented as passing evidence.
- Exact percentage and artifact hash were not surfaced by the connector; the configured threshold and artifact-integrity steps passed.

## 23. Blocking validation evidence
Exact-head Pi staging workflow run `31072790867`, job `92524036760`, completed `FAILURE` for `b63fa1fa09ae4a9ea90988143ecda2cc7decbe14`. No passing Pi build/boot/restart evidence exists for ES-P02. The connector could not retrieve sufficient external-run details to classify this exact run as a valid zero-execution exception, and no ES-P02 owner approval exists in any case. ES-P01's one-time exception does not apply.

## 24. Exact unblock condition
One of the following must occur:

1. allocate the specialized runner and obtain successful Pi build, safe boot, and restart evidence for the unchanged exact product head; or
2. record explicit owner approval for ES-P02 under `VALIDATION-POLICY.md`, with evidence that the exact failed staging run meets every zero-execution infrastructure-exception condition.

After either condition, rerun or verify every required exact-head gate and merge only if the validated head is unchanged.

## 25. Resume state
Resume PR #70 and branch `package/es-p02-runtime-db-recovery`. Do not modify product code without a newly confirmed defect. Reconcile the current branch documentation-only head against frozen product head `b63fa1fa09ae4a9ea90988143ecda2cc7decbe14`, resolve the staging blocker, then repeat applicable exact-head validation before merge.

## 26. Merge and cleanup record
No merge occurred. `main` remains `d94d0219a598c9afb7e19c4ea9fddafd554d6469`. Product-head containment in `main`, divergence cleanup, branch deletion, finalization PR, and dependency-derived READY updates are not applicable until merge. External parity is not applicable.

## 27. Handoff
[`2026-08-05-es-p02-runtime-db-recovery.md`](../../reports/package-handoffs/2026-08-05-es-p02-runtime-db-recovery.md)
