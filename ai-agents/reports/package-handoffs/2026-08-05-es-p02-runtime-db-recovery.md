# ES-P02 runtime database recovery and Velocity reload handoff

- Date: 2026-08-05
- Package: `ES-P02 — Runtime database recovery and Velocity reload`
- Starting status: `READY`
- Current status: `ACTIVE`
- Selection: automatic sequential package selection
- Starting `main`: `d94d0219a598c9afb7e19c4ea9fddafd554d6469`
- Branch: `package/es-p02-runtime-db-recovery`
- Pull request: pending early draft creation at this checkpoint
- Highest Flyway migration: `V16`; V1–V16 remain immutable
- Issue #43: open, deferred, and explicitly excluded
- Production authority: LiteBans remains authoritative

## Selection and live reconciliation

Live GitHub was reconciled before selection:

- `main` was `d94d0219a598c9afb7e19c4ea9fddafd554d6469`, the normal merge of ES-P01 finalization PR #69.
- No open or draft pull request existed.
- No remote branch other than `main` existed before the ES-P02 branch was created.
- ES-P01 was `COMPLETE`.
- ES-P02 and ES-X05 were `READY`; ES-P02 priority 20 precedes ES-X05 priority 35.
- ES-P02 depends only on ES-P01 and is not parallel-safe around lifecycle/configuration.
- Issue #43 remained open and reserved for later production-like LiteBans acceptance.
- V16 remained the highest migration.
- No previous ES-P02 branch, PR, checkpoint, or handoff existed.

ES-P02 was therefore the next eligible package. ES-X05 remains READY and was not started.

## Authority and package scope

Included audit IDs:

- `AUD-RUNTIME-001`
- `AUD-RUNTIME-002`
- `AUD-CONFIG-002`
- `AUD-CONFIG-003`
- `AUD-CONFIG-004`
- `AUD-PERF-005`
- relevant `AUD-ESC-005`

Included behavior:

- bounded Paper and Velocity database bootstrap recovery;
- transient startup failure recovery without process restart;
- atomic Velocity configuration reload;
- invalid-candidate rejection and rollback;
- explicit restart-required reporting;
- degraded, retrying, exhausted, healthy, and restart-required health/operator states;
- reconnect, repeated reload, failed candidate, shutdown, and race tests;
- directly necessary configuration, operator, Wiki, and package documentation.

Explicit exclusions:

- production database access or private rows;
- Flyway repair or migration-history rewrite;
- unrelated configuration redesign;
- invented provider APIs or repositories;
- deployment or production authority changes;
- issue #43, the 168-hour shadow period, migration, activation, cutover, or rollback;
- ES-X05 or any other package.

## Reproduced source gaps

Paper:

- `StorageBootstrapCoordinator` permits one `start()` call and enters a terminal state after a bootstrap failure.
- `EnthusiaStaffPaperPlugin.startStorageBootstrap()` constructs and invokes that one-shot coordinator once.
- A transient initial MariaDB failure therefore leaves storage unavailable until process restart.

Velocity:

- `onProxyInitialization` submits `initializeStorage()` once.
- `initializeStorage()` loads configuration, initializes MariaDB, publishes many runtime fields/resources, and degrades health on failure without a retry lifecycle.
- Velocity has no `/estaff reload` path.
- Several resource-bound configuration values are composed only during initial bootstrap, so safe reload must distinguish atomically reloadable values from restart-required values.
- Failed partial initialization needs explicit cleanup before retry.

## Completed checkpoint

- Created `package/es-p02-runtime-db-recovery` from exact starting `main`.
- Replaced obsolete owner-supplied `Assigned package ID` rules with automatic sequential selection in:
  - `ai-agents/AGENTS.md`;
  - `ai-agents/work-packages/WORKER-PROTOCOL.md`;
  - `ai-agents/UNIVERSAL-AGENT-PROMPT.md`.
- Preserved resume-first behavior, dependency ordering, one-package scope, branch/PR rules, exact-head validation, production boundaries, and stop conditions.
- Updated ES-P02 package and workspace routing records.
- No product source, test, migration, workflow, provider, or production path has changed yet.

## Planned implementation direction

Paper:

- make bootstrap retry bounded and explicit rather than reusing cleanup-only retry behavior;
- prevent duplicate concurrent attempts;
- retry transient bootstrap/recovery failures with capped delays;
- stop deterministically during plugin shutdown;
- preserve worker/global/entity thread separation and off-main-thread MariaDB close;
- publish health transitions for retrying, exhausted, and recovered states.

Velocity:

- introduce a testable bounded bootstrap retry coordinator;
- open and validate a complete candidate runtime before publication;
- close partial resources on any failed attempt;
- publish a successful runtime atomically enough that event/command readers do not observe mismatched configuration/store/resource state;
- stop retries and close runtime resources deterministically on shutdown;
- add `/estaff reload` with explicit permission and bounded asynchronous dispatch;
- validate candidates before mutation;
- reload only values that are safe to publish live and report every changed resource-bound value as restart-required;
- keep the previous active configuration on invalid or rejected candidates;
- expose sanitized operator status without connection details or secrets.

This direction is not final evidence. It must be implemented, tested, harshly reviewed, and exact-head validated.

## Tests and validation

Completed at this checkpoint:

- source and existing-test inspection only;
- no build or test run is claimed;
- no workflow result is claimed;
- no static-analysis result is claimed;
- no Pi or staging result is claimed.

Required later evidence:

- focused Paper bootstrap retry, recovery, exhaustion, shutdown, rejection, and late-callback tests;
- focused Velocity retry, partial cleanup, atomic reload, invalid candidate, restart-required, repeated reload, concurrent reload, and shutdown-race tests;
- applicable MariaDB/Testcontainers failure/reconnect proof;
- Java 21 warnings-as-errors full build and module tests;
- migration clean-install/upgrade/checksum integrity with V16 unchanged;
- aggregate coverage and runtime JAR/provider-leak inspection;
- configured static analysis, CodeRabbit, Codacy, documentation/package validation, and zero valid unresolved review threads;
- applicable exact-head staging result or a separately authorized package-specific disposition. ES-P01's exception does not automatically apply.

## Current branch history

The first checkpoint consists of orchestration and routing commits on `package/es-p02-runtime-db-recovery`. Record the exact latest branch head and draft PR number after the PR is opened.

## Current blockers

None known.

The local shell could not resolve GitHub DNS, so repository operations are being performed through the authenticated GitHub connector. This is not a product defect, package blocker, test result, or infrastructure exception.

## Exact next action

1. Update the canonical registry and latest-handoff route to mark ES-P02 ACTIVE.
2. Open an early draft PR from `package/es-p02-runtime-db-recovery` to `main` and record its number/head.
3. Implement and test bounded Paper bootstrap recovery.
4. Implement and test bounded Velocity bootstrap recovery and atomic reload.
5. Continue durable checkpoints without beginning another package.

## Systems not to disturb

- LiteBans authority and issue #43 acceptance.
- Production databases, credentials, routes, and private data.
- V1–V16 migration bytes.
- External component repositories and aggregate component copies.
- ES-X05 and every other package.
