# `ES-P02` — Runtime database recovery and Velocity reload

## 1. Package identity
`ES-P02`; Internal; primary `COMP-STAFF`; priority 20; not parallel-safe around lifecycle/configuration.

## 2. Status
`REVIEW`. Implementation, focused tests, documentation, and review repair are complete on the package branch. Exact-head hosted validation, merge, containment, cleanup, and post-merge finalization remain.

## 3. Objective
Recover from transient Paper/Velocity database bootstrap failures without process restart and provide safe Velocity configuration reload.

## 4. Why the package exists
`AUD-RUNTIME-001/002`, configuration reload, health, latency, and legacy policy reload share lifecycle composition and must be fixed coherently.

## 5. Included audit IDs
`AUD-RUNTIME-001`, `AUD-RUNTIME-002`, `AUD-CONFIG-002`, `AUD-CONFIG-003`, `AUD-CONFIG-004`, `AUD-PERF-005`, relevant `AUD-ESC-005`.

## 6. Included behavior
Bounded bootstrap retry/recovery; atomic Velocity reload; candidate validation/rollback; restart-required reporting; health/operator feedback; reconnect, repeated reload, failed candidate, shutdown, and race tests.

## 7. Explicit exclusions
Production DB access; Flyway repair/history rewrite; unrelated configuration redesign; issue #43; deployment; authority activation; shadow period; cutover; other packages.

## 8. Dependencies
`ES-P01` is `COMPLETE`.

## 9. Component and repository boundaries
Root EnthusiaStaff runtime/configuration/tests/docs only. No external component import, permanent component branch, or isolated PR.

## 10. Required branches
Temporary `package/es-p02-runtime-db-recovery`; delete after verified merge containment.

## 11. Required PRs
PR `#70` to `wsg138/EnthusiaStaff:main`.

## 12. Implementation checklist
- [x] Reconcile live heads, PRs, branches, registry, routing, migration boundary, issue #43, and prior handoff.
- [x] Confirm no unfinished package work exists and select ES-P02 through automatic sequential rules.
- [x] Create `package/es-p02-runtime-db-recovery` from exact `main` `d94d0219a598c9afb7e19c4ea9fddafd554d6469`.
- [x] Correct obsolete explicit-assignment orchestration documents on the selected package branch.
- [x] Record package claim, workspace routing, handoff, scope, exclusions, and exact next action.
- [x] Open draft PR #70 and move it to review after the first complete implementation checkpoint.
- [x] Reproduce the Paper one-shot bootstrap failure from current source and existing tests.
- [x] Implement bounded Paper bootstrap retry/recovery and deterministic shutdown behavior.
- [x] Reproduce the Velocity one-shot bootstrap failure and missing reload path.
- [x] Implement bounded Velocity bootstrap recovery with partial-resource cleanup and deterministic shutdown.
- [x] Implement atomic Velocity configuration candidate validation, reloadable-field publication, rollback, and restart-required reporting.
- [x] Add the `enthusiastaff.reload` operator command boundary, sanitized status, retry, health, and restart-required feedback.
- [x] Add focused lifecycle, worker/scheduler rejection, cleanup, reconnect-cycle, repeated reload, failed candidate, shutdown, and race tests.
- [x] Document recovery, reload, operator procedure, security boundary, and unchanged V16 migration boundary.
- [x] Harshly review the complete diff and repair every confirmed CodeRabbit finding.
- [x] Resolve all three valid review threads; current thread count is zero unresolved.
- [ ] Freeze the final tracked head after canonical REVIEW records are complete.
- [ ] Run every applicable exact-head hosted validation gate and inspect jobs/logs.
- [ ] Confirm current-head CodeRabbit/Codacy/static-analysis results and zero valid unresolved findings.
- [ ] Merge normally, verify containment/divergence, clean the temporary branch, finalize package records, update dependencies, and stop.

## 13. Acceptance criteria
Transient startup failure is recoverable without restart; reload is atomic and rejects invalid candidates without corrupting live state; bounded retries and shutdown are deterministic; health distinguishes degraded, retrying, exhausted, healthy, and restart-required states.

## 14. Tests added
Paper coordinator tests cover thread-phase separation, transient recovery, initial worker rejection, retry exhaustion, shutdown before retry, cleanup-before-retry, retired entity callbacks, recovery failure, cleanup worker rejection, and retry callback payloads.

