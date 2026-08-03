# EnthusiaStaff workspace state

Last updated: 2026-08-02

This file is a concise routing record for the next AI agent. It must be verified against live GitHub and repository state before use.

## Repository

| Field | Recorded value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current `main` SHA | `d07cb888952fde575a4f8245571f8d1ebc858b63` at PR #50 start; live verify before acting |
| Checkpoint after report-configuration work | `d07cb888952fde575a4f8245571f8d1ebc858b63` |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java | 21 |
| Runtime | Paper-compatible backends, Velocity, MariaDB |

## Current work

| Field | Value |
| --- | --- |
| State | `BLOCKED` |
| Active PR | `#50 — Record RoseChat provider blocker after PR 49` until live merge verification |
| Active branch | `docs/record-rosechat-provider-blocker` until live merge and cleanup verification |
| Active work item | Reconcile post-PR #49 state and formally record the RoseChat private-message provider blocker |
| Implementation state | Documentation reconciliation complete; no speculative integration code was added |
| Known blocker | The intended `wsg138/Enthusia-RoseChat` repository and a supported private-message callback API are unavailable |
| Required input | Accessible supported provider repository or artifact with callback lifecycle, identity, delivery, threading, version and privacy semantics |
| Handoff | `ai-agents/reports/agent-handoffs/2026-08-02-pr50-rosechat-provider-blocker.md` |
| Exact validation and merge evidence | Read PR #50 live |

If PR #50 is merged, no implementation PR remains active. The RoseChat feature itself remains blocked until the required provider contract exists.

## Live reconciliation at work-item start

- PR #49 was already merged with normal merge commit `d07cb888952fde575a4f8245571f8d1ebc858b63`.
- No pull request was open or in draft.
- Every remaining remote branch was `ahead_by: 0` relative to `main`; no unfinished work was displaced.
- PR #49 had zero unresolved review threads.
- The exact PR #49 feature-head validation recorded run `30774370125`, job `91566952409`, Java 21.0.9 and a successful full Gradle build through Flyway V14.
- No pull-request-triggered workflow run was returned for the PR #49 merge commit; no post-merge CI result is claimed.
- The live highest migration remains `V14__punishment_history_and_exact_sanction_changes.sql`.
- Installed and public repository search found no accessible `wsg138/Enthusia-RoseChat` repository.

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
| PR #50 schema result | No migration added or edited |
| Locked deployed checksums | V11 `-2005375055`; V12 `-1787751803`; V13 `1189066017` |

Never edit an existing migration. Verify the live migration directory before adding a new migration.

## RoseChat provider blocker

The next recorded report-system item is a supported RoseChat private-message callback and privacy presentation boundary. It cannot be implemented honestly until all of the following are available:

- an accessible provider repository or published supported API artifact;
- the exact callback/event contract and lifecycle timing;
- sender, recipient, cancellation and delivery semantics;
- supported version and dependency coordinates;
- threading and Paper/Folia scheduling guarantees;
- message identity or sequence semantics needed for duplicate safety;
- the privacy and retention fields the provider can supply without reflection or private implementation access.

Do not add reflection against unknown RoseChat internals, invent provider-owned API classes, scrape logs as a substitute callback, capture before delivery semantics are known, or claim support based only on an unverified stub.

## Next legitimate work

1. Resume the RoseChat private-message callback only after the required supported provider contract becomes available.
2. If it remains unavailable, reconcile live GitHub again and select the highest-priority prerequisite-complete item from the goals, development map and requirements matrix.
3. Do not begin that second item inside PR #50.

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
