# ES-P02 runtime database recovery and Velocity reload handoff

- Updated: 2026-08-06
- Package: `ES-P02 — Runtime database recovery and Velocity reload`
- Starting status: `READY`
- Current status: `REVIEW`
- Selection: automatic sequential package selection
- Starting `main`: `d94d0219a598c9afb7e19c4ea9fddafd554d6469`
- Branch: `package/es-p02-runtime-db-recovery`
- Pull request: `#70`, open and non-draft
- Highest Flyway migration: `V16`; V1–V16 remain immutable
- Issue #43: open, deferred, and excluded
- Production authority: LiteBans remains authoritative
- External parity: not applicable; ES-P02 is internal

## Selection and live reconciliation

Before claim, live GitHub showed `main` at `d94d0219a598c9afb7e19c4ea9fddafd554d6469`, no open or draft PR, no package branch, ES-P01 complete, ES-P02 and ES-X05 ready, V16 highest, and issue #43 still open. ES-P02 priority 20 precedes ES-X05 priority 35 and depends only on ES-P01. ES-X05 remains READY and unstarted.

## Included scope completed

### Paper

- Replaced terminal one-shot bootstrap with a bounded coordinator.
- Default retry cycle: six attempts with capped exponential delays of 1, 2, 4, 8, 16, and at most 30 seconds.
- Fenced one active attempt and one scheduled retry.
- Required removal and close of a partially published runtime before retry.
- Preserved worker/global/entity scheduler separation.
- Ignored stale entity/global callbacks from retired attempts.
- Suppressed new attempts after shutdown and handled cleanup worker rejection.
- Published sanitized BOOTSTRAP, retrying, recovered, and exhausted health.

### Velocity

- Added a bounded bootstrap coordinator with transient retry, permanent-failure exhaustion, manual restart of an exhausted cycle, worker/scheduler rejection, serialized terminal transitions, and shutdown suppression.
- Kept authority in BOOTSTRAP until full runtime publication.
- Closed partial channel, network outbox, Discord worker, website server, scheduled tasks, stores, and MariaDB runtime before retry.
- Added deterministic shutdown cleanup and guaranteed store clearing even if runtime close throws.
- Added `/estaff reload` with the independent `enthusiastaff.reload` permission.
- Validated a complete candidate before publication.
- Live-reloaded only `fail-closed-while-active` and `appeals-url` as one immutable snapshot.
- Restored the previous snapshot when publication failed.
- Rejected every resource-bound change with explicit restart-required keys and no partial application.
- Rejected overlapping reload and reload during shutdown.
- Allowed one immediate bounded bootstrap cycle after an exhausted startup only after configuration validation.
- Made health issue changes atomic and tied them to the current authority mode.

### Orchestration and documentation

- Replaced obsolete mandatory `Assigned package ID` rules with automatic sequential selection in the repository agent prompt, rules, and worker protocol.
- Updated package, workspace, registry routing, and latest handoff records.
- Added `docs/runtime-database-recovery.md` with retry, reload, operator, security, migration, and validation boundaries.

## Exclusions preserved

No production database, private player row, credential, secret, production route, deployment, provider invention, Flyway repair, migration rewrite, authority activation, issue #43 acceptance, shadow period, production migration, cutover, rollback, external component repository, ES-X05, or second package was touched.

## Tests added

Paper tests cover phase separation, transient recovery, initial worker rejection, retry exhaustion, shutdown before retry, cleanup-before-retry, retired entity callbacks, recovery failure, cleanup worker rejection, and callback payloads.

Velocity bootstrap tests cover transient recovery, configured exhaustion, permanent failure, shutdown, immediate retry without overlap, worker rejection, and scheduler rejection. Reload tests cover atomic application, restart-required rejection, invalid candidates, publication rollback, repeated reload, shutdown before load, and shutdown after candidate load. Runtime-health tests cover immutable snapshots and atomic issue merges.

## Review findings and repairs

CodeRabbit reported three valid defects:

1. Paper could overwrite a terminal scheduling failure with a misleading retry health message.
2. Velocity health read-copy-write updates could lose concurrent issues and republish a stale mode.
3. Velocity bootstrap could expose a terminal-transition window in which manual retry overlapped the completing attempt.

All were repaired. The review also produced lower-severity test and maintainability findings. Relevant repairs include initial worker-rejection tests, scheduler-rejection tests, retry payload assertions, a split Paper test harness, removed dead attempt state, simplified recovery steps, reload shutdown-race coverage, and simplified bounded backoff. Current unresolved review-thread count is zero.

A later harsh review identified and repaired an additional permanent-failure notification race: the exhausted health callback now occurs inside the serialized bootstrap transition so a manual retry cannot be followed by a stale degraded publication.

## Workflow and validation history

No final exact-head pass is claimed yet.

- Earlier Coverage runs are superseded.
- Superseded run `31070331833`, job `92516786222`, executed Paper, persistence, protocol, and integration tests before Velocity compilation failed because `VelocityBootstrapCoordinator.PermanentFailure` lacked `serialVersionUID` under warnings-as-errors.
- The compiler defect was fixed.
- CodeRabbit reviewed a superseded implementation head and its confirmed defects were repaired.
- The final tracked head still needs current-head Coverage/build, static analysis, documentation/package validation, runtime artifact checks, review-bot evidence, and any applicable staging disposition.
- No ES-P02 infrastructure exception is approved. ES-P01's exception does not apply.
- The local shell cannot resolve GitHub, so no local Gradle or shell validation is claimed.

## Migration and privacy boundary

No migration was added. V16 remains highest, and V1–V16 are byte-immutable. No private or production data was accessed. Logs and operator messages expose component state and exception classes only, not connection details or sensitive values.

## Current checkpoint

Implementation, focused tests, documentation, and all known review repairs are complete. The pre-record implementation/documentation head was `decb40702820333726f4dfa787af73a5ddb370c9`. Subsequent canonical REVIEW records advance the branch and must finish before the exact head freezes.

## Remaining work

1. Update the registry and latest routing to REVIEW with PR #70 and current facts.
2. Freeze the resulting tracked branch head.
3. Ensure PR #70's head equals that branch head; stale earlier PR-head metadata is not evidence.
4. Request a current-head CodeRabbit review and verify zero valid unresolved findings.
5. Run and inspect every applicable current-head GitHub Actions job, including Java 21 warnings-as-errors, full tests, MariaDB/Testcontainers, migration integrity, static analysis, coverage, runtime JAR/provider-leak checks, and documentation/package validation.
6. Determine the exact Pi/staging gate result under `VALIDATION-POLICY.md`; do not inherit ES-P01's exception.
7. Merge PR #70 normally only if the validated head remains unchanged and all gates pass.
8. Verify resulting `main`, containment, divergence, no unique commits, and safe branch deletion.
9. Merge a documentation-only finalization PR if required to record actual merge facts, mark ES-P02 complete, and move dependency-cleared packages to READY without starting them.
10. Stop without beginning another package.

## Known blockers

No product blocker is known. Current-head hosted evidence is still missing, so the package correctly remains REVIEW.

## Systems not to disturb

- LiteBans authority and issue #43 acceptance.
- Production databases, credentials, routes, and private data.
- V1–V16 migration bytes.
- External component repositories and aggregate component copies.
- ES-X05 and every other package.
