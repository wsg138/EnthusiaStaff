# `ES-P02` — Runtime database recovery and Velocity reload

## 1. Package identity
`ES-P02`; Internal; primary `COMP-STAFF`; priority 20; not parallel-safe around lifecycle/configuration.

## 2. Status
Initial `PLANNED`; registry is authoritative.

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
`ES-P01` must be `COMPLETE`.

## 9. Component and repository boundaries
Root EnthusiaStaff runtime/configuration/tests/docs only. No external component import, permanent component branch, or isolated PR.

## 10. Required branches
Temporary `package/es-p02-runtime-db-recovery`; delete after verified merge containment.

## 11. Required PRs
One PR to `wsg138/EnthusiaStaff:main`.

## 12. Implementation checklist
Reconcile live heads/status; reproduce gaps; implement bounded recovery/reload; test success/failure/concurrency/restart; update package state/handoff/docs; harshly review; freeze; exact-head validate; merge normally; verify cleanup.

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
No migration expected. If unavoidable, add a new migration after live V16 verification; never edit V1–V16.

## 19. Bedrock considerations
Reload/recovery must not regress Floodgate identity or text fallback; runtime Bedrock acceptance belongs to `ES-V02`.

## 20. Distributed-runtime considerations
Multiple Paper/Velocity processes, retry storms, ownership, reconnect, bounded queues, and shutdown must be safe.

## 21. External-provider considerations
Provider settings must validate explicitly; missing/incompatible providers degrade safely without invented APIs.

## 22. Completion definition
All criteria and exact-head gates pass; zero valid review threads; the one required PR merges normally; temporary branch cleanup verified.

## 23. Resume state
Unassigned; no branch/PR/handoff. Do not start until registry advances and assigns it.

## 24. Last completed checkpoint
Package definition only; no product implementation began.

## 25. Remaining checklist
All implementation, tests, review, validation, merge, and evidence remain.

## 26. Known blockers
Dependency `ES-P01`; otherwise none known after live reconciliation.

## 27. Final evidence
Unset: record starting/final heads, runs/jobs, test/static-analysis/migration results, review disposition.

## 28. Merge and synchronization record
Unset: record feature head, merge commit, resulting main, containment, and temporary branch deletion; parity not applicable.
