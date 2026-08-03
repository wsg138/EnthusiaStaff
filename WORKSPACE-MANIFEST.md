# EnthusiaStaff workspace manifest

Last updated: 2026-08-02 (America/Indiana/Indianapolis)

This manifest records repository, validation and authority boundaries for development coordination. Nothing here authorizes production deployment, production-data access, a LiteBans cutover or a change in punishment authority.

## Repository checkpoint

| Field | Current value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current verified `main` at PR #52 start | `4f7165adced48d98bce86730e89b92944afba063` |
| Latest merged product PR before current work | PR #51 — escalation clean-period decay correctness |
| Latest merged coordination PR | PR #50 — RoseChat provider blocker reconciliation |
| Active work | PR #52 — reason aliases and removed-ID presentation; verify live closure |
| Migration boundary | V14 is latest; V1–V14 are immutable |
| Dormant default | Startup remains non-`ACTIVE`; merging development code does not activate authority |
| Production authority | **LiteBans remains authoritative** |

At PR #52 start there were no open pull requests. Every pre-existing remote branch was verified `ahead_by: 0` relative to `main`, so no unfinished work was displaced.

## Current implementation checkpoint

PR #52 implements one bounded escalation-policy compatibility slice:

- old stable reason IDs can be declared as explicit aliases to one active canonical reason;
- aliases resolve current policy behavior but never become duplicate entries in the selectable reason catalog;
- newly committed punishment records use the canonical reason ID and current configuration version;
- removed reason IDs expose bounded display metadata without a ladder and cannot resolve for new punishment creation;
- removed IDs in the dynamic `cheating.polar.*` namespace block template expansion rather than becoming selectable accidentally;
- active policies, aliases, removed metadata and version are published and restored as one atomic reload snapshot;
- saved punishment review presentation distinguishes active, renamed, removed and unknown reason IDs;
- the requirements matrix now records the implemented compatibility slice without overstating the broader escalation requirement;
- no existing case, sanction, ordinal, expiration, draft, request or audit record is rewritten;
- no schema or Flyway migration changed.

Exact final-head validation, review and merge evidence must be read live from PR #52.

## Harsh-review checkpoint

The separate full-PR review confirmed and fixed three defects before the final tracked-content freeze:

1. removed `cheating.polar.*` identifiers could still resolve through dynamic template expansion;
2. reload snapshots were assembled through separate atomic reads and could theoretically mix metadata from concurrent versions;
3. the requirements matrix still described aliases and removed IDs as entirely unimplemented after the code and focused tests supplied that slice.

Regression tests cover the runtime boundaries, configuration rejection, atomic publication/restore, selection exclusion and canonical committed identity. The documentation fix preserves conservative `PARTIAL` status and leaves policy snapshots, serious-offense decay metadata, combined recommendations and broader modular configuration as separate future work.

## Prior verified evidence

PR #51 exact feature head `e8b70154dc07a38c4ee9f8e63a0c670ebf21102f` recorded:

- `Coverage` workflow run `30776087520`: success;
- `Validate Wiki` workflow run `30776087528`: success;
- zero unresolved review threads;
- normal merge commit `4f7165adced48d98bce86730e89b92944afba063`.

PR #49 exact feature head `1ad41be3eeca49370694916f386dda0484e3bfa3` recorded Java 21 clean build, unit and MariaDB/Testcontainers suites, Flyway through V14, runtime-JAR checks, coverage/static analysis and zero unresolved review threads in its live PR evidence.

Do not attribute those prior-head results to PR #52.

## Current provider blocker

The supported RoseChat private-message callback and privacy presentation boundary remains blocked because no accessible supported provider repository/API defines callback timing, sender/recipient identity, cancellation and delivery semantics, threading, duplicate identity, version coordinates and privacy-safe evidence fields.

Do not invent an API, reflect against unknown implementation classes, copy provider-owned classes into EnthusiaStaff, or scrape logs as a substitute for a delivery callback.

## Development merge gate

For PR #52 and later implementation PRs, merge only when:

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

1. Verify PR #52's live head, checks, review state, normal merge result, resulting `main` and branch cleanup.
2. Obtain or publish the supported RoseChat provider contract before implementing the private-message callback.
3. If that external input remains unavailable, select one prerequisite-ready escalation-policy slice after fresh goals, development-map, requirements-matrix and code reconciliation; explicit policy-snapshot behavior across ladder edits is the current likely candidate.
4. Keep one coherent item per PR and do not silently combine RoseChat, serious-offense decay metadata or broader modular configuration with PR #52.
5. Complete issue #43 only after the plugin is closer to release and one exact release candidate is pinned.

## Release boundaries

- Never combine evidence from undeclared revisions into one release candidate.
- Keep production credentials, private JARs, databases, logs, evidence and runtime folders out of Git.
- Do not repair Flyway history, rewrite migration history, edit deployed migration bytes or delete legacy LiteBans data.
- Retain backups and legacy data through cutover; legacy removal is a later manual operation.
- Do not represent hosted tests, isolated staging, Pi validation or merge-ref-only validation as production acceptance.
- A merged pull request is a development checkpoint, not deployment or cutover authorization.
