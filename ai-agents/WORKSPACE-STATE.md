# EnthusiaStaff workspace state

Last updated: 2026-08-05

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

`ES-P02 ACTIVE — selected automatically after live reconciliation because there was no unfinished package work, ES-P01 is COMPLETE, and ES-P02 is the lowest-priority-number eligible READY package. The package branch records the required one-time correction from explicit assignment to automatic sequential selection.`

| Field | Value |
| --- | --- |
| Canonical package status | `ai-agents/work-packages/PACKAGE-REGISTRY.md` |
| Active package | `ES-P02 — Runtime database recovery and Velocity reload` |
| Starting status | `READY` |
| Current status | `ACTIVE` |
| Starting `main` | `d94d0219a598c9afb7e19c4ea9fddafd554d6469` |
| Branch | `package/es-p02-runtime-db-recovery` |
| Pull request | `Pending early draft creation after claim checkpoint` |
| Package handoff | `ai-agents/reports/package-handoffs/2026-08-05-es-p02-runtime-db-recovery.md` |
| Current checkpoint | Live state reconciled; package selected and branch created; orchestration documents updated; runtime implementation not yet started. |
| Exact next action | Complete durable claim records, open the draft PR, then implement bounded Paper and Velocity database bootstrap recovery plus atomic Velocity reload and tests. |
| Other ready package | `ES-X05 — remains READY and unstarted` |

## Selection evidence

- Live `main` was `d94d0219a598c9afb7e19c4ea9fddafd554d6469`.
- No open or draft PR existed.
- No remote branch other than `main` existed before the ES-P02 branch was created.
- ES-P01 was complete and ES-P02 and ES-X05 were ready.
- ES-P02 priority 20 precedes ES-X05 priority 35.
- ES-P02 depends only on complete ES-P01 and is not parallel-safe around lifecycle/configuration.

## Previous package evidence retained

ES-P01 completed through implementation PR #68 and finalization PR #69. Its exact reviewed implementation head is contained in `main`; the implementation branch was deleted; and its owner-approved zero-execution infrastructure exception does not claim a Pi pass. Missing distributed Pi and Java/Bedrock staging evidence remains assigned to ES-V02.

## ES-P02 boundaries

- Included: transient Paper/Velocity MariaDB bootstrap recovery, bounded retry, safe Velocity configuration reload, invalid-candidate rollback, restart-required reporting, health/operator feedback, reconnect/repeated-reload/shutdown/race tests, and directly necessary documentation.
- Excluded: production database access, Flyway repair or history rewrite, unrelated configuration redesign, provider invention, deployment, authority activation, issue #43, shadow period, cutover, or another package.
- Migration boundary: no migration expected; V16 remains highest and V1–V16 are immutable.
- LiteBans remains authoritative.
