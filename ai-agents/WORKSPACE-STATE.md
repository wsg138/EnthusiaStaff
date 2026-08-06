# EnthusiaStaff workspace state

Last updated: 2026-08-06

This is a routing record. Live GitHub and `ai-agents/work-packages/PACKAGE-REGISTRY.md` must be reconciled before acting.

## Repository

| Field | Value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current package starting `main` | `d94d0219a598c9afb7e19c4ea9fddafd554d6469` |
| Current live `main` | `5c969901146fc5081eec14b3c089bec7b06d5f5e` |
| Previous package implementation merge | `203b2854d5546a6d3744037c367099129654b42a` |
| Previous package finalization merge | `d94d0219a598c9afb7e19c4ea9fddafd554d6469` |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java/runtime | Java 21; Paper/Leaf backends, Velocity, MariaDB |
| Highest migration | `V16`; V1–V16 remain immutable |
| Issue #43 | Open and deferred; excluded from ES-P02 |

## Current package state

`ES-P02 BLOCKED — implementation and hosted product validation passed for frozen product head b63fa1fa09ae4a9ea90988143ecda2cc7decbe14. Required staging run 31072794096 failed twice because the ordinary ubuntu-latest build job received no runner and executed zero steps; the Pi job was skipped. The branch is also 53 commits ahead and 5 commits behind current main.`

| Field | Value |
| --- | --- |
| Canonical package status | `ai-agents/work-packages/PACKAGE-REGISTRY.md` |
| Active package | `ES-P02 — Runtime database recovery and Velocity reload` |
| Starting status | `READY` |
| Current status | `BLOCKED` |
| Starting `main` | `d94d0219a598c9afb7e19c4ea9fddafd554d6469` |
| Current live `main` | `5c969901146fc5081eec14b3c089bec7b06d5f5e` |
| Branch | `package/es-p02-runtime-db-recovery` |
| Pull request | `#70 — open, non-draft, unmerged, currently non-mergeable` |
| Package handoff | `ai-agents/reports/package-handoffs/2026-08-05-es-p02-runtime-db-recovery.md` |
| Frozen product head | `b63fa1fa09ae4a9ea90988143ecda2cc7decbe14` |
| Current checkpoint | Product scope, tests, documentation, review repair, Java 21 build/test/coverage, MariaDB/Testcontainers, migration integrity, runtime-JAR/provider-leak, Codacy, and review-thread gates passed. Staging attempts 1 and 2 failed before any step because no hosted runner was assigned. |
| Branch divergence | `53 commits ahead, 5 commits behind; merge base d94d0219a598c9afb7e19c4ea9fddafd554d6469` |
| Exact next action | Recheck staging only when runner allocation may have recovered or new owner authorization exists. Then merge current main into the package branch through an ordinary merge commit, resolve conflicts, freeze the synchronized head, and rerun every exact-head gate before merge. |
| Other ready package | `ES-X05 — remains READY and unstarted; do not activate it while this worker is handling ES-P02` |

## Selection evidence

- Live GitHub contained open PR #70 for ES-P02, so resume-first selection applied.
- The existing package record said Pi staging had failed, but the failure reason was incomplete.
- Staging run `31072794096` attempt 1 build job `92524048937` had `runner_id: 0`, empty runner name, and `steps: []`; Pi job `92524054852` was skipped.
- The failed build job was rerun. Attempt 2 build job `92541148296` again had `runner_id: 0`, empty runner name, and `steps: []`; Pi job `92541160241` was skipped.
- No product build, Pi boot, or restart executed in staging, so no product failure and no pass is claimed.
- Because the unavailable job is an ordinary hosted build, repository policy does not permit treating it as a passing or excepted gate.
- No second package was started.

## Implemented scope

- Paper: bounded exponential bootstrap retry, one active attempt, cleanup-before-retry, stale callback rejection, shutdown suppression, exhaustion, recovery health, and scheduler-correct startup recovery.
- Velocity: bounded transient retry, permanent-failure/manual-retry path, complete partial-resource cleanup, BOOTSTRAP authority until publication, deterministic shutdown, and serialized terminal transitions.
- Reload: `enthusiastaff.reload`, complete candidate validation, immutable publication of fail-closed and appeal URL settings, rollback, concurrent-reload rejection, shutdown rejection, and explicit restart-required reporting for resource-bound settings.
- Tests: worker and scheduler rejection, recovery, exhaustion, cleanup, stale callbacks, repeated reload, invalid candidate, publication rollback, restart-required candidates, atomic health, shutdown, and race paths.
- Documentation: `docs/runtime-database-recovery.md`.
- Orchestration: obsolete explicit-assignment rules replaced with automatic sequential package selection.

## Review state

CodeRabbit identified three confirmed defects: a Paper terminal-health overwrite, a Velocity lost-update/stale-mode health race, and a Velocity overlapping bootstrap race. All were fixed. Manual review fixed two additional race windows. Codacy passed with zero annotations and zero valid unresolved review threads were recorded for the frozen product head.

## Validation state

Frozen product head `b63fa1fa09ae4a9ea90988143ecda2cc7decbe14` passed source-repository Coverage workflow run `31072792371`, job `92524077883`, including Java 21, full tests, MariaDB/Testcontainers, migration integrity, changed-code coverage threshold, runtime JAR integrity, and provider-leak checks. Codacy and CodeRabbit checks passed. Snyk was skipped and is not counted as passing evidence.

Required staging remains blocked. Parent run `31072790867`, job `92524036760`, dispatched staging run `31072794096`. Attempts 1 and 2 both failed before execution because the ordinary hosted build job received no runner. The Pi job was skipped. No ES-P02 owner-approved exception exists, and a missing ordinary hosted build cannot be relabeled as passed.

## Boundaries

- No production database, private player data, credential, secret, or production route was accessed.
- No migration was added; V16 remains highest and V1–V16 remain immutable.
- LiteBans remains authoritative.
- Issue #43, staging acceptance, the shadow period, production migration, activation, cutover, rollback, ES-X05, and every other package remain untouched.
