# ES-P02 runtime database recovery and Velocity reload handoff

- Updated: 2026-08-06
- Package: `ES-P02 — Runtime database recovery and Velocity reload`
- Starting status: `READY`
- Current status: `BLOCKED`
- Selection: resumed through automatic sequential package selection because PR #70 remained open
- Starting `main`: `d94d0219a598c9afb7e19c4ea9fddafd554d6469`
- Current `main`: `5c969901146fc5081eec14b3c089bec7b06d5f5e`
- Branch: `package/es-p02-runtime-db-recovery`
- Pull request: `#70`, open, non-draft, unmerged, and currently non-mergeable
- Frozen product head: `b63fa1fa09ae4a9ea90988143ecda2cc7decbe14`
- Highest Flyway migration: `V16`; V1–V16 remain immutable
- Issue #43: open, deferred, and excluded
- Production authority: LiteBans remains authoritative
- External parity: not applicable; ES-P02 is internal

## Selection and live reconciliation

Live GitHub contained one unfinished package PR: PR #70 for ES-P02. The PR body and package records identified the package as blocked by required Pi staging evidence. The parent workflow failure was rechecked rather than selecting ES-X05. The staging diagnostics showed the failure occurred before the Pi runner: the ordinary `ubuntu-latest` build job received no runner and executed zero steps. The failed staging build job was rerun once; attempt 2 reproduced the same zero-runner, zero-step failure. ES-P02 therefore remains the selected package and is correctly blocked. No second package was started.

The package branch has also diverged from current `main`: it is 53 commits ahead and 5 commits behind, with merge base `d94d0219a598c9afb7e19c4ea9fddafd554d6469`. The current `main` head is `5c969901146fc5081eec14b3c089bec7b06d5f5e`.

## Included scope completed

### Paper

- Bounded exponential MariaDB bootstrap retry and recovery.
- One active attempt and one scheduled retry at a time.
- Cleanup-before-retry for partially published runtimes.
- Worker/global/entity scheduler separation and stale callback rejection.
- Retry exhaustion, recovery, shutdown suppression, and sanitized health.

### Velocity

- Bounded bootstrap recovery with transient and permanent failure handling.
- Complete partial-resource cleanup before retry.
- BOOTSTRAP authority until full runtime publication.
- Serialized terminal transitions and validated manual retry after exhaustion.
- Deterministic shutdown.
- `/estaff reload` with independent `enthusiastaff.reload` authorization.
- Complete candidate validation, immutable publication, and rollback.
- Live reload limited to fail-closed behavior and the appeals URL.
- Whole-candidate restart-required rejection for resource-bound settings.
- Atomic health issue updates that preserve the current mode.

### Process and documentation

- Automatic sequential package-selection rules were established.
- Package, registry, workspace, handoff, prompt, and worker-protocol records were updated.
- `docs/runtime-database-recovery.md` documents recovery, reload, operator, security, and migration boundaries.

## Exclusions preserved

No production database or private-data access; no Flyway repair or migration rewrite; no provider invention; no deployment, authority activation, issue #43 acceptance, shadow period, production migration, cutover, rollback, ES-X05 work, external component work, or second package.

## Tests and review evidence

Paper tests cover scheduler phase separation, transient recovery, worker rejection, exhaustion, shutdown before retry, cleanup-before-retry, stale callbacks, recovery failure, cleanup rejection, and retry payloads.

Velocity tests cover transient recovery, exhaustion, permanent failure, shutdown, manual retry without overlap, worker/scheduler rejection, atomic reload, restart-required rejection, invalid candidates, publication rollback, repeated reload, shutdown races, immutable health snapshots, and atomic issue merges.

CodeRabbit identified three confirmed defects: a Paper terminal-health overwrite, a Velocity lost-update/stale-mode health race, and overlapping Velocity bootstrap transitions. All were fixed. Manual review fixed two additional race windows. Codacy findings were addressed. Zero valid unresolved review threads were recorded before the package froze its product head.

## Exact product-head validation

Frozen product head `b63fa1fa09ae4a9ea90988143ecda2cc7decbe14`:

- Coverage workflow run `31072792371`, job `92524077883`: `SUCCESS`.
- Expected SHA verification and Java 21 setup: passed.
- Full tests, MariaDB/Testcontainers, migration integrity, and changed-code coverage threshold: passed.
- Runtime JAR build, integrity, and API/provider-leak checks: passed.
- Codacy Static Code Analysis: passed with zero annotations.
- CodeRabbit check: passed.
- Unresolved review threads: zero.
- Snyk: skipped and not counted as passing evidence.
- Exact numeric coverage and artifact hash were not surfaced by the connector, so only the configured threshold and integrity gates are claimed.

## Blocking staging evidence

Parent source-repository workflow:

- Run `31072790867`, job `92524036760`.
- Tested source SHA: `b63fa1fa09ae4a9ea90988143ecda2cc7decbe14`.
- Dispatched staging run: `31072794096`.

Staging attempt 1:

- Build job `92524048937`, `ubuntu-latest`: `FAILURE`.
- `runner_id: 0`; runner name empty; `steps: []`.
- Pi job `92524054852`: `SKIPPED`.

Staging attempt 2, triggered by rerunning the failed build job:

- Build job `92541148296`, `ubuntu-latest`: `FAILURE`.
- `runner_id: 0`; runner name empty; `steps: []`.
- Pi job `92541160241`: `SKIPPED`.

No staging product build, Pi boot, or restart step executed. This is infrastructure-unavailable evidence, not a product failure and not a pass. The missing gate is an ordinary hosted build, so `VALIDATION-POLICY.md` does not permit treating it as an owner-approved infrastructure exception. No ES-P02 package-specific owner approval exists regardless.

## Current blocker

ES-P02 cannot merge until all of the following occur:

1. an exact package head obtains a successful ordinary staging build and successful specialized-runner Pi build, safe boot, and restart result, or the owner records a policy-valid ES-P02 disposition that does not relabel a missing ordinary hosted gate as passed;
2. current `main` is merged into the package branch through the approved merge-commit workflow, without rebase or force-push, and all conflicts are resolved; and
3. every applicable exact-head hosted, static-analysis, review, artifact, migration, and staging gate is rerun on the synchronized frozen head.

## Resume state and exact next action

Resume PR #70 and `package/es-p02-runtime-db-recovery`. Do not modify product code without a newly confirmed defect. Recheck staging only when hosted runner allocation may have recovered or the owner supplies new authorization. After the staging condition can proceed, merge current `main` into the package branch, resolve conflicts, freeze the resulting head, rerun all required exact-head gates, and merge normally only if every gate passes.

## Systems not to disturb

- LiteBans authority and issue #43 acceptance.
- Production databases, credentials, routes, logs, and private data.
- V1–V16 migration bytes.
- External component repositories and aggregate component copies.
- ES-X05 and every other package.
