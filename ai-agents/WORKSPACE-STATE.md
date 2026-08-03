# EnthusiaStaff workspace state

Last updated: 2026-08-02

This file is a concise routing record for the next AI agent. It must be verified against live GitHub and repository state before use.

## Repository

| Field | Recorded value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current `main` SHA | `4f7165adced48d98bce86730e89b92944afba063` at this work-item start; live verify before acting |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java | 21 |
| Runtime | Paper-compatible backends, Velocity, MariaDB |

## Current work

| Field | Value |
| --- | --- |
| Work state | `PLANNING` |
| Intended post-merge state | `IDLE` after the current PR is live-verified |
| Active PR | Draft PR to be opened from `feature/escalation-policy-aliases` |
| Active branch | `feature/escalation-policy-aliases` |
| Active work item | Add explicit versioned reason aliases and readable-but-unselectable removed reason IDs |
| Implementation state | Live state reconciled; implementation and focused tests pending |
| Known product blocker | Supported RoseChat private-message provider contract remains unavailable |
| Handoff | Pending final immutable report under `ai-agents/reports/agent-handoffs/` |
| Exact validation and merge evidence | Read the active PR live after it is opened |

## Live reconciliation at work-item start

- PR #51 was merged with normal merge commit `4f7165adced48d98bce86730e89b92944afba063` from exact feature head `e8b70154dc07a38c4ee9f8e63a0c670ebf21102f`.
- PR #51 exact-head workflows `30776087520` (`Coverage`) and `30776087528` (`Validate Wiki`) succeeded.
- PR #51 had zero unresolved review threads.
- No pull request was open or in draft.
- Every pre-existing remote branch was `ahead_by: 0` relative to `main`; no unfinished work was displaced.
- The live highest migration remained `V14__punishment_history_and_exact_sanction_changes.sql`.
- The supported RoseChat callback/API contract remained unavailable and was not invented.

## Selected work

The goals and current development map require explicit aliases for renamed stable reason IDs and readable historical metadata for removed IDs. Current code supports only active IDs, so a rename makes stored identifiers unresolved and adding a removed ID back to the active catalog would make it selectable again.

This work will keep selection and historical presentation separate:

- active policies remain the only selectable policies;
- aliases resolve historical renamed IDs to one active canonical policy;
- removed IDs expose bounded display metadata without a punishment ladder;
- aliases and removed metadata are validated and atomically reloaded with the active catalog;
- no existing stored ordinal, sanction, case, draft, request or configuration snapshot will be rewritten.

## Migration state

| Field | Value |
| --- | --- |
| Live highest migration at work start | `V14__punishment_history_and_exact_sanction_changes.sql` |
| Immutable migrations | `V1` through `V14` |
| Expected next number | `V15`, unless live repository state shows a newer legitimate migration |
| Current work schema expectation | No migration required |
| Locked deployed checksums | V11 `-2005375055`; V12 `-1787751803`; V13 `1189066017` |

Never edit an existing migration. Verify the live migration directory before adding a new migration.

## RoseChat provider blocker

The supported private-message callback and privacy presentation boundary remains blocked until an accessible provider repository or published API artifact defines callback timing, identity, cancellation/delivery semantics, threading, duplicate identity, supported versions and privacy-safe evidence fields.

Do not add reflection against unknown RoseChat internals, invent provider-owned API classes, scrape logs as a substitute callback, or claim support from an unverified stub.

## Next legitimate work

1. Finish, harsh-review, freeze, validate and merge the current reason-alias/removed-ID compatibility slice if every gate passes.
2. Resume RoseChat only if the required supported provider contract becomes available.
3. Otherwise select one prerequisite-ready escalation-policy slice after fresh live reconciliation; policy snapshot behavior across ladder edits is the likely next escalation item.
4. Do not begin the next feature inside the current PR.

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