Velocity coordinator tests cover transient recovery, exhaustion, permanent failure, shutdown, immediate retry without overlap, worker rejection, scheduler rejection, and retry-state publication. Reload tests cover atomic application, restart-required rejection, invalid candidate rollback, publication rollback, repeated reload, shutdown before load, and shutdown after candidate load. Health tests cover immutable snapshots and atomic issue merges.

No final passing test claim is made until exact-head hosted runs finish. An earlier superseded Coverage run executed Paper, persistence, protocol, and integration tests successfully before exposing the now-fixed Velocity warnings-as-errors `serialVersionUID` defect.

## 15. Static-analysis requirements
Java 21 warnings-as-errors, repository static analysis, CodeRabbit/Codacy where available, zero valid unresolved findings.

## 16. Documentation
`docs/runtime-database-recovery.md` documents bounded retry, partial cleanup, operator status, Velocity reload, restart-required settings, recovery procedure, security boundaries, V16 immutability, and later validation boundaries.

## 17. Security and privacy
No secrets, database rows, private player data, raw addresses, or production routes were accessed or committed. Operator output is sanitized. Reload requires `enthusiastaff.reload`. Sensitive authority remains fail-closed and LiteBans remains authoritative.

## 18. Migration impact
No migration. V16 remains highest. V1–V16 remain byte-immutable; Flyway repair remains prohibited.

## 19. Bedrock considerations
Reload/recovery does not change Floodgate identity or player-facing component contracts. Runtime Java/Bedrock acceptance remains assigned to `ES-V02`.

## 20. Distributed-runtime considerations
Each Paper or Velocity process owns one bounded bootstrap coordinator. Attempts and retries cannot overlap within a process; resource publication, cleanup, shutdown, stale callbacks, and manual retry are fenced. This package does not claim distributed staging acceptance.

## 21. External-provider considerations
Provider and listener settings validate explicitly. Missing or unavailable optional integrations degrade with component health; no provider API or repository was invented.

## 22. Completion definition
All package scope and exact-head gates pass; zero valid review threads; PR #70 merges normally; feature-head containment, divergence, branch cleanup, final records, and dependency-derived statuses are verified.

## 23. Resume state
Branch `package/es-p02-runtime-db-recovery` starts at `d94d0219a598c9afb7e19c4ea9fddafd554d6469`. Implementation and review repair are complete. The branch head immediately before this REVIEW record was `decb40702820333726f4dfa787af73a5ddb370c9`; this record itself advances the head and must be followed by one final canonical-state checkpoint before freezing validation.

## 24. Review findings and fixes
CodeRabbit identified three valid defects: Paper could overwrite terminal degraded health with a retry message when scheduling failed; Velocity health issue updates could lose concurrent changes and republish a stale mode; Velocity bootstrap transitions could permit an immediate manual retry to overlap automatic retry or terminal publication. All three were fixed. Eleven lower-severity test and maintainability findings were reviewed; all behaviorally relevant items were implemented, including worker/scheduler rejection tests, retry payload assertions, harness extraction, dead-state removal, simpler recovery steps, reload shutdown-race coverage, and backoff simplification. The positional Velocity test fixture remains test-only and is subject to current-head static analysis.

## 25. Remaining checklist
Complete canonical REVIEW updates; freeze the resulting exact branch head; make PR #70 reflect that head; rerun and inspect all applicable hosted workflows and review bots; merge normally if every gate passes; verify resulting `main`, containment, divergence, and cleanup; merge a documentation-only finalization PR if needed; mark ES-P02 complete; move dependency-cleared packages to READY without starting them.

## 26. Known blockers
No product blocker is known. The local shell cannot resolve GitHub, so no local build is claimed. Repository work and hosted validation use the authenticated GitHub connector and GitHub Actions. No ES-P02 infrastructure exception is approved.

## 27. Current evidence
Starting `main`: `d94d0219a598c9afb7e19c4ea9fddafd554d6469`. PR: `#70`. Highest migration: V16. Issue #43 remains open and excluded. CodeRabbit's three review threads are resolved. Earlier workflow evidence is superseded and not final. Final reviewed head, run/job IDs, complete test results, static analysis, coverage, runtime artifact checks, staging disposition, and merge facts remain unset until the tracked head freezes.

## 28. Merge and synchronization record
Unset. ES-P02 is internal; external parity is not applicable. Record the frozen feature head, normal merge commit, resulting `main`, containment, divergence, branch deletion, and finalization only after those events occur.

## 29. Handoff
[`2026-08-05-es-p02-runtime-db-recovery.md`](../../reports/package-handoffs/2026-08-05-es-p02-runtime-db-recovery.md)
