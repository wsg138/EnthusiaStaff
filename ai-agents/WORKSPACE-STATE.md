# EnthusiaStaff workspace state

Last updated: 2026-08-02 18:18 America/Indiana/Indianapolis

This file is a concise routing record for the next AI agent. It must be verified against live GitHub and repository state before use.

## Repository

| Field | Recorded value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current `main` SHA | **LIVE VERIFY FROM GITHUB** |
| Checkpoint before AI-workspace PR | `f53143132db29b9cd75e7caa6589f979d99af8c4` |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java | 21 |
| Runtime | Paper-compatible backends, Velocity, MariaDB |

## Current work after PR #47 merges

| Field | Value |
| --- | --- |
| State | `IDLE` |
| Active PR | None expected; verify live |
| Active branch | None expected; verify live |
| Active work item | None |
| Known blocker | None |

Before starting new work, inspect live open PRs and branches. Resume relevant unfinished work even when this table says `IDLE`.

## Latest repository-process update

| Field | Value |
| --- | --- |
| PR | `#47 — Add shared AI agent workflow` |
| Branch | `agent/add-ai-agent-workflow` |
| Intended result | AI-agent rules, shared state, universal prompt, and handoff-report location merged into `main` |
| Handoff | `ai-agents/reports/agent-handoffs/2026-08-02-ai-agent-workspace.md` |
| Exact validation and merge evidence | Read PR #47 live |

The next agent must verify whether PR #47 merged successfully and reconcile this file if it did not.

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

## Next planned work item

### Staff report workflow

Priority: High

Status: Prerequisites complete; no feature branch or PR recorded.

Objective:

- player `/report` submission;
- staff report queue and database-bounded filtering;
- assignment, claiming, and reassignment;
- report detail and append-only event timeline;
- staff notes and evidence references;
- escalation into cases and punishment requests;
- resolution, dismissal, duplicate linking, and reopening;
- durable staff and reporter notifications;
- searchable report history;
- Java and Bedrock command parity;
- concurrency, idempotency, restart, permission, privacy, and reload coverage.

The agent must inspect existing report code and the goals documents before deciding the exact PR scope. It must not assume every listed item is absent.

## Migration state

| Field | Value |
| --- | --- |
| Recorded highest migration | `V14__punishment_history_and_exact_sanction_changes.sql` |
| Immutable migrations | `V1` through `V14` |
| Expected next number | `V15`, unless live repository state shows a newer legitimate migration |
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