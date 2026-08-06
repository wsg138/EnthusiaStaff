# `ES-P02` — Runtime database recovery and Velocity reload

## 1. Package identity
`ES-P02`; Internal; primary `COMP-STAFF`; priority 20; not parallel-safe around lifecycle and configuration.

## 2. Status
`BLOCKED` and classified `PARKED_BLOCKED` while the recorded external runner or authorization condition remains unchanged. Product implementation and hosted source-repository validation are complete for frozen product head `b63fa1fa09ae4a9ea90988143ecda2cc7decbe14`, but required staging did not execute. PR #70 and its unique product work remain preserved.

## 3. Objective
Recover from transient Paper and Velocity database bootstrap failures without process restart and provide safe Velocity configuration reload.

## 4. Why the package exists
`AUD-RUNTIME-001/002`, configuration reload, health, latency, and legacy policy reload share lifecycle composition and must be fixed coherently.

## 5. Included audit IDs
`AUD-RUNTIME-001`, `AUD-RUNTIME-002`, `AUD-CONFIG-002`, `AUD-CONFIG-003`, `AUD-CONFIG-004`, `AUD-PERF-005`, relevant `AUD-ESC-005`.

## 6. Included behavior completed
- Bounded Paper and Velocity bootstrap retry and recovery.
- Cleanup-before-retry and deterministic shutdown.
- Atomic Velocity reload with validation and rollback.
- Explicit restart-required reporting for resource-bound settings.
- Sanitized health and operator feedback.
- Worker and scheduler rejection, retry, reconnect-cycle, repeated reload, invalid candidate, shutdown, and race tests.

## 7. Explicit exclusions preserved
Production database access; private data; Flyway repair or history rewrite; unrelated configuration redesign; provider invention; deployment; authority activation; issue #43; shadow period; production migration; cutover; rollback; ES-X05; any second implementation package.

## 8. Dependencies
`ES-P01` is `COMPLETE`.

## 9. Component and repository boundaries
Root EnthusiaStaff runtime, configuration, tests, and documentation only. No external component import, permanent component branch, or isolated PR.

## 10. Required branch
`package/es-p02-runtime-db-recovery`; retained because PR #70 is unmerged and contains unique work.

## 11. Required PR
PR `#70` to `wsg138/EnthusiaStaff:main`; open, non-draft, unmerged, and currently non-mergeable because `main` advanced.

## 12. Heads and current routing record
- Starting `main`: `d94d0219a598c9afb7e19c4ea9fddafd554d6469`.
- Canonical `main` at status-publication start: `5c969901146fc5081eec14b3c089bec7b06d5f5e`.
- Frozen ES-P02 product head: `b63fa1fa09ae4a9ea90988143ecda2cc7decbe14`.
- Current ES-P02 package-record and PR head: `80d4ea840f34017c09afb618f623581b31c6223d`.
- Current classification: `PARKED_BLOCKED` while runner availability and owner authorization remain unchanged.
- Active implementation package: `NONE`.
- Next eligible ready package outside this worker: `ES-X05`; it remains unstarted.

## 13. Implementation checklist
- [x] Reconcile live GitHub, registry, routing, migration boundary, issue #43, and prior handoff.
- [x] Create `package/es-p02-runtime-db-recovery` from exact `main` `d94d0219a598c9afb7e19c4ea9fddafd554d6469`.
- [x] Open PR #70 and maintain package records on the implementation branch.
- [x] Implement bounded Paper bootstrap recovery and shutdown behavior.
- [x] Implement bounded Velocity bootstrap recovery and partial-resource cleanup.
- [x] Implement atomic Velocity reload, rollback, restart-required reporting, and authorization.
- [x] Add focused lifecycle, rejection, cleanup, retry, reload, and race tests.
- [x] Add operator and recovery documentation.
- [x] Harshly review the complete product diff and repair confirmed findings.
- [x] Resolve all valid review threads; zero unresolved threads remain.
- [x] Pass frozen-product-head Java 21 build, all tests, MariaDB and Testcontainers, migration integrity, changed-code coverage threshold, runtime-JAR, and provider-leak checks.
- [x] Pass frozen-product-head Codacy with zero annotations and CodeRabbit.
- [x] Reconcile the required staging failure and one rerun.
- [x] Verify both ordinary `ubuntu-latest` build attempts had `runner_id: 0`, empty runner names, and zero steps, with both downstream Pi jobs skipped.
- [x] Prepare canonical blocked-state records for normal merge through the documentation-only status-publication process.
- [ ] After the external condition changes, obtain successful exact-head staging build plus Pi build, safe boot, and restart evidence, or a policy-valid explicit owner disposition that does not relabel the missing ordinary hosted build as passed.
- [ ] After that change, merge current `main` into the package branch through the approved normal merge-commit workflow and resolve conflicts without rebase or force-push.
- [ ] Freeze and revalidate the synchronized exact head.
- [ ] Merge normally, verify containment and divergence, clean the branch, finalize records, update dependencies, and stop.

## 14. Acceptance criteria status
All code-level acceptance criteria are implemented and validated for the frozen product head. Package completion remains blocked by the required staging gate and later current-main synchronization and exact-head revalidation.

## 15. Tests added
Paper tests cover scheduler phase separation, transient recovery, initial worker rejection, exhaustion, shutdown before retry, cleanup-before-retry, retired callbacks, recovery failure, cleanup rejection, and retry payloads.

