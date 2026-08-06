# ES-P02 runtime database recovery and Velocity reload handoff

- Updated: 2026-08-06
- Package: `ES-P02 — Runtime database recovery and Velocity reload`
- Starting status: `READY`
- Current status: `BLOCKED`
- Current routing classification: `PARKED_BLOCKED` while the exact runner and authorization condition remains unchanged
- Starting `main`: `d94d0219a598c9afb7e19c4ea9fddafd554d6469`
- Canonical `main` at status-publication start: `5c969901146fc5081eec14b3c089bec7b06d5f5e`
- Branch: `package/es-p02-runtime-db-recovery`
- Pull request: `#70`, open, non-draft, unmerged, and currently non-mergeable
- Frozen product head: `b63fa1fa09ae4a9ea90988143ecda2cc7decbe14`
- Current package-record and PR head: `80d4ea840f34017c09afb618f623581b31c6223d`
- Highest Flyway migration: `V16`; V1–V16 remain immutable
- Issue #43: open, deferred, and excluded
- Production authority: LiteBans remains authoritative
- External parity: not applicable; ES-P02 is internal
- Active implementation package: `NONE`
- Next eligible ready package: `ES-X05`, unstarted

## Canonical routing disposition

PR #70 and its unique product work remain preserved. ES-P02 is not actionable merely because the PR is open, the branch is behind current `main`, or the PR is non-mergeable. Its required next product action still depends on the same unavailable runner or a new policy-valid owner disposition, and there is no other known actionable defect.

While that condition remains unchanged, classify ES-P02 as `PARKED_BLOCKED`. Do not rerun the identical staging gate, merge `main` into the package branch merely to keep it current, modify PR #70, or start repair work without a newly confirmed defect. Continue canonical selection to the eligible `READY` package.

When hosted runner availability or owner authorization demonstrably changes, classify ES-P02 as `ACTIONABLE_CONTINUATION` and resume PR #70 before starting another new package.

## Included scope completed on the preserved implementation branch

### Paper

- Bounded exponential MariaDB bootstrap retry and recovery.
- One active attempt and one scheduled retry at a time.
- Cleanup-before-retry for partially published runtimes.
- Worker, global, and entity scheduler separation and stale callback rejection.
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

### Documentation

- `docs/runtime-database-recovery.md` on the preserved implementation branch documents recovery, reload, operator, security, and migration boundaries.
- The implementation branch package records preserve the detailed product checklist and evidence.
- This canonical handoff publishes routing and blocker state plus summarized scope and validation evidence; it does not copy product source or test files.

## Exclusions preserved

No production database or private-data access; no Flyway repair or migration rewrite; no provider invention; no deployment, authority activation, issue #43 acceptance, shadow period, production migration, cutover, rollback, ES-X05 work, external component work, or second implementation package.

## Tests and review evidence

Paper tests cover scheduler phase separation, transient recovery, worker rejection, exhaustion, shutdown before retry, cleanup-before-retry, stale callbacks, recovery failure, cleanup rejection, and retry payloads.

Velocity tests cover transient recovery, exhaustion, permanent failure, shutdown, manual retry without overlap, worker and scheduler rejection, atomic reload, restart-required rejection, invalid candidates, publication rollback, repeated reload, shutdown races, immutable health snapshots, and atomic issue merges.

CodeRabbit identified three confirmed defects: a Paper terminal-health overwrite, a Velocity lost-update and stale-mode health race, and overlapping Velocity bootstrap transitions. All were fixed. Manual review fixed two additional concurrency windows. Codacy findings were addressed. Zero valid unresolved review threads remain on PR #70.

## Successful hosted product validation

Frozen product head `b63fa1fa09ae4a9ea90988143ecda2cc7decbe14`:

- Coverage workflow run `31072792371`, job `92524077883`: `SUCCESS`.
- Expected SHA verification and Java 21 setup: passed.
- Full tests, MariaDB and Testcontainers, migration integrity, and changed-code coverage threshold: passed.
- Runtime JAR build, integrity, and API or provider-leak checks: passed.
- Codacy Static Code Analysis: passed with zero annotations.
- CodeRabbit check: passed.
- Valid unresolved review threads: zero.
- Snyk: skipped and not counted as passing evidence.
- Exact numeric coverage and artifact hash were not surfaced by the connector, so only the configured threshold and integrity gates are claimed.

## Blocking staging evidence

Parent source-repository workflow:

- Run `31072790867`, job `92524036760`.
- Tested source SHA: `b63fa1fa09ae4a9ea90988143ecda2cc7decbe14`.
- Dispatched staging run: `31072794096`.

Staging attempt 1:

- Ordinary hosted build job `92524048937`, label `ubuntu-latest`: `FAILURE`.
- `runner_id: 0`; runner name empty; `steps: []`.
- Downstream Pi job `92524054852`: `SKIPPED`.

Staging attempt 2, triggered by rerunning the failed build job once:

- Ordinary hosted build job `92541148296`, label `ubuntu-latest`: `FAILURE`.
- `runner_id: 0`; runner name empty; `steps: []`.
- Downstream Pi job `92541160241`: `SKIPPED`.

No staging product build, Pi boot, or restart step executed. This is infrastructure-unavailable evidence, not a product failure and not a pass. The unavailable job is an ordinary hosted build gate. No ES-P02 package-specific infrastructure exception or owner authorization exists.

Do not rerun an identical zero-runner gate without evidence that runner capacity, billing, authorization, configuration, or environment availability changed. A manual rerun alone is not evidence that the unblock condition changed.

## Exact unblock condition

All of the following are required before merge:

1. an exact package head obtains a successful ordinary staging build and successful specialized-runner Pi build, safe boot, and restart result, or the owner records a policy-valid ES-P02 disposition that does not relabel a missing ordinary hosted gate as passed;
2. after that external condition changes, current `main` is merged into the package branch through the approved normal merge-commit workflow, without rebase or force-push, and conflicts are resolved; and
3. every applicable exact-head hosted, static-analysis, review, artifact, migration, and staging gate is rerun on the synchronized frozen head with zero valid unresolved findings.

Branch drift and non-mergeability do not make ES-P02 actionable while the external condition is unchanged. Do not synchronize merely to keep the branch current.

## Exact next-worker behavior while unchanged

1. Inspect PR #70 and confirm its head, checks, reviews, package records, and exact unblock condition.
2. Classify ES-P02 as `PARKED_BLOCKED`.
3. Do not rerun staging.
4. Do not merge `main` into the ES-P02 branch.
5. Do not modify or close PR #70.
6. Select ES-X05 as the lowest-priority eligible `READY` package.
7. Complete only ES-X05 and stop.

## Systems not to disturb

- PR #70, its unique commits, and `package/es-p02-runtime-db-recovery`.
- LiteBans authority and issue #43 acceptance.
- Production databases, credentials, routes, logs, and private data.
- V1–V16 migration bytes.
- Workflows and runtime configuration.
- External component repositories and aggregate component copies.
- ES-X05 during this documentation-only status-publication work.
