# EnthusiaStaff workspace manifest

Last updated: 2026-08-03 (America/Indiana/Indianapolis)

This manifest records development coordination and authority boundaries. It does not authorize deployment, production-data access, LiteBans cutover or a change in punishment authority.

## Repository checkpoint

| Field | Current value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| `main` at PR #54 start | `fc1e94bd7317d59a33d297a049a94fd2eb3f1c5e` |
| Latest merged product PR before current work | PR #53 — escalation recommendation snapshots |
| Active work | PR #54 — serious-offense decay eligibility metadata |
| Current handoff | `ai-agents/reports/agent-handoffs/2026-08-03-pr54-serious-offense-decay-metadata-validation-final.md` |
| Migration boundary | PR #54 adds V16; V1–V15 remain immutable |
| Dormant default | Startup remains non-`ACTIVE` |
| Production authority | **LiteBans remains authoritative** |

At PR #54 start there were no open pull requests and every pre-existing non-main branch was `ahead_by: 0` relative to `main`.

## Implementation checkpoint

PR #54 implements one bounded escalation-history compatibility slice:

- `DecayEligibility` records `ELIGIBLE`, `INELIGIBLE`, or legacy `UNKNOWN` behavior on each prior offense;
- the central escalation decision captures the creating reason policy's explicit decay setting;
- V16 stores nullable `decay_eligible` in `punishment_steps` in the same transaction as the case, recommendation snapshot, applied sanctions, audit and outboxes;
- related-history loading evaluates each prior offense from its stored value instead of reinterpreting it through a later reason policy;
- the latest contributing, non-overturned related offense still resets the shared clean-period clock;
- each 90-day interval reduces only contributions stored as eligible;
- explicitly non-decaying serious history does not decay under a later minor policy;
- eligible minor history still decays under a later non-decaying policy;
- pre-V16 rows remain nullable/`UNKNOWN` and are not inferred or rewritten;
- V1–V15 and existing case, sanction, request, appeal, expiration and audit history remain unchanged.

Exact final-head validation, review and merge evidence belong in PR #54 live metadata.

## Harsh-review checkpoint

The separate full-PR review found and fixed one confirmed coverage defect: direct persistence fixtures did not prove that `PunishmentService` copied the creating policy's decay setting into the committed plan. A focused service test now verifies both eligible and ineligible policies through the authoritative application path.

Regression coverage targets clean-period boundaries, reset behavior, mixed eligibility, later-policy changes, legacy unknown rows, restart persistence, database constraints, V15-to-V16 upgrade preservation and default policy configuration values.

## Prior verified evidence

PR #53 exact head `d766dfcd849c25df37df47962a0aab9bc6975304` passed Coverage `30783188447` and Validate Wiki `30783188443`, had zero unresolved review threads, and merged normally as `fc1e94bd7317d59a33d297a049a94fd2eb3f1c5e`. Do not attribute prior-head evidence to PR #54.

## Provider blocker

The supported RoseChat private-message callback and privacy presentation boundary remains blocked because no accessible supported provider repository/API defines callback timing, identity, cancellation/delivery semantics, threading, duplicate behavior, versions and privacy-safe evidence fields. Do not invent an API, reflect against unknown classes, copy provider-owned classes or scrape logs as a substitute callback.

## Development merge gate

Merge PR #54 only after one unchanged exact head is synchronized with `main` and passes Java 21 build/tests, MariaDB/Testcontainers clean-install and V15-to-V16 upgrade checks, migration checksums, runtime-JAR inspection, aggregate coverage, configured static analysis, wiki validation and all review gates. Zero unresolved valid threads must remain. Record exact evidence in the PR without changing the feature SHA and use a normal merge commit.

## Production cutover gate

Issue #43 remains open. Before it is complete, do not deploy a production cutover candidate, begin a real shadow window, activate EnthusiaStaff authority, disable/remove LiteBans, perform final production migration or authorize live cutover.

## Related repositories

Provider and website repositories remain independent. Their histories must not be flattened into EnthusiaStaff, and provider API classes must not leak into Paper or Velocity runtime JARs. The intended RoseChat provider repository/API remains unavailable.

## Current route

1. Verify PR #54's exact live head, checks, reviews, normal merge result, resulting `main` and branch cleanup.
2. Resume RoseChat only after a supported contract exists.
3. Otherwise select exactly one prerequisite-ready follow-up after fresh reconciliation; wider combined recommendations, explicit family relationships and modular escalation configuration remain separate.
4. Stop after PR #54 and do not combine the next slice with it.

## Release boundaries

- Never combine evidence from different revisions.
- Keep credentials, private JARs, databases, logs and evidence out of Git.
- Never repair Flyway history or edit deployed migration bytes.
- Do not represent hosted tests or isolated staging as production acceptance.
- A merged pull request is a development checkpoint, not deployment authorization.
