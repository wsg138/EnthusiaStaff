# EnthusiaStaff workspace state

Last updated: 2026-08-02

This file is a concise routing record for the next AI agent. It must be verified against live GitHub and repository state before use.

## Repository

| Field | Recorded value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| `main` at PR #53 start | `49ee42c142ccd9e66b7b5fed2c30fc5b4094a052`; live verify before acting |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java | 21 |
| Runtime | Paper-compatible backends, Velocity, MariaDB |

## Current work

| Field | Value |
| --- | --- |
| Work state | `VALIDATING` |
| Intended post-merge state | `IDLE` after PR #53 is live-verified |
| Active PR | `#53 — Preserve escalation recommendation snapshots across ladder edits` until live merge verification |
| Active branch | `feature/escalation-policy-snapshots` until live merge and cleanup verification |
| Active work item | Preserve the exact configured recommendation and selected ladder step for new cases without rewriting historical cases |
| Implementation state | Scoped code, migration, focused tests, harsh-review fixes and documentation are being frozen before exact-head validation |
| Known product blocker | Supported RoseChat private-message provider contract remains unavailable |
| Handoff | `ai-agents/reports/agent-handoffs/2026-08-02-pr53-escalation-policy-snapshots.md` |
| Exact validation and merge evidence | Read PR #53 live |

If PR #53 is merged, no implementation PR remains active. The broader escalation requirement remains partial, and RoseChat remains externally blocked.

## Live reconciliation at work-item start

- PR #52 was already merged with normal merge commit `49ee42c142ccd9e66b7b5fed2c30fc5b4094a052` from exact feature head `ac08bcce7281caf6425393213c5ef4d48cd99b3e`.
- PR #52 exact-head workflows `30780118437` (`Coverage`) and `30780118455` (`Validate Wiki`) succeeded.
- PR #52 had zero unresolved review threads.
- No pull request was open or in draft.
- Every pre-existing remote branch was `ahead_by: 0` relative to `main`; no unfinished work was displaced.
- The live highest migration was `V14__punishment_history_and_exact_sanction_changes.sql`.
- The supported RoseChat callback/API contract remained unavailable and was not invented.

## PR #53 implemented behavior

- `V15__punishment_recommendation_snapshots.sql` adds nullable `selected_ordinal` and `recommended_sanctions_json` fields to `punishment_steps` without editing V1–V14.
- Every new policy-created case writes the selected configured ladder ordinal and exact recommended sanction specifications in the same transaction as the case and the sanctions actually applied.
- Raw ordinal, effective ordinal and selected ordinal remain separate because a finite ladder may clamp an out-of-range effective ordinal to its final configured step.
- The configured recommendation remains separate from an authorized applied override; actual sanction type, issue time and expiration remain authoritative and unchanged.
- V14 and older rows retain null snapshot fields. Case review reports those historical snapshots as unavailable instead of inventing a recommendation from applied sanctions.
- Corrupt stored recommendation JSON fails closed rather than silently presenting false history.
- `/case` history presentation shows configuration version, raw/effective ordinal, selected ordinal, step label and frozen recommendation before listing the actual sanctions.
- Current reason policies continue interpreting future recommendations against the current ladder; an out-of-range ordinal selects the current final step.
- Focused unit and MariaDB/Testcontainers coverage exercises ladder edits, final-step clamping, restart persistence, overrides, corrupt snapshots and V14-to-V15 upgrade compatibility.
- Existing case, sanction, ordinal, expiration, request, appeal and audit rows are not rewritten.

## Separate harsh review

The complete PR is being reviewed for migration safety, historical truthfulness, transaction atomicity, request-approval reuse, override separation, malformed JSON, restart behavior, finite-ladder clamping, staff presentation, provider boundaries and documentation accuracy.

Two confirmed defects were fixed before the final tracked-content freeze:

1. persisting only the effective ordinal left finite-ladder cases ambiguous because an out-of-range effective ordinal can select a lower final-step ordinal; V15, persistence, review presentation and tests now preserve `selected_ordinal` separately;
2. the first snapshot codec used generic Jackson record serialization, which did not follow the repository's established sanction snapshot schema and risked incompatible `Optional<Duration>` handling; the implementation now reuses the strict `PunishmentDraftSanctionCodec` format.

Regression coverage includes an effective ordinal of eight selecting stored step ordinal two, an applied duration override that remains separate from the seven-day recommendation, restart persistence, legacy null behavior and corrupt-snapshot failure. Any later CI, analyzer or review finding must be resolved before merge and exact-head validation repeated after tracked changes.

## Migration state

| Field | Value |
| --- | --- |
| Live highest migration at PR #53 start | `V14__punishment_history_and_exact_sanction_changes.sql` |
| PR #53 migration | `V15__punishment_recommendation_snapshots.sql` |
| Immutable migrations | `V1` through `V14` |
| Expected next number after PR #53 | `V16`, unless live repository state shows a newer legitimate migration |
| Locked deployed checksums | V11 `-2005375055`; V12 `-1787751803`; V13 `1189066017` |

Never edit an existing applied migration. Verify the live migration directory before adding a later migration.

## Remaining escalation work

The policy-snapshot slice is implemented in PR #53, but the broader escalation requirement remains partial. Separate future work still includes:

- serious-offense decay metadata and explicit non-decaying behavior;
- wider combined-recommendation and acceptance coverage;
- the broader modular punishment and escalation configuration tree;
- representative non-production staff usability and multi-runtime staging.

Do not silently expand PR #53 into those separate feature slices.

## RoseChat provider blocker

The supported private-message callback and privacy presentation boundary remains blocked until an accessible provider repository or published API artifact defines callback timing, identity, cancellation/delivery semantics, threading, duplicate identity, supported versions and privacy-safe evidence fields.

Do not add reflection against unknown RoseChat internals, invent provider-owned API classes, scrape logs as a substitute callback, or claim support from an unverified stub.

## Next legitimate work

1. Verify PR #53's exact live head, checks, reviews, merge state, resulting `main` and branch cleanup.
2. Resume RoseChat only if the required supported provider contract becomes available.
3. Otherwise select exactly one prerequisite-ready item after fresh goals, blueprint, matrix and code reconciliation; serious-offense decay metadata is the current likely escalation follow-up.
4. Do not begin the next feature inside PR #53.

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
