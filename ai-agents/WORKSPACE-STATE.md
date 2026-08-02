# EnthusiaStaff workspace state

Last updated: 2026-08-02 18:56 America/Indiana/Indianapolis

This file is a concise routing record for the next AI agent. It must be verified against live GitHub and repository state before use.

## Repository

| Field | Recorded value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current `main` SHA | `9d7f31945db37f9b2872ac57cb24de6b6b281198` at work-item start; live verify before acting |
| Checkpoint before report-GUI work | `9d7f31945db37f9b2872ac57cb24de6b6b281198` |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java | 21 |
| Runtime | Paper-compatible backends, Velocity, MariaDB |

## Current work

| Field | Value |
| --- | --- |
| State | `PLANNING` |
| Active PR | Draft PR pending first branch commit |
| Active branch | `feature/report-queue-gui` |
| Active work item | Staff report queue and detail GUI with stale-state-safe actions and text-command fallback |
| Known blocker | None |

Before starting different work, inspect the live branch and pull requests. Resume this report-GUI work when it remains unfinished.

## Latest repository-process update

| Field | Value |
| --- | --- |
| PR | `#47 — Add shared AI agent workflow` |
| Branch | `agent/add-ai-agent-workflow` |
| Merge commit | `9d7f31945db37f9b2872ac57cb24de6b6b281198` |
| Result | AI-agent rules, shared state, universal prompt, and handoff-report location merged into `main` |
| Handoff | `ai-agents/reports/agent-handoffs/2026-08-02-pr47-ai-agent-workspace.md` |
| Exact validation and merge evidence | Read PR #47 live |

## Last completed product work

| Field | Value |
| --- | --- |
| PR | `#46 — Add punishment history and sanction lifecycle workflow` |
| Final feature head | `070cc5e0e7f65a33f8b57259f03324039d7a6369` |
| Merge commit | `f53143132db29b9cd75e7caa6589f979d99af8c4` |
| Resulting `main` at that checkpoint | `f53143132db29b9cd75e7caa6589f979d99af8c4` |
| Handoff | `ai-agents/reports/agent-handoffs/2026-08-02-pr46-punishment-history.md` |

Completed product capabilities include:

- database-bounded `/history`;
- complete `/case` timeline;
- exact-sanction reduce, end, revoke, and overturn actions;
- appeal and punishment-request linkage;
- append-only mutation history;
- offline, historical-name, UUID, Java, and Bedrock identity resolution;
- transactional authorization, hierarchy, concurrency, and idempotency;
- reloadable history and sanction-action settings.

## Current planned work item

### Staff report queue and detail GUI

Priority: High

Status: Active on `feature/report-queue-gui`.

Objective:

- staff inventory GUI for report queues and report details;
- asynchronous database reads with entity-thread inventory opening;
- exact-revision state changes so stale screens cannot overwrite newer work;
- inventory-click/drag safety, viewer ownership, permission revalidation, and duplicate-submit protection;
- existing `/reports` text commands retained as Java and Bedrock fallback;
- focused tests, documentation, harsh review, exact-head validation, durable handoff, and normal merge when every gate passes.

Explicitly deferred from this work item:

- modular `reports.yml` and GUI-file configuration;
- RoseChat private-message provider bridge;
- Discord report rendering;
- production-like multi-server staging and production deployment.

## Migration state

| Field | Value |
| --- | --- |
| Live highest migration at work-item start | `V14__punishment_history_and_exact_sanction_changes.sql` |
| Immutable migrations | `V1` through `V14` |
| Expected next number | `V15`, unless live repository state shows a newer legitimate migration |
| This work item | No schema change expected |
| Locked deployed checksums | V11 `-2005375055`; V12 `-1787751803`; V13 `1189066017` |

Never edit an existing migration. Verify the live migration directory before adding a new migration.

## Production and release boundary

- LiteBans remains authoritative.
- Issue #43 remains open.
- No production deployment is authorized.
- No production database or production-derived backup access is authorized.
- No 168-hour production acceptance window is active.
- Merging dormant development code does not authorize production cutover.
- Staging controls are separate and must not be changed unless the current work item explicitly requires them.

## Required references

Read before implementation:

- `ai-agents/AGENTS.md`
- `ai-agents/reports/agent-handoffs/latest.md`
- `ENTHUSIASTAFF-GOALS.md`
- `WORKSPACE-MANIFEST.md`
- `docs/wiki/pages/Development-Blueprint.md`
- `reports/REQUIREMENTS-MATRIX.md`

## Update contract

The current work PR must update this file before tracked content is frozen for exact-head validation.

At minimum update:

- active state, PR, branch, and work item;
- latest completed repository work;
- latest completed product work when applicable;
- next planned work;
- migration boundary;
- blockers;
- handoff link.

Do not create a circular commit sequence by attempting to embed the current PR's final SHA, final CI run IDs, or merge commit in this tracked file. Put exact final-head and merge evidence in the PR description or comments. The next agent must always reconcile this file with live GitHub.
