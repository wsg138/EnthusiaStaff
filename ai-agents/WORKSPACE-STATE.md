# EnthusiaStaff workspace state

Last updated: 2026-08-03

This is a routing record, not a substitute for live GitHub reconciliation.

## Repository

| Field | Recorded value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| `main` at PR #54 start | `fc1e94bd7317d59a33d297a049a94fd2eb3f1c5e` |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java/runtime | Java 21; Paper-compatible backends, Velocity, MariaDB |

## Current work

| Field | Value |
| --- | --- |
| State | `FROZEN_PENDING_EXACT_HEAD_VALIDATION` after the final coordination commit |
| Active PR | `#54 — Preserve serious-offense decay eligibility in escalation history` |
| Active branch | `feature/serious-offense-decay-metadata` |
| Work item | Persist each new punishment step's configured decay eligibility and evaluate later history from that immutable value |
| Current handoff | `ai-agents/reports/agent-handoffs/2026-08-03-pr54-serious-offense-decay-metadata-validation-final.md` |
| Exact validation/merge evidence | Read PR #54 live; evidence comments must identify one unchanged exact head |
| External blocker | Supported RoseChat private-message provider contract remains unavailable |

## Start-state reconciliation

- PR #53 was already merged by normal merge commit `fc1e94bd7317d59a33d297a049a94fd2eb3f1c5e` from exact feature head `d766dfcd849c25df37df47962a0aab9bc6975304`.
- PR #53 Coverage `30783188447` and Validate Wiki `30783188443` succeeded, with zero unresolved review threads.
- No pull request was open or draft at PR #54 start.
- Every pre-existing non-main branch was `ahead_by: 0` relative to `main`; no unfinished branch was displaced.
- V15 was the live highest migration.
- No supported RoseChat callback/API contract was available.

## PR #54 behavior

- `DecayEligibility` distinguishes `ELIGIBLE`, `INELIGIBLE`, and legacy `UNKNOWN` history.
- V16 adds nullable `punishment_steps.decay_eligible`; V1–V15 remain immutable.
- New central-policy decisions carry the creating reason's explicit decay setting into the durable punishment transaction.
- Related-history reads use the stored value from each prior offense rather than the newly selected reason policy.
- The clean-period clock remains shared and starts after the latest contributing, non-overturned related offense ends.
- Each 90-day interval reduces only prior contributions stored as eligible.
- Explicitly non-decaying serious history remains effective under later eligible/minor policies.
- Eligible minor history can still decay under a later non-decaying/serious policy.
- Pre-V16 `NULL` values load as `UNKNOWN` and do not decay; no historical policy is inferred or backfilled.
- Recommendation snapshots, actual sanctions, lifecycle state, visibility and active sanction authority remain unchanged.

## Harsh-review findings

One confirmed gap was fixed during the separate full-PR review:

1. Persistence tests constructed `PunishmentPlan` directly and therefore did not prove the central `PunishmentService` copied the creating policy's decay setting into the committed plan. `PunishmentDecayMetadataServiceTest` now verifies both eligible and ineligible policies through the authoritative service path.

Focused coverage also includes 89/90/180-day boundaries, latest-related-offense reset, mixed eligibility, serious/minor policy changes, legacy unknown behavior, restart persistence, database constraint enforcement, V15-to-V16 upgrade preservation, and explicit default-catalog values.

## Migration boundary

| Field | Value |
| --- | --- |
| Highest migration at start | V15 |
| PR #54 migration | `V16__punishment_decay_eligibility_snapshots.sql` |
| Immutable history | V1–V15 |
| Next expected number | V17 unless live state is newer |
| Locked checksums | V11 `-2005375055`; V12 `-1787751803`; V13 `1189066017` |

Never edit an applied migration or use Flyway repair.

## Remaining work after PR #54

The broader escalation requirement remains partial. Separate future slices include wider combined-recommendation coverage, explicit family relationships, broader modular punishment/escalation configuration, decayed-history GUI presentation, and representative multi-runtime/staff acceptance. Do not expand PR #54 into them.

## Production boundary

LiteBans remains authoritative. Issue #43 remains open. No deployment, production access, authority activation, production shadow window or cutover is authorized by this development PR.

## Next route

1. Verify PR #54's exact live head, checks, reviews, normal merge result, resulting `main`, feature-head containment and branch cleanup.
2. Resume RoseChat only if a supported contract becomes available.
3. Otherwise reconcile live goals/code and select exactly one bounded follow-up from the remaining escalation or higher-priority correctness work.
4. Stop after PR #54; do not begin the next feature in it.
