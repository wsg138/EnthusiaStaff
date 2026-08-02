# EnthusiaStaff workspace state

Last updated: 2026-08-02 19:24 America/Indiana/Indianapolis

This file is a concise routing record for the next AI agent. It must be verified against live GitHub and repository state before use.

## Repository

| Field | Recorded value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current `main` SHA | `9d7f31945db37f9b2872ac57cb24de6b6b281198` at PR #48 start; live verify before acting |
| Checkpoint before report-GUI work | `9d7f31945db37f9b2872ac57cb24de6b6b281198` |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java | 21 |
| Runtime | Paper-compatible backends, Velocity, MariaDB |

## Intended post-merge work state

| Field | Value |
| --- | --- |
| State | `IDLE` after PR #48 merges; otherwise resume PR #48 |
| Active PR | `#48 — Add staff report queue and detail GUI` until live merge verification |
| Active branch | `feature/report-queue-gui` until live merge and cleanup verification |
| Active work item | None after PR #48 merges |
| Known blocker | None recorded in tracked state; read PR #48 exact-head evidence live |
| Handoff | `ai-agents/reports/agent-handoffs/2026-08-02-pr48-report-queue-gui.md` |

The next agent must inspect live pull requests, review threads, checks and branch state. Resume PR #48 if it remains open or blocked; do not start competing report-GUI work.

## Latest product work

| Field | Value |
| --- | --- |
| PR | `#48 — Add staff report queue and detail GUI` |
| Branch | `feature/report-queue-gui` |
| Intended result | Staff report inventory queues, report detail/review screens, exact-revision actions and text-command fallback |
| Handoff | `ai-agents/reports/agent-handoffs/2026-08-02-pr48-report-queue-gui.md` |
| Exact validation and merge evidence | Read PR #48 live |

Implemented scope in PR #48:

- `/reports` inventory GUI for authorized Paper players;
- open, mine, all-claimed, awaiting-review and recently-closed filters;
- database work on the bounded executor and entity-thread inventory presentation;
- per-viewer fencing against out-of-order asynchronous loads;
- report detail, location and retained-evidence counts without raw sensitive JSON in lore;
- private note and confirmation screens for claim, review and resolution actions;
- exact displayed revisions and stable idempotency keys for mutations;
- safe distinction between mutation outcome and follow-up refresh outcome;
- inventory click/drag cancellation, viewer ownership, permission rechecks and disconnect cleanup;
- explicit text command workflow retained for console and Bedrock use;
- focused unit tests and updated staff documentation.

## Previous completed product work

| Field | Value |
| --- | --- |
| PR | `#46 — Add punishment history and sanction lifecycle workflow` |
| Final feature head | `070cc5e0e7f65a33f8b57259f03324039d7a6369` |
| Merge commit | `f53143132db29b9cd75e7caa6589f979d99af8c4` |
| Handoff | `ai-agents/reports/agent-handoffs/2026-08-02-pr46-punishment-history.md` |

## Next recommended work item

### Modular report policy and GUI configuration

Priority: High

Start only after live verification that PR #48 is merged and no older relevant PR requires work.

Expected scope:

- move report cooldowns, duplicate window, open-report limit, query limits and retention policy into validated report configuration;
- move report inventory titles, materials, slots and messages into validated GUI/configuration files where the repository architecture permits;
- support atomic safe reload with the previous valid configuration retained after invalid input;
- preserve command/console/Bedrock fallback behavior;
- document defaults, permissions and reload behavior;
- add invalid-config, reload, concurrency and usability coverage.

Do not silently combine this with the separate RoseChat callback bridge, Discord rendering or production staging.

## Migration state

| Field | Value |
| --- | --- |
| Live highest migration at PR #48 start | `V14__punishment_history_and_exact_sanction_changes.sql` |
| Immutable migrations | `V1` through `V14` |
| Expected next number | `V15`, unless live repository state shows a newer legitimate migration |
| PR #48 schema result | No migration added or edited |
| Locked deployed checksums | V11 `-2005375055`; V12 `-1787751803`; V13 `1189066017` |

Never edit an existing migration. Verify the live migration directory before adding a new migration.

## Remaining report-system work

- modular report and GUI configuration;
- supported RoseChat private-message callback integration;
- dedicated sensitive-evidence presentation and privacy review;
- Discord report rendering and delivery validation;
- production-like multi-server/Folia staging.

## Production and release boundary

- LiteBans remains authoritative.
- Issue #43 remains open.
- No production deployment is authorized.
- No production database, credentials, production-derived backup or private player evidence was accessed.
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

The next work PR must update this file before tracked content is frozen for exact-head validation.

Do not create a circular commit sequence by embedding the current PR's final SHA, final CI run IDs or merge commit in this tracked file. Exact live evidence belongs in the PR description or comments, and every next agent must reconcile this file with GitHub before acting.
