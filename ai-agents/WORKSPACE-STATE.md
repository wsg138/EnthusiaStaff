# EnthusiaStaff workspace state

Last updated: 2026-08-02

This file is a concise routing record for the next AI agent. It must be verified against live GitHub and repository state before use.

## Repository

| Field | Recorded value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current `main` SHA | `39e616bbdcd61f540d77406155f3b579b4fc57ab` at PR #49 start; live verify before acting |
| Checkpoint before report-configuration work | `39e616bbdcd61f540d77406155f3b579b4fc57ab` |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java | 21 |
| Runtime | Paper-compatible backends, Velocity, MariaDB |

## Current work

| Field | Value |
| --- | --- |
| State | `VALIDATION` |
| Active PR | `#49 — Add modular report configuration and safe reload` |
| Active branch | `feature/report-configuration-reload` |
| Active work item | Modular report policy and GUI configuration with validated reload behavior |
| Implementation state | Complete and harsh-reviewed; tracked content frozen for exact-head validation |
| Known blocker | None recorded in tracked content; live checks, review threads, Pi evidence and merge gates must be verified |
| Handoff | `ai-agents/reports/agent-handoffs/2026-08-02-pr49-report-configuration.md` |
| Exact validation and merge evidence | Read PR #49 live |

Live GitHub showed no open pull requests at work-item start. Every remaining remote branch was fully contained in `main`, so no unfinished branch work was displaced.

## Active implementation summary

PR #49 includes:

- immutable report policy for cooldowns, duplicate window, open-report limit, query limits, recently-closed window, evidence retention and cleanup batch size;
- bundled, operator-editable `reports.yml` preserving prior defaults;
- bundled `gui/reports.yml` for inventory size, slots, materials, titles and messages;
- strict cross-file parsing, exact-key checks, item-material validation and per-screen slot-overlap validation;
- startup validation and `/estaff reload` rejection that retains the previous valid report configuration;
- atomic publication after the existing reload coordinator succeeds;
- one policy snapshot per persistence operation and one GUI snapshot per open inventory;
- configured queue-result capping and evidence retention without new main-thread database work;
- focused loader, reload and MariaDB/Testcontainers policy tests;
- operator documentation in `docs/wiki/pages/Report-Configuration.md`.

The separate harsh review fixed stale-inventory slot reinterpretation, a lowered-query-limit reload race, concurrent reload publication and registry-dependent pure unit tests.

## Latest completed product work

| Field | Value |
| --- | --- |
| PR | `#48 — Add staff report queue and detail GUI` |
| Branch | `feature/report-queue-gui` |
| Merge commit | `39e616bbdcd61f540d77406155f3b579b4fc57ab` |
| Result | Staff report inventory queues, report detail/review screens, exact-revision actions and text-command fallback |
| Handoff | `ai-agents/reports/agent-handoffs/2026-08-02-pr48-report-queue-gui.md` |
| Exact validation and merge evidence | Read PR #48 live |

## Previous completed product work

| Field | Value |
| --- | --- |
| PR | `#46 — Add punishment history and sanction lifecycle workflow` |
| Merge commit | `f53143132db29b9cd75e7caa6589f979d99af8c4` |
| Handoff | `ai-agents/reports/agent-handoffs/2026-08-02-pr46-punishment-history.md` |

## Migration state

| Field | Value |
| --- | --- |
| Live highest migration at PR #49 start | `V14__punishment_history_and_exact_sanction_changes.sql` |
| Immutable migrations | `V1` through `V14` |
| Expected next number | `V15`, unless live repository state shows a newer legitimate migration |
| PR #49 schema result | No migration added or edited |
| Locked deployed checksums | V11 `-2005375055`; V12 `-1787751803`; V13 `1189066017` |

Never edit an existing migration. Verify the live migration directory before adding a new migration.

## Remaining report-system work outside PR #49

- supported RoseChat private-message callback integration;
- dedicated sensitive-evidence presentation and privacy review;
- Discord report rendering and delivery validation;
- production-like multi-server/Folia staging.

Do not begin those items until PR #49 is closed and live repository state is reconciled.

## Production and release boundary

- LiteBans remains authoritative.
- Issue #43 remains open.
- No production deployment is authorized.
- No production database, credentials, production-derived backup or private player evidence may be accessed.
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

Do not create a circular commit sequence by embedding the current PR's final SHA, final CI run IDs or merge commit in this tracked file. Exact live evidence belongs in the PR description or comments, and every next agent must reconcile this file with GitHub before acting.
