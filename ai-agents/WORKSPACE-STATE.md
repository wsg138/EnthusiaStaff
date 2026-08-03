# EnthusiaStaff workspace state

Last updated: 2026-08-02

This file is a concise routing record for the next AI agent. It must be verified against live GitHub and repository state before use.

## Repository

| Field | Recorded value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current `main` SHA | `d07cb888952fde575a4f8245571f8d1ebc858b63` at this work-item start; live verify before acting |
| Checkpoint after report-configuration work | `d07cb888952fde575a4f8245571f8d1ebc858b63` |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java | 21 |
| Runtime | Paper-compatible backends, Velocity, MariaDB |

## Current work

| Field | Value |
| --- | --- |
| State | `PLANNING` |
| Active PR | Pending creation after this initial branch commit |
| Active branch | `docs/record-rosechat-provider-blocker` |
| Active work item | Reconcile post-PR #49 state and formally record the RoseChat private-message provider blocker |
| Implementation state | Live repository and provider availability reconciliation in progress |
| Known blocker | The intended `wsg138/Enthusia-RoseChat` repository and a supported private-message callback API are not available; do not invent an integration contract |
| Handoff | Pending on the active branch |
| Exact validation and merge evidence | Record live in the active PR |

## Live reconciliation at work-item start

- PR #49 was already merged with normal merge commit `d07cb888952fde575a4f8245571f8d1ebc858b63`.
- No pull request was open or in draft.
- Every remaining remote branch was `ahead_by: 0` relative to `main`; no unfinished work was displaced.
- PR #49 had zero unresolved review threads.
- The exact PR #49 feature-head validation recorded run `30774370125`, job `91566952409`, Java 21.0.9 and a successful full Gradle build through Flyway V14.
- The live highest migration remains `V14__punishment_history_and_exact_sanction_changes.sql`.
- Repository search found no accessible `wsg138/Enthusia-RoseChat` repository and no supported provider contract was supplied.

## Latest completed product work

| Field | Value |
| --- | --- |
| PR | `#49 — Add modular report configuration and safe reload` |
| Branch | `feature/report-configuration-reload` |
| Final feature head | `1ad41be3eeca49370694916f386dda0484e3bfa3` |
| Merge commit | `d07cb888952fde575a4f8245571f8d1ebc858b63` |
| Result | Validated report policy and GUI configuration with atomic safe reload |
| Handoff | `ai-agents/reports/agent-handoffs/2026-08-02-pr49-report-configuration.md` |
| Exact validation and merge evidence | Read PR #49 live |

## Migration state

| Field | Value |
| --- | --- |
| Live highest migration | `V14__punishment_history_and_exact_sanction_changes.sql` |
| Immutable migrations | `V1` through `V14` |
| Expected next number | `V15`, unless live repository state shows a newer legitimate migration |
| Current work schema result | No migration planned or permitted for this documentation-only reconciliation |
| Locked deployed checksums | V11 `-2005375055`; V12 `-1787751803`; V13 `1189066017` |

Never edit an existing migration. Verify the live migration directory before adding a new migration.

## RoseChat provider blocker

The next recorded report-system item is a supported RoseChat private-message callback and privacy presentation boundary. It cannot be implemented honestly until all of the following are available:

- an accessible provider repository or published supported API artifact;
- the exact callback/event contract and lifecycle timing;
- sender, recipient, cancellation and delivery semantics;
- supported version and dependency coordinates;
- the privacy and retention fields that the provider can supply without reflection or private implementation access.

Do not add reflection against unknown RoseChat internals, invent provider-owned API classes, scrape logs as a substitute callback, or claim private-message evidence support without the provider contract.

## Production and release boundary

- LiteBans remains authoritative.
- Issue #43 remains open.
- No production deployment is authorized.
- No production database, credentials, production-derived backup or private player evidence may be accessed.
- No 168-hour production acceptance window is active.
- Merging dormant development or documentation does not authorize production cutover.
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
