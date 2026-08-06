# EnthusiaStaff workspace state

Last updated: 2026-08-06

This is a routing record. Live GitHub and `ai-agents/work-packages/PACKAGE-REGISTRY.md` must be reconciled before acting.

## Repository

| Field | Value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current package starting `main` | `d94d0219a598c9afb7e19c4ea9fddafd554d6469` |
| Previous package implementation merge | `203b2854d5546a6d3744037c367099129654b42a` |
| Previous package finalization merge | `d94d0219a598c9afb7e19c4ea9fddafd554d6469` |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java/runtime | Java 21; Paper/Leaf backends, Velocity, MariaDB |
| Highest migration | `V16`; V1–V16 remain immutable |
| Issue #43 | Open and deferred; excluded from ES-P02 |

## Current package state

`ES-P02 REVIEW — implementation, focused tests, documentation, and review repair are complete on package/es-p02-runtime-db-recovery. Exact-head hosted validation, merge, containment, cleanup, finalization, and dependency-derived status updates remain.`

| Field | Value |
| --- | --- |
| Canonical package status | `ai-agents/work-packages/PACKAGE-REGISTRY.md` |
| Active package | `ES-P02 — Runtime database recovery and Velocity reload` |
| Starting status | `READY` |
| Current status | `REVIEW` |
| Starting `main` | `d94d0219a598c9afb7e19c4ea9fddafd554d6469` |
| Branch | `package/es-p02-runtime-db-recovery` |
| Pull request | `#70 — open, non-draft` |
| Package handoff | `ai-agents/reports/package-handoffs/2026-08-05-es-p02-runtime-db-recovery.md` |
| Current checkpoint | Paper and Velocity bounded recovery, atomic Velocity reload, tests, documentation, and all confirmed CodeRabbit repairs are committed. Zero review threads are unresolved. |
| Pre-record implementation head | `decb40702820333726f4dfa787af73a5ddb370c9` |
| Exact next action | Complete canonical REVIEW records, freeze the resulting head, make PR #70 reflect it, request current-head review, and inspect every exact-head hosted workflow before merge. |
| Other ready package | `ES-X05 — remains READY and unstarted` |

## Selection evidence

- Live `main` was `d94d0219a598c9afb7e19c4ea9fddafd554d6469`.
- No open or draft PR existed and no package branch existed before ES-P02 was claimed.
- ES-P01 was complete and ES-P02 and ES-X05 were ready.
- ES-P02 priority 20 precedes ES-X05 priority 35.
- ES-P02 depends only on complete ES-P01 and is not parallel-safe around lifecycle/configuration.

## Implemented scope

- Paper: bounded exponential bootstrap retry, one active attempt, cleanup-before-retry, stale callback rejection, shutdown suppression, exhaustion, recovery health, and scheduler-correct startup recovery.
- Velocity: bounded transient retry, permanent-failure/manual-retry path, complete partial-resource cleanup, BOOTSTRAP authority until publication, deterministic shutdown, and serialized terminal transitions.
- Reload: `enthusiastaff.reload`, complete candidate validation, immutable publication of fail-closed and appeal URL settings, rollback, concurrent-reload rejection, shutdown rejection, and explicit restart-required reporting for resource-bound settings.
- Tests: worker and scheduler rejection, recovery, exhaustion, cleanup, stale callbacks, repeated reload, invalid candidate, publication rollback, restart-required candidates, atomic health, shutdown, and race paths.
- Documentation: `docs/runtime-database-recovery.md`.
- Orchestration: obsolete explicit-assignment rules replaced with automatic sequential package selection.

## Review state

CodeRabbit identified three confirmed defects: a Paper terminal-health overwrite, a Velocity lost-update/stale-mode health race, and a Velocity overlapping bootstrap race. All were fixed. Every review thread is resolved. Current-head hosted review and static analysis must still be rerun after the final canonical record commit.

## Validation state

No final exact-head pass is claimed. Earlier workflow runs are superseded. A prior Coverage job ran Paper, persistence, protocol, and integration tests before exposing a Velocity warnings-as-errors `serialVersionUID` defect; that defect is fixed, but only the final frozen head may supply merge evidence. No ES-P02 infrastructure exception is approved.

## Boundaries

- No production database, private player data, credential, secret, or production route was accessed.
- No migration was added; V16 remains highest and V1–V16 remain immutable.
- LiteBans remains authoritative.
- Issue #43, staging acceptance, the shadow period, production migration, activation, cutover, rollback, ES-X05, and every other package remain untouched.
