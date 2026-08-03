# EnthusiaStaff workspace state

Last updated: 2026-08-02

This file is a concise routing record for the next AI agent. It must be verified against live GitHub and repository state before use.

## Repository

| Field | Recorded value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current `main` SHA | `ead1b5a02d3e8dc71eeb5ceb3c9505da1843e727` at PR #51 start; live verify before acting |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java | 21 |
| Runtime | Paper-compatible backends, Velocity, MariaDB |

## Current work

| Field | Value |
| --- | --- |
| Intended post-merge state | `IDLE` after PR #51 is live-verified |
| Active PR | `#51 — Fix escalation clean-period decay` until live merge verification |
| Active branch | `fix/escalation-clean-period-decay` until live merge and cleanup verification |
| Active work item | Correct escalation decay so recent related reoffending resets the clean-period clock |
| Implementation state | Code, focused tests, routing records and immutable handoff frozen before exact-head validation |
| Known product blocker | Supported RoseChat private-message provider contract remains unavailable |
| Handoff | `ai-agents/reports/agent-handoffs/2026-08-02-pr51-escalation-clean-period-decay.md` |
| Exact validation and merge evidence | Read PR #51 live |

If PR #51 is merged, no implementation PR remains active. The broader escalation requirement remains partial, and RoseChat remains externally blocked.

## Live reconciliation at work-item start

- PR #50 was merged with normal merge commit `ead1b5a02d3e8dc71eeb5ceb3c9505da1843e727`.
- No pull request was open or in draft.
- Every pre-existing remote branch was `ahead_by: 0` relative to `main`; no unfinished work was displaced.
- PR #50 had zero unresolved review threads.
- PR #50 exact-head workflows `30775061520` (`Validate Wiki`) and `30775061525` (`Coverage`) succeeded for `e5d72a9809b7aabec39e95705e6e0a82f4a3f663`.
- The live highest migration remained `V14__punishment_history_and_exact_sanction_changes.sql`.
- The supported RoseChat callback/API contract remained unavailable and was not invented.

## PR #51 completed behavior

- Decay intervals are calculated from the most recent contributing, non-overturned related offense instead of independently from every historical offense.
- A recent related reoffense resets the clean-period clock for older related contributions.
- Existing severity weighting, family filtering, contribution/overturn filtering, future-end filtering, 30-day recency bonus, non-decaying policies and finite-ladder clamping are preserved.
- Focused tests cover 89-day, 90-day and 180-day boundaries, reset behavior, shared clean-period decay and non-decaying policy behavior.
- No command, permission, configuration key, provider contract, schema or migration changed.

## Migration state

| Field | Value |
| --- | --- |
| Live highest migration at PR #51 start | `V14__punishment_history_and_exact_sanction_changes.sql` |
| Immutable migrations | `V1` through `V14` |
| Expected next number | `V15`, unless live repository state shows a newer legitimate migration |
| PR #51 schema result | No migration added or edited |
| Locked deployed checksums | V11 `-2005375055`; V12 `-1787751803`; V13 `1189066017` |

Never edit an existing migration. Verify the live migration directory before adding a new migration.

## Remaining escalation work

The clean-period correctness defect is fixed by PR #51, but the complete escalation requirement remains partial. Separate future work still includes:

- modular/versioned escalation configuration;
- explicit aliases for renamed stable IDs;
- readable but unselectable removed IDs;
- policy snapshot behavior across ladder edits;
- serious-offense decay metadata;
- broader combined-recommendation and acceptance coverage.

Do not silently expand PR #51 into those separate feature slices.

## RoseChat provider blocker

The supported private-message callback and privacy presentation boundary remains blocked until an accessible provider repository or published API artifact defines callback timing, identity, cancellation/delivery semantics, threading, duplicate identity, supported versions and privacy-safe evidence fields.

Do not add reflection against unknown RoseChat internals, invent provider-owned API classes, scrape logs as a substitute callback, or claim support from an unverified stub.

## Next legitimate work

1. Verify PR #51's exact live head, checks, reviews, merge state, resulting `main` and branch cleanup.
2. Resume RoseChat only if the required supported provider contract is available.
3. Otherwise select one prerequisite-ready escalation-policy slice after fresh goals, blueprint, matrix and code reconciliation; versioned aliases and removed-ID readability are the current recommendation.
4. Do not begin the next feature inside PR #51.

## Production and release boundary

- LiteBans remains authoritative.
- Issue #43 remains open.
- No production deployment is authorized.
- No production database, credentials, production-derived backup or private player evidence may be accessed.
- No 168-hour production acceptance window is active.
- Merging dormant development code does not authorize production cutover.
- Staging controls are separate and must not be changed unless the selected work item explicitly requires them.

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
