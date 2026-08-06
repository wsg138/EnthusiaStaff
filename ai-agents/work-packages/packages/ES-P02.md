# `ES-P02` — Runtime database recovery and Velocity reload

## 1. Package identity
`ES-P02`; Internal; primary `COMP-STAFF`; priority 20; not parallel-safe around lifecycle/configuration.

## 2. Status
`ACTIVE`. Registry is authoritative.

## 3. Objective
Recover from transient Paper/Velocity database bootstrap failures without process restart and provide safe Velocity configuration reload.

## 4. Why the package exists
`AUD-RUNTIME-001/002`, configuration reload, health, latency, and legacy policy reload share lifecycle composition and must be fixed coherently.

## 5. Included audit IDs
`AUD-RUNTIME-001`, `AUD-RUNTIME-002`, `AUD-CONFIG-002`, `AUD-CONFIG-003`, `AUD-CONFIG-004`, `AUD-PERF-005`, relevant `AUD-ESC-005`.

## 6. Included behavior
Bounded bootstrap retry/recovery; atomic Velocity reload; candidate validation/rollback; restart-required reporting; health/operator feedback; reconnect, repeated reload, failed candidate, shutdown, and race tests.

## 7. Explicit exclusions
Production DB access; Flyway repair/history rewrite; unrelated configuration redesign; issue #43.

## 8. Dependencies
`ES-P01` is `COMPLETE`.

## 9. Component and repository boundaries
Root EnthusiaStaff runtime/configuration/tests/docs only. No external component import, permanent component branch, or isolated PR.

## 10. Required branches
Temporary `package/es-p02-runtime-db-recovery`; delete after verified merge containment.

## 11. Required PRs
One PR to `wsg138/EnthusiaStaff:main`.

## 12. Implementation checklist
- [x] Reconcile live heads, PRs, branches, registry, routing, migration boundary, issue #43, and prior handoff.
- [x] Confirm no unfinished package work exists and select ES-P02 through automatic sequential rules.
- [x] Create `package/es-p02-runtime-db-recovery` from exact `main` `d94d0219a598c9afb7e19c4ea9fddafd554d6469`.
- [x] Correct obsolete explicit-assignment orchestration documents on the selected package branch.
- [x] Record package claim, workspace routing, handoff, scope, exclusions, and exact next action.
- [ ] Open the early draft PR and keep its description current.
- [ ] Reproduce and test Paper one-shot bootstrap failure.
- [ ] Implement bounded Paper bootstrap retry/recovery and deterministic shutdown behavior.
- [ ] Reproduce and test Velocity one-shot bootstrap failure and missing reload path.
- [ ] Implement bounded Velocity bootstrap recovery with partial-resource cleanup and deterministic shutdown.
- [ ] Implement atomic Velocity configuration candidate validation, reloadable-field publication, rollback, and restart-required reporting.
- [ ] Add operator command, permission, health, status, verify, and reload feedback.
- [ ] Add focused lifecycle/configuration/reconnect/repeated-reload/failed-candidate/shutdown/race tests and applicable MariaDB/Testcontainers proof.
- [ ] Update configuration, recovery, operator, Wiki, and package documentation conservatively.
- [ ] Harshly review the complete diff; fix every valid finding and resolve all review threads.
- [ ] Freeze tracked content and run all exact-head validation gates.
- [ ] Merge normally, verify containment/divergence, clean the temporary branch, finalize package records, update dependencies, and stop.

## 13. Acceptance criteria
Transient startup failure is recoverable without restart; reload is atomic and rejects invalid candidates without corrupting live state; bounded retries and shutdown are deterministic; health distinguishes degraded/retrying/restart-required states.

## 14. Test requirements
Focused Paper/Velocity lifecycle and configuration tests, MariaDB/Testcontainers failure/reconnect tests, repeated reload and shutdown races, plus full applicable Gradle suites.

## 15. Static-analysis requirements
Java 21 warnings-as-errors, repository static analysis, CodeRabbit/Codacy where available, zero valid unresolved findings.

## 16. Documentation requirements
Update configuration, reload, health, recovery, operator, Wiki, registry/package/handoff records conservatively.

## 17. Security and privacy requirements
No secrets/DB rows; fail closed; redact connection details; authorization for operator commands.

## 18. Migration impact
No migration expected. Live reconciliation confirmed V16 is highest. If a migration becomes unavoidable, add a new immutable migration after V16 and test clean install, upgrade, checksums, restart, and rollback; never edit V1–V16.

## 19. Bedrock considerations
Reload/recovery must not regress Floodgate identity or text fallback; runtime Bedrock acceptance belongs to `ES-V02`.

## 20. Distributed-runtime considerations
Multiple Paper/Velocity processes, retry storms, ownership, reconnect, bounded queues, and shutdown must be safe.

## 21. External-provider considerations
Provider settings must validate explicitly; missing/incompatible providers degrade safely without invented APIs.

## 22. Completion definition
All criteria and exact-head gates pass; zero valid review threads; the one required PR merges normally; temporary branch cleanup and post-merge records are verified.

## 23. Resume state
Selected automatically from `READY` after live reconciliation. Branch `package/es-p02-runtime-db-recovery` starts at `d94d0219a598c9afb7e19c4ea9fddafd554d6469`. No prior ES-P02 branch, PR, or handoff existed. Current work is limited to the durable claim/orchestration checkpoint; product implementation has not started.

## 24. Last completed checkpoint
Live GitHub reconciled, ES-P02 selected, branch created, obsolete assigned-package rules replaced with automatic sequential selection, and durable package-routing records prepared.

## 25. Remaining checklist
Draft PR creation; all Paper and Velocity implementation; focused and full tests; documentation; review and repair; exact-head hosted validation; merge; containment and cleanup; post-merge finalization; dependency-derived status updates.

## 26. Known blockers
None known. Local shell DNS could not reach GitHub, so repository work is being performed through the authenticated GitHub connector; this is not a product or package blocker. No infrastructure exception is currently approved for ES-P02.

## 27. Final evidence
Starting `main`: `d94d0219a598c9afb7e19c4ea9fddafd554d6469`. Highest migration: V16. Issue #43 remains open and excluded. Final reviewed heads, runs/jobs, tests, static analysis, coverage, runtime artifacts, staging disposition, and review evidence are unset until implementation freezes.

## 28. Merge and synchronization record
Unset. ES-P02 is internal; external parity is not applicable. Record feature head, normal merge commit, resulting `main`, containment, divergence, branch deletion, and finalization only after those events occur.

## 29. Handoff
[`2026-08-05-es-p02-runtime-db-recovery.md`](../../reports/package-handoffs/2026-08-05-es-p02-runtime-db-recovery.md)
