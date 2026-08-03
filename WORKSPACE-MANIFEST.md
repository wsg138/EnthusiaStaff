# EnthusiaStaff workspace manifest

Last updated: 2026-08-02 (America/Indiana/Indianapolis)

This manifest records repository, validation and authority boundaries for development coordination. Nothing here authorizes production deployment, production-data access, a LiteBans cutover or a change in punishment authority.

## Repository checkpoint

| Field | Current value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current verified `main` at PR #51 start | `ead1b5a02d3e8dc71eeb5ceb3c9505da1843e727` |
| Latest merged product PR before current work | PR #49 — modular report configuration and safe reload |
| Latest merged coordination PR | PR #50 — RoseChat provider blocker reconciliation |
| Active work | PR #51 — escalation clean-period decay correctness fix; verify live closure |
| Migration boundary | V14 is latest; V1–V14 are immutable |
| Dormant default | Startup remains non-`ACTIVE`; merging development code does not activate authority |
| Production authority | **LiteBans remains authoritative** |

At PR #51 start there were no open pull requests. Every pre-existing remote branch was verified `ahead_by: 0` relative to `main`, so no unfinished work was displaced.

## Current implementation checkpoint

PR #51 fixes one bounded escalation defect:

- decay intervals are measured from the newest contributing, non-overturned related offense;
- a recent related reoffense resets the clean-period clock for older related history;
- 89-day, 90-day and 180-day boundaries are covered;
- shared clean-period decay and non-decaying policy behavior are covered;
- severity weighting, recency, filtering and finite-ladder clamping are preserved;
- no command, permission, configuration, provider contract, schema or migration changes.

Exact final-head validation, review and merge evidence must be read live from PR #51.

## Prior verified evidence

PR #50 exact feature head `e5d72a9809b7aabec39e95705e6e0a82f4a3f663` recorded:

- `Validate Wiki` workflow run `30775061520`: success;
- `Coverage` workflow run `30775061525`: success;
- zero unresolved review threads;
- normal merge commit `ead1b5a02d3e8dc71eeb5ceb3c9505da1843e727`.

PR #49 exact feature head `1ad41be3eeca49370694916f386dda0484e3bfa3` recorded Java 21 clean build, unit and MariaDB/Testcontainers suites, Flyway through V14, runtime-JAR checks, coverage/static analysis and zero unresolved review threads in its live PR evidence.

Do not attribute those prior-head results to PR #51.

## Current provider blocker

The supported RoseChat private-message callback and privacy presentation boundary remains blocked because no accessible supported provider repository/API defines callback timing, sender/recipient identity, cancellation and delivery semantics, threading, duplicate identity, version coordinates and privacy-safe evidence fields.

Do not invent an API, reflect against unknown implementation classes, copy provider-owned classes into EnthusiaStaff, or scrape logs as a substitute for a delivery callback.

## Development merge gate

For PR #51 and later implementation PRs, merge only when:

- the complete scoped behavior is implemented and harsh-reviewed;
- every confirmed defect and merge blocker is fixed;
- the final branch is synchronized appropriately with `main`;
- exact final-head Java 21 build, tests, migration checks, runtime-JAR inspection, static analysis, coverage and documentation checks pass as configured;
- no unresolved valid review thread remains;
- workspace state and an immutable handoff are included;
- exact evidence is recorded in the PR without changing the feature SHA;
- a normal merge commit is used.

## Production cutover gate

Issue #43 remains the separate production activation and cutover gate. It must stay open until one exact release candidate completes the required sanitized migration, interruption, shadow-window, maintenance, activation, rollback, topology, provider, saturation and operator acceptance record.

Before issue #43 is complete, do not:

- deploy the current implementation as the production cutover candidate;
- begin a real production shadow window;
- activate EnthusiaStaff punishment authority;
- disable or remove LiteBans;
- perform the final production migration;
- authorize a live cutover.

## Related repositories

| Repository | Role | Current boundary |
| --- | --- | --- |
| `wsg138/EnthusiaStaff-Staging` | Reusable staging controls and evidence validation | Separate repository; no workflow dispatch or production testing is implied |
| `wsg138/enthusia-site` | Private punishment and appeal website | Auth/session/CSRF/media/rate-limit work and private staging remain separate |
| `wsg138/EnthusiaCurrency` | Economy moderation snapshots and plans | Provider implementation and cross-plugin staging remain separate |
| `wsg138/EnthusiaCommend` | Persistent reputation restrictions | Provider implementation and enforcement staging remain separate |
| `wsg138/EnthusiaAutoClicker` | Versioned bounded client evidence | Provider implementation and handshake staging remain separate |
| `wsg138/EnthusiaMarket` | Stall moderation and escrow-safe behavior | Provider implementation and transaction-compatible staging remain separate |
| Intended `wsg138/Enthusia-RoseChat` | Private-message and moderation/staff-channel provider bridge | Repository/API is unavailable; implementation is blocked and must not be invented |

Each related project remains an independent Git repository. Histories must not be flattened into EnthusiaStaff, and provider-owned API classes must not leak into Paper or Velocity runtime JARs.

## Current development route

1. Verify PR #51's live head, checks, review state, normal merge result, resulting `main` and branch cleanup.
2. Obtain or publish the supported RoseChat provider contract before implementing the private-message callback.
3. If that external input remains unavailable, select one prerequisite-ready escalation-policy slice after fresh goals, development-map, requirements-matrix and code reconciliation; versioned aliases and removed-ID readability are the current recommendation.
4. Keep one coherent item per PR and do not silently combine RoseChat or broader escalation work with PR #51.
5. Complete issue #43 only after the plugin is closer to release and one exact release candidate is pinned.

## Release boundaries

- Never combine evidence from undeclared revisions into one release candidate.
- Keep production credentials, private JARs, databases, logs, evidence and runtime folders out of Git.
- Do not repair Flyway history, rewrite migration history, edit deployed migration bytes or delete legacy LiteBans data.
- Retain backups and legacy data through cutover; legacy removal is a later manual operation.
- Do not represent hosted tests, isolated staging, Pi validation or merge-ref-only validation as production acceptance.
- A merged pull request is a development checkpoint, not deployment or cutover authorization.
