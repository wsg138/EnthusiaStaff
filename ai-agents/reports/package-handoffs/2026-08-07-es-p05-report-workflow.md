# ES-P05 package handoff — report evidence and staff workflow completion

Recorded: 2026-08-07 America/Chicago

## Package and routing

| Field | Value |
| --- | --- |
| Package | `ES-P05 — Report evidence and staff workflow completion` |
| Classification at selection | `READY` |
| Current status | `ACTIVE` / `ACTIONABLE_CONTINUATION` |
| Repository | `wsg138/EnthusiaStaff` |
| Starting legitimate `main` | `bf9b305ba96d9536f3d111c79eef674bd2e11dc5` |
| Branch | `package/es-p05-report-workflow` |
| Pull request | Pending first coherent checkpoint |
| Migration boundary | immutable V17 |
| External parity | not applicable; internal package |

Live GitHub and current default-branch source override this handoff if they diverge. This file records only the selected package and must not be used to activate another package.

## Startup reconciliation

- Read the universal package prompt and required package policies from current legitimate `main`.
- Open PR inventory contained only PR #70, `ES-P02 — Runtime database recovery and Velocity reload`.
- ES-P02 remains `BLOCKED` / `PARKED_BLOCKED`: its exact external unblock condition is still the private Actions Billing & plans account restriction. No evidence showed that condition changed, so the branch/PR was not synchronized or rerun.
- Live branches were `main`, `package/es-p02-runtime-db-recovery`, and the separate `docs/wiki-maintenance-2026-08` branch. The docs branch is not a package branch and had no package-state authority; this ES-P05 worker does not modify it.
- No ES-P05 branch, PR, or prior ES-P05 package handoff existed.
- ES-P03 and ES-P04 are complete. ES-P05 was therefore the lowest-priority-number dependency-complete READY package, ahead of ES-P09 and ES-P10.
- Issue #43 remains open/deferred and is explicitly outside this package.
- LiteBans remains authoritative.

## Package scope

Included:

- player report submission and target resolution;
- ordinary and same-target cooldowns, duplicate prevention, merge/open-report behavior, and replay/idempotency durability;
- staff queue/detail GUI and complete command/text fallback;
- note/status transitions with exact revision and stale-state protection;
- concurrent reviewer behavior and failure/retry clarity;
- chat, coordinate/location, and provider-independent client evidence;
- retention, purge, privacy, least-privilege presentation, and bounded evidence;
- explicit attachment behavior decision;
- restart/reconnect behavior and package-scoped documentation/tests.

Explicitly excluded:

- RoseChat private-message evidence implementation (`ES-X01`);
- Discord route rendering/delivery completion (`ES-P06`);
- production evidence, private databases/player rows, production routes, credentials, deployment, cutover, issue #43 acceptance, or authority activation.

## Existing foundations identified before implementation

Historical merged report checkpoints already provide substantial foundations:

- durable report submission, cooldown/duplicate/replay persistence and transactional outbox behavior;
- `/report` player command and `/reports` staff command;
- queue/detail GUI with immutable per-open GUI configuration, private `/reports note ...` action-note capture, exact displayed-revision mutation, stable operation UUIDs, out-of-order async load fencing, entity-scheduler presentation, and command fallback;
- modular `reports.yml` and `gui/reports.yml` policy/configuration with validated atomic reload;
- `ChatContextBuffer`, coordinate/location capture, client evidence persistence, report evidence retention and purge maintenance;
- existing durable query/state stores, row locks, optimistic revisions, audit, report-message persistence, and Discord-outbox writes.

The audit still records weak direct proof for command/GUI runtime wiring and no attachment workflow. ES-P05 must verify the current code rather than assume those historical statements remain correct.

## Current evidence and known constraints

- Package-start `main`: `bf9b305ba96d9536f3d111c79eef674bd2e11dc5`.
- Package-start migration boundary: V17, unchanged by ES-P04.
- Current audit route: `AUD-REPORT-001`, `AUD-REPORT-002`, and provider-independent portions of `AUD-REPORT-003`.
- Representative live Java/Bedrock and distributed acceptance remains `ES-V02`; ES-P05 must still provide automated text-fallback and Bedrock-safe behavior proof.
- RoseChat PM capture is unavailable by design in this package and must not be invented.

## Checkpoint state

Completed:

1. Live GitHub reconciliation and classification.
2. ES-P05 selection through canonical routing.
3. Exact branch creation from `bf9b305b...`.
4. Durable registry/package/workspace claim publication on the package branch.
5. Initial historical audit/requirements/report handoff review.

In progress:

- trace current report domain, persistence, Paper command/GUI, evidence and maintenance code against package acceptance criteria;
- identify only confirmed provider-independent gaps;
- implement and test the package without expanding into ES-X01 or ES-P06.

## Exact next action

Read current report source/tests/configuration on the ES-P05 branch, compare each package acceptance criterion to direct implementation/test evidence, then implement only the missing provider-independent behavior and strengthen direct command/GUI/privacy/restart/concurrency proof.

## Systems not to disturb

- parked ES-P02 PR #70 and branch;
- `docs/wiki-maintenance-2026-08` documentation worker branch;
- RoseChat external provider work;
- Discord route delivery work;
- external component repositories;
- production/private data and credentials;
- LiteBans authority and issue #43;
- existing immutable migrations V1–V17.
