# EnthusiaStaff workspace state

Last updated: 2026-08-06

This is a routing record. Live GitHub must be reconciled before acting. Live PR, branch, review, and check state override stale default-branch package text.

## Repository

| Field | Value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current process baseline | `d94d0219a598c9afb7e19c4ea9fddafd554d6469` |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java/runtime | Java 21; Paper/Leaf backends, Velocity, MariaDB |
| Highest migration | `V16`; V1–V16 remain immutable |
| Issue #43 | Open and deferred |

## Current package routing

`ES-P01 is COMPLETE. ES-P02 is BLOCKED in preserved PR #70. ES-X05 is READY and unstarted. ES-V02 is DEFERRED. No implementation package is active.`

| Field | Value |
| --- | --- |
| Completed prerequisite | `ES-P01 — Exact-sanction appeal isolation` |
| Blocked package | `ES-P02 — Runtime database recovery and Velocity reload` |
| Preserved branch | `package/es-p02-runtime-db-recovery` |
| Preserved PR | `#70 — open and unmerged` |
| Blocker authority | Live PR #70, its branch records, checks, and canonical ES-P02 handoff |
| Ready package | `ES-X05 — Website UX, authentication, and appeals` |
| Deferred validation | `ES-V02 — Distributed and Java/Bedrock staging` |
| Active implementation package | `NONE` |

The process-extraction work is documentation-only and is not an implementation package. It does not change ES-P02 product code, tests, evidence, status, scope, priority, dependencies, or blocker. It does not activate ES-X05.

## Sequential selection

A future sequential worker must:

1. reconcile live GitHub and inspect PR #70;
2. resume ES-P02 only when its unblock condition may have changed or actionable work exists;
3. leave ES-P02 blocked when the blocker is unchanged;
4. then select the lowest-priority eligible `READY` package, which is currently ES-X05;
5. complete exactly one package and stop.

When the default-branch registry is stale relative to live PR evidence, the worker must use live GitHub for selection and correct the stale package state on the selected package branch.

## ES-P01 retained evidence

- Implementation PR #68 merged normally at `203b2854d5546a6d3744037c367099129654b42a`.
- Finalization PR #69 merged normally at `d94d0219a598c9afb7e19c4ea9fddafd554d6469`.
- ES-P01's owner-approved zero-execution infrastructure exception remains recorded without calling the Pi gate passed.
- The deferred distributed Pi/Java/Bedrock obligation remains assigned to ES-V02.

## Boundaries

- No product code, test, migration, workflow, runtime configuration, deployment, authority, private data, LiteBans cutover, issue #43 acceptance, shadow period, or production behavior is changed by the process extraction.
- LiteBans remains authoritative.
- PR #70 and `package/es-p02-runtime-db-recovery` must remain preserved with all unique work.
- Do not begin ES-X05 in the process-extraction session.