Velocity tests cover transient recovery, exhaustion, permanent failure, shutdown, manual retry without overlap, worker and scheduler rejection, atomic reload, restart-required rejection, invalid candidates, publication rollback, repeated reload, shutdown races, immutable health snapshots, and atomic issue merges.

## 16. Review findings and fixes
CodeRabbit identified three confirmed defects: a Paper terminal-health overwrite, a Velocity lost-update and stale-mode health race, and overlapping Velocity bootstrap transitions. All were fixed. Manual review fixed two additional concurrency windows. Codacy findings were addressed. Current valid unresolved review-thread count is zero.

## 17. Documentation
`docs/runtime-database-recovery.md` on the preserved implementation branch documents bounded retries, cleanup, status, Velocity reload, restart-required settings, operator recovery, privacy and security, V16 immutability, and later validation boundaries.

## 18. Security and privacy
No secret, credential, production route, database row, private player data, raw address, or production environment was accessed or committed. Operator output is sanitized. Reload requires `enthusiastaff.reload`. Sensitive authority remains fail-closed; LiteBans remains authoritative.

## 19. Migration impact
No migration. V16 remains highest. V1–V16 remain byte-immutable. Flyway repair remains prohibited.

## 20. Bedrock considerations
No Floodgate identity or player-facing protocol contract changed. Java and Bedrock staging acceptance remains assigned to `ES-V02` and is not claimed here.

## 21. Distributed-runtime considerations
Each process owns one fenced bootstrap coordinator. Attempts and retries cannot overlap within a process. Publication, cleanup, shutdown, stale callbacks, and manual retry are fenced. Distributed staging acceptance is not claimed.

## 22. External-provider considerations
Provider and listener settings validate explicitly. Missing optional integrations degrade with component health. No provider API or repository was invented.

## 23. Successful hosted product validation
Frozen product head `b63fa1fa09ae4a9ea90988143ecda2cc7decbe14`:

- Coverage workflow run `31072792371`, job `92524077883`: `SUCCESS`.
- Expected SHA verification and Java 21 setup passed.
- Full tests, MariaDB and Testcontainers, migration integrity, and changed-code coverage threshold passed.
- Runtime JAR build, integrity, and API or provider-leak checks passed.
- Codacy Static Code Analysis passed with zero annotations.
- CodeRabbit check passed.
- Valid unresolved review threads: zero.
- Snyk was skipped and is not represented as passing evidence.
- The connector did not surface an exact numeric coverage percentage or artifact hash, so only the configured threshold and integrity gates are claimed.

## 24. Blocking staging evidence
Parent exact-head Pi staging workflow run `31072790867`, job `92524036760`, tested source SHA `b63fa1fa09ae4a9ea90988143ecda2cc7decbe14` and dispatched staging run `31072794096`.

Attempt 1:
- Ordinary hosted build job `92524048937`, label `ubuntu-latest`: `FAILURE` with `runner_id: 0`, empty runner name, and `steps: []`.
- Downstream Pi job `92524054852`: `SKIPPED`.

Attempt 2, from rerunning the failed build job once:
- Ordinary hosted build job `92541148296`, label `ubuntu-latest`: `FAILURE` with `runner_id: 0`, empty runner name, and `steps: []`.
- Downstream Pi job `92541160241`: `SKIPPED`.

No staging product build, Pi boot, or restart step executed in either attempt. This is infrastructure-unavailable evidence, not a product failure and not a pass. The unavailable job is an ordinary hosted build gate; no ES-P02 infrastructure exception exists, and no package-specific owner authorization exists.

Do not rerun this identical gate merely because a new worker opened. A manual rerun alone is not evidence that runner capacity, billing, authorization, configuration, or environment availability changed.

## 25. Exact unblock condition
All of the following are required before merge:

1. obtain a successful ordinary staging build and successful specialized-runner Pi build, safe boot, and restart evidence for an exact package head; or record an explicit ES-P02 owner disposition that is valid under every applicable `VALIDATION-POLICY.md` condition and does not relabel the missing ordinary hosted build as passed;
2. after the external condition changes, merge current `main` into the package branch through the approved normal merge-commit workflow, resolving divergence without rebase or force-push; and
3. freeze and rerun every required exact-head hosted, static-analysis, review, artifact, migration, and staging gate on the synchronized head, with zero valid unresolved review findings.

Branch drift and PR non-mergeability do not make ES-P02 actionable while the runner or authorization condition remains unchanged. Do not synchronize the branch merely to keep it current.

## 26. Resume state
While the exact unblock condition is unchanged, classify ES-P02 as `PARKED_BLOCKED`, leave PR #70 and `package/es-p02-runtime-db-recovery` untouched, do not rerun staging, and continue canonical selection to the eligible `READY` package.

When hosted runner availability or owner authorization demonstrably changes, classify ES-P02 as `ACTIONABLE_CONTINUATION` and resume PR #70 before starting another new package. Do not modify product code without a newly confirmed defect.

## 27. Merge and cleanup record
No ES-P02 merge occurred. PR #70 and its implementation branch remain open because they contain unique work. Product-head containment in `main`, safe implementation-branch deletion, and dependency-derived completion updates are not applicable until the package merges. External parity is not applicable.

## 28. Handoff
[`2026-08-05-es-p02-runtime-db-recovery.md`](../../reports/package-handoffs/2026-08-05-es-p02-runtime-db-recovery.md)
