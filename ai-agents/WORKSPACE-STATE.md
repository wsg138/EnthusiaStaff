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
| State | `IDLE — PR #54 requires live merge verification` |
| Pull request to verify | `#54 — Preserve serious-offense decay eligibility in escalation history` |
| Feature branch to verify | `feature/serious-offense-decay-metadata` |
| Completed work item | Persist each new punishment step's configured decay eligibility and evaluate later history from that immutable value |
| Current handoff | `ai-agents/reports/agent-handoffs/2026-08-03-pr54-serious-offense-decay-metadata.md` |
| Exact validation/merge evidence | Read PR #54 live; exact SHA, Coverage, Wiki, Pi, Codacy, artifacts, reviews and merge evidence belong in PR metadata |
| Pi staging repository | `https://github.com/wsg138/EnthusiaStaff-Staging` |
| Known Pi failure evidence | Public wrapper run `30794945133`; failure artifact `8848768264`; private staging run `30794966760` |
| Staging-control correction | Private PR #7 merged normally as `635423c64a2254d137002fce32652eb20770db34` |
| External blocker | Supported RoseChat private-message provider contract remains unavailable; track it through a focused blocker issue and the normal handoff rather than issue #43 |

## Start-state reconciliation

- PR #53 was already merged by normal merge commit `fc1e94bd7317d59a33d297a049a94fd2eb3f1c5e` from exact feature head `d766dfcd849c25df37df47962a0aab9bc6975304`.
- PR #53 Coverage `30783188447` and Validate Wiki `30783188443` succeeded, with zero unresolved review threads.
- No pull request was open or draft at PR #54 start.
- Every pre-existing non-main branch was `ahead_by: 0` relative to `main`; no unfinished branch was displaced.
- V15 was the live highest migration.
- No supported RoseChat callback/API contract was available.

## Pi evidence routing and failure history

The public Pi wrapper uses `pull_request_target`, so commit-scoped workflow listings may show Coverage and Wiki while omitting the Pi wrapper. Future agents must inspect the public wrapper run and the private staging repository directly rather than treating an omitted commit-scoped result as absent or non-applicable.

Current durable navigation:

- public wrapper run: `https://github.com/wsg138/EnthusiaStaff/actions/runs/30794945133`;
- public failure artifact: `https://github.com/wsg138/EnthusiaStaff/actions/runs/30794945133/artifacts/8848768264`;
- private staging repository: `https://github.com/wsg138/EnthusiaStaff-Staging`;
- dispatched private staging run: `https://github.com/wsg138/EnthusiaStaff-Staging/actions/runs/30794966760`.

That exact-head run built PR #54 successfully, then the Lincoln-PI-4 boot job failed because the database used by the nominally disposable profile retained an earlier mutable PR-head copy of V16. Flyway correctly rejected applied checksum `-1973590227` versus final exact-head checksum `-782756421`.

The failure was a staging-isolation defect, not a reason to edit V16 or weaken Flyway validation. `EnthusiaStaff-Staging` PR #7 merged normally as `635423c64a2254d137002fce32652eb20770db34`. The corrected harness clears only a guarded dedicated staging/test/Pi database before and after each two-boot run, preserves it between the two boots for restart testing, and never calls Flyway `repair` or selectively rewrites migration history.

For later failures, inspect the wrapper artifact first; it contains the private run ID, job state, sanitized failed-job logs and Pi evidence. Then inspect the private staging run directly with the GitHub connector.

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

The complete confirmed finding list is maintained in the canonical PR #54 handoff. Two especially important findings were:

1. Persistence tests constructed `PunishmentPlan` directly and therefore did not prove the central `PunishmentService` copied the creating policy's decay setting into the committed plan. `PunishmentDecayMetadataServiceTest` now verifies both eligible and ineligible policies through the authoritative service path.
2. The Pi profile deleted its Paper files but reused MariaDB migration history across mutable PR heads. The private staging harness now resets only the guarded disposable database before and after a run while preserving the database across the two restart-test cycles.

Focused coverage also includes 89/90/180-day boundaries, latest-related-offense reset, mixed eligibility, serious/minor policy changes, legacy unknown behavior, restart persistence, database constraint enforcement, V15-to-V16 upgrade preservation, and explicit default-catalog values.

## Owner priorities and selection guardrails

Current owner priority order:

1. Staff mode, vanish, and freeze.
2. Report notification completion.
3. Escalation-policy completion.

When prerequisites are comparable, prefer owner-prioritized staff/player-visible work over another internal policy or infrastructure slice.

Do not perform more than two consecutive internal infrastructure or policy PRs unless the work:

- fixes a confirmed correctness, security, concurrency, migration, or data-integrity defect;
- directly unblocks a higher-priority feature; or
- is explicitly approved by the owner.

Direct owner instructions in the current conversation override this recorded order.

PR #54 is allowed to finish because it fixes a confirmed historical-correctness and data-integrity defect. Do not start another escalation-policy slice immediately after PR #54.

## Blocked-work routing

- Issue #43 is specifically the LiteBans production-cutover acceptance issue and remains open.
- Issue #43 is not the general bug-report or blocker queue.
- External blockers such as an unavailable provider API should normally be tracked in a focused issue and the normal handoff.
- Do not open a standalone documentation PR solely to record a blocker unless routing would otherwise be materially incorrect or unsafe.

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

The broader escalation requirement remains partial, but it is third in the current owner priority order. Separate future slices include wider combined-recommendation coverage, explicit family relationships, broader modular punishment/escalation configuration, decayed-history GUI presentation, and representative multi-runtime/staff acceptance.

## Production boundary

LiteBans remains authoritative. Issue #43 remains open specifically for production-cutover acceptance. No deployment, production access, authority activation, production shadow window or cutover is authorized by PR #54.

## Next route

1. Verify PR #54's exact live head, terminal checks, review state, normal merge result, resulting `main`, feature-head containment and automatic branch cleanup.
2. For Pi, inspect the public wrapper, its failure/success artifact, and the dispatched private `EnthusiaStaff-Staging` run directly.
3. When no newer direct owner instruction supersedes this record, select one bounded staff mode, vanish, or freeze work item after fresh live reconciliation.
4. Then prioritize report notification completion; track unavailable provider APIs in focused blocker issues and the normal handoff.
5. Treat escalation-policy completion as the third owner priority and do not begin another escalation slice immediately after PR #54.
6. Stop after verifying PR #54; do not begin the next feature in the same session.
