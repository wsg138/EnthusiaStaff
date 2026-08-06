# EnthusiaStaff workspace state

Last updated: 2026-08-06

This is a routing record. Live GitHub must be reconciled before acting. Live PR, branch, review, and check state override stale default-branch package text, but known persistent state must be published to `main` through the documented status-publication process.

## Repository

| Field | Value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Canonical process baseline before this correction | `5c969901146fc5081eec14b3c089bec7b06d5f5e` |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java/runtime | Java 21; Paper and Leaf backends, Velocity, MariaDB |
| Highest migration | `V16`; V1–V16 remain immutable |
| Issue #43 | Open and deferred |

## Current package routing

`ES-P01 is COMPLETE. ES-P02 is BLOCKED and PARKED_BLOCKED in preserved PR #70 while its runner and authorization condition remains unchanged. ES-X05 is READY and unstarted. ES-V02 is DEFERRED. No implementation package is active.`

| Field | Value |
| --- | --- |
| Completed prerequisite | `ES-P01 — Exact-sanction appeal isolation` |
| Blocked package | `ES-P02 — Runtime database recovery and Velocity reload` |
| Classification | `PARKED_BLOCKED` while the exact external unblock condition is unchanged |
| Preserved branch | `package/es-p02-runtime-db-recovery` |
| Preserved PR | `#70 — open, non-draft, unmerged, and currently non-mergeable` |
| Frozen product head | `b63fa1fa09ae4a9ea90988143ecda2cc7decbe14` |
| Current package-record head | `80d4ea840f34017c09afb618f623581b31c6223d` |
| Canonical main at publication start | `5c969901146fc5081eec14b3c089bec7b06d5f5e` |
| Blocker authority | PR #70, preserved branch records, checks, canonical ES-P02 package file, and canonical package handoff |
| Ready package | `ES-X05 — Website UX, authentication, and appeals` |
| Deferred validation | `ES-V02 — Distributed and Java/Bedrock staging` |
| Active implementation package | `NONE` |

The blocked-package routing and status-publication correction is documentation-only and is not an implementation package. It does not change ES-P02 product code, tests, evidence, status, scope, priority, dependencies, or blocker. It does not activate ES-X05.

## Selection classification and order

Every incomplete package is classified before selection:

- `ACTIONABLE_CONTINUATION`: existing work has a safe action that can be performed now.
- `PARKED_BLOCKED`: the same unavailable external condition still controls the next action and no other actionable defect exists.
- `READY`: dependency-complete and eligible to begin.

Select the highest-priority `ACTIONABLE_CONTINUATION`, skip every `PARKED_BLOCKED` package, then select the eligible `READY` package with the lowest numerical priority. An open PR or branch alone does not receive priority.

## Required next-worker behavior

While PR #70's runner and authorization condition remains unchanged, a sequential worker must:

1. inspect PR #70, its exact head, checks, reviews, package records, and unblock condition;
2. classify ES-P02 as `PARKED_BLOCKED`;
3. not rerun the identical staging gate;
4. not merge `main` into the ES-P02 branch merely to keep it current;
5. not modify or close PR #70;
6. select ES-X05 as the lowest-priority eligible `READY` package;
7. complete only ES-X05 and stop.

When runner availability or owner authorization demonstrably changes, a future worker must classify ES-P02 as `ACTIONABLE_CONTINUATION` and resume PR #70 before starting another new package.

## ES-P02 retained evidence

- Frozen product head `b63fa1fa09ae4a9ea90988143ecda2cc7decbe14` passed the hosted Java 21 build, tests, MariaDB and Testcontainers, migration integrity, changed-code coverage threshold, runtime-JAR and provider-leak checks, Codacy with zero annotations, CodeRabbit, and zero valid unresolved review threads.
- Required staging run `31072794096` did not execute a product step.
- Ordinary hosted build attempts `92524048937` and `92541148296` each had `runner_id: 0`, an empty runner name, and `steps: []`.
- Downstream Pi jobs `92524054852` and `92541160241` were skipped.
- No staging product build, Pi boot, or restart executed. This is not a pass.
- No ES-P02 package-specific infrastructure exception or owner authorization exists.
- Branch drift and non-mergeability do not make the package actionable while the external blocker is unchanged.

## Persistent status publication

An unmerged implementation PR that stops in `PARTIAL`, `BLOCKED`, `REVIEW`, `MERGE_PENDING`, or `SYNC_PENDING` must have that state published to `main` through a small documentation-only PR before the worker stops, unless tool loss makes publication impossible.

The publication PR preserves the implementation PR and branch and may update only the registry, selected package file, workspace state, canonical handoff, latest handoff pointer, and directly necessary routing documentation. It contains no product code, product tests, migrations, workflow changes, or runtime configuration.

## Boundaries

- No product code, product test, migration, workflow, runtime configuration, deployment, authority, private data, LiteBans cutover, issue #43 acceptance, shadow period, or production behavior is changed by this process correction.
- LiteBans remains authoritative.
- PR #70 and `package/es-p02-runtime-db-recovery` must remain preserved with all unique work.
- Do not begin ES-X05 in this process-correction session.
