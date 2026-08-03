# EnthusiaStaff workspace state

Last updated: 2026-08-02

This file is a concise routing record for the next AI agent. It must be verified against live GitHub and repository state before use.

## Repository

| Field | Recorded value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current `main` SHA | `4f7165adced48d98bce86730e89b92944afba063` at PR #52 start; live verify before acting |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java | 21 |
| Runtime | Paper-compatible backends, Velocity, MariaDB |

## Current work

| Field | Value |
| --- | --- |
| Work state | `VALIDATING` |
| Intended post-merge state | `IDLE` after PR #52 is live-verified |
| Active PR | `#52 — Add versioned reason aliases and removed-ID presentation` until live merge verification |
| Active branch | `feature/escalation-policy-aliases` until live merge and cleanup verification |
| Active work item | Explicit reason aliases plus readable-but-unresolvable removed reason metadata |
| Implementation state | Complete scoped code, focused tests, harsh-review fixes, documentation, requirements matrix and handoff frozen before exact-head validation |
| Known product blocker | Supported RoseChat private-message provider contract remains unavailable |
| Handoff | `ai-agents/reports/agent-handoffs/2026-08-02-pr52-reason-policy-compatibility.md` |
| Exact validation and merge evidence | Read PR #52 live |

If PR #52 is merged, no implementation PR remains active. The broader escalation requirement remains partial, and RoseChat remains externally blocked.

## Live reconciliation at work-item start

- PR #51 was merged with normal merge commit `4f7165adced48d98bce86730e89b92944afba063` from exact feature head `e8b70154dc07a38c4ee9f8e63a0c670ebf21102f`.
- PR #51 exact-head workflows `30776087520` (`Coverage`) and `30776087528` (`Validate Wiki`) succeeded.
- PR #51 had zero unresolved review threads.
- No pull request was open or in draft.
- Every pre-existing remote branch was `ahead_by: 0` relative to `main`; no unfinished work was displaced.
- The live highest migration remained `V14__punishment_history_and_exact_sanction_changes.sql`.
- The supported RoseChat callback/API contract remained unavailable and was not invented.

## PR #52 completed behavior

- Optional `aliases` map old stable reason IDs directly to one active canonical policy.
- Alias self-targets, chains, unknown targets, duplicate sources, malformed IDs and active/removed overlap are rejected.
- Aliases resolve current policy behavior but never enter the active selection list.
- New punishment plans evaluated through an alias commit the canonical reason ID, family, public reason and current configuration version.
- Optional `removed-reasons` entries provide stable ID, family and display metadata without a ladder.
- Removed IDs remain readable in historical/saved-review presentation but cannot resolve, enter selection or create a new punishment.
- Removed dynamic `cheating.polar.*` identifiers block template expansion.
- Version, active policies, aliases and removed metadata publish and restore as one atomic snapshot assembled from one repository read.
- Existing policy files without compatibility sections remain valid.
- The requirements matrix records this compatibility slice while retaining conservative `PARTIAL` status for the broader escalation and modular-configuration requirements.
- Existing stored cases, sanctions, ordinals, expirations, drafts, requests and audit records are not rewritten.
- No command, permission, provider contract, schema or migration changed.

## Separate harsh review

The complete PR was reviewed for active/historical separation, direct service behavior, dynamic Polar expansion, alias validation, removed-ID selection, startup degradation, reload rollback, concurrent snapshot consistency, immutable ownership, compatibility, stored-history preservation and documentation accuracy.

Three confirmed defects were fixed before the final freeze:

1. removed `cheating.polar.*` IDs could still resolve through dynamic template expansion;
2. reload snapshots could mix metadata because they were assembled from separate atomic reads;
3. the requirements matrix still described aliases and removed IDs as entirely unimplemented after the scoped code and tests supplied them.

Focused regression coverage was added for the runtime defects. The matrix now narrows remaining work to policy snapshots across ladder edits, serious-offense decay metadata, broader combined recommendations and full modular configuration. Any later CI, analyzer or review finding must be resolved before merge and exact-head validation repeated after tracked changes.

## Migration state

| Field | Value |
| --- | --- |
| Live highest migration at PR #52 start | `V14__punishment_history_and_exact_sanction_changes.sql` |
| Immutable migrations | `V1` through `V14` |
| Expected next number | `V15`, unless live repository state shows a newer legitimate migration |
| PR #52 schema result | No migration added or edited |
| Locked deployed checksums | V11 `-2005375055`; V12 `-1787751803`; V13 `1189066017` |

Never edit an existing migration. Verify the live migration directory before adding a new migration.

## Remaining escalation work

The alias/removed-ID compatibility slice is complete in PR #52, but the broader escalation requirement remains partial. Separate future work still includes:

- explicit policy-snapshot behavior across ladder edits;
- serious-offense decay metadata;
- wider combined-recommendation and acceptance coverage;
- the broader modular punishment and escalation configuration tree.

Do not silently expand PR #52 into those separate feature slices.

## RoseChat provider blocker

The supported private-message callback and privacy presentation boundary remains blocked until an accessible provider repository or published API artifact defines callback timing, identity, cancellation/delivery semantics, threading, duplicate identity, supported versions and privacy-safe evidence fields.

Do not add reflection against unknown RoseChat internals, invent provider-owned API classes, scrape logs as a substitute callback, or claim support from an unverified stub.

## Next legitimate work

1. Verify PR #52's exact live head, checks, reviews, merge state, resulting `main` and branch cleanup.
2. Resume RoseChat only if the required supported provider contract becomes available.
3. Otherwise select one prerequisite-ready escalation-policy slice after fresh goals, blueprint, matrix and code reconciliation; policy-snapshot behavior across ladder edits is the current likely candidate.
4. Do not begin the next feature inside PR #52.

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
