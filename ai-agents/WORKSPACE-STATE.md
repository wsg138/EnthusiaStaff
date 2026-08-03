# EnthusiaStaff workspace state

Last updated: 2026-08-02

This is a routing record, not a substitute for live GitHub reconciliation.

## Repository

| Field | Recorded value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| `main` at PR #53 start | `49ee42c142ccd9e66b7b5fed2c30fc5b4094a052` |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java/runtime | Java 21; Paper-compatible backends, Velocity, MariaDB |

## Current work

| Field | Value |
| --- | --- |
| State | `VALIDATING` until PR #53 is live-verified |
| Active PR | `#53 — Preserve escalation recommendation snapshots across ladder edits` |
| Active branch | `feature/escalation-policy-snapshots` |
| Work item | Preserve exact configured recommendations and selected ladder ordinals without rewriting legacy history |
| Current handoff | `ai-agents/reports/agent-handoffs/2026-08-02-pr53-escalation-policy-snapshots-ci-final.md` |
| Exact validation/merge evidence | Read PR #53 live |
| External blocker | Supported RoseChat private-message provider contract remains unavailable |

## Start-state reconciliation

- PR #52 was already merged by normal merge commit `49ee42c142ccd9e66b7b5fed2c30fc5b4094a052` from exact head `ac08bcce7281caf6425393213c5ef4d48cd99b3e`.
- PR #52 Coverage `30780118437` and Validate Wiki `30780118455` succeeded; zero review threads remained.
- No PR was open or draft.
- Every pre-existing non-main branch was `ahead_by: 0` relative to `main`.
- V14 was the live highest migration.
- No supported RoseChat callback/API contract was available.

## PR #53 behavior

- V15 adds nullable `selected_ordinal` and `recommended_sanctions_json` to `punishment_steps`; V1–V14 remain immutable.
- A check constraint requires both snapshot fields to be null together for legacy rows or populated together for new rows.
- New policy-created cases persist raw, effective and selected ordinals, configuration version, selected label, contribution details and exact recommendation in the same transaction as the actual sanctions, audit and outboxes.
- Recommendation snapshots use the established strict sanction codec.
- Applied sanctions remain separate and authoritative for type, issue time, expiration and lifecycle.
- Legacy rows remain explicitly snapshot-unavailable; no recommendation is inferred.
- Domain and JDBC review paths reject malformed or incomplete snapshots.
- `/case` shows the frozen policy snapshot before the actual sanctions.
- Current policies continue using the current ladder; out-of-range ordinals select the current final step.

## Harsh review and CI findings

Five confirmed defects were fixed:

1. effective ordinal alone left finite-ladder clamping ambiguous, so selected ordinal is stored separately;
2. generic Jackson serialization did not use the established sanction schema, so the strict existing codec is reused;
3. independently nullable fields allowed incomplete snapshots, so database, domain and JDBC invariants enforce a complete pair;
4. an intermediate requirements-matrix rewrite omitted its tail, which was restored;
5. exact-head Coverage run `30782286201` on `7a01745d747aa52778d6ee723a2401de0ab9967d` found four invalid Crockford test fixture IDs containing `O`; the fixtures now use valid 16-digit identifiers. That failed run is not validation-success evidence.

Focused coverage includes ladder edits, final-step clamping, pair integrity, restart persistence, recommendation-versus-override separation, legacy null behavior, corrupt snapshots and V14-to-V15 upgrade preservation.

## Migration boundary

| Field | Value |
| --- | --- |
| Highest migration at start | V14 |
| PR #53 migration | `V15__punishment_recommendation_snapshots.sql` |
| Immutable history | V1–V14 |
| Next expected number | V16 unless live state is newer |
| Locked checksums | V11 `-2005375055`; V12 `-1787751803`; V13 `1189066017` |

Never edit an applied migration or use Flyway repair.

## Remaining work after PR #53

The broader escalation requirement remains partial. Separate future slices include serious-offense decay metadata, wider combined-recommendation coverage, broader modular configuration and representative multi-runtime/staff acceptance. Do not expand PR #53 into them.

## Production boundary

LiteBans remains authoritative. Issue #43 remains open. No deployment, production access, authority activation, production shadow window or cutover is authorized by this development PR.

## Next route

1. Verify PR #53's exact live head, checks, reviews, merge result, resulting `main` and branch cleanup.
2. Resume RoseChat only if a supported contract becomes available.
3. Otherwise reconcile live goals/code and select exactly one bounded follow-up; serious-offense decay metadata is the current likely candidate.
4. Stop after PR #53; do not begin the next feature in it.
