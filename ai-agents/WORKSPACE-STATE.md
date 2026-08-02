# EnthusiaStaff workspace state

Last updated: 2026-08-02 18:07 America/Indiana/Indianapolis

This file is a concise routing record for the next AI agent. It must be verified against live GitHub and repository state before use.

## Repository

| Field | Recorded value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Recorded `main` SHA | `f53143132db29b9cd75e7caa6589f979d99af8c4` |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java | 21 |
| Runtime | Paper-compatible backends, Velocity, MariaDB |

## Current work

| Field | Value |
| --- | --- |
| State | `IDLE` |
| Active PR | None |
| Active branch | None |
| Active work item | None |
| Known blocker | None |

Before starting new work, inspect live open PRs and branches. Resume relevant unfinished work even when this table still says `IDLE`.

## Last completed work

| Field | Value |
| --- | --- |
| PR | `#46 — Add punishment history and sanction lifecycle workflow` |
| Final feature head | `070cc5e0e7f65a33f8b57259f03324039d7a6369` |
| Merge commit | `f53143132db29b9cd75e7caa6589f979d99af8c4` |
| Resulting `main` | `f53143132db29b9cd75e7caa6589f979d99af8c4` |
| Handoff | `ai-agents/reports/agent-handoffs/2026-08-02-pr46-punishment-history.md` |

Completed capabilities include:

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

Status: Prerequisites complete; no branch or PR recorded.

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

The current work PR must update this file before merge.

At minimum update:

- recorded `main` or final feature head as appropriate;
- active state, PR, branch, and work item;
- completed work;
- next planned work;
- migration boundary;
- blockers;
- handoff link.

Because a merge commit does not exist until after the PR content is finalized, the handoff may record the final feature head and PR number before merge. The agent must verify the actual merge commit live after merging and record it in the PR description or final PR comment. The next agent must always reconcile this file with live GitHub.