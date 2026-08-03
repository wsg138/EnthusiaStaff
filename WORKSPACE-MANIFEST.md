# EnthusiaStaff workspace manifest

Last updated: 2026-08-02 (America/Indiana/Indianapolis)

This manifest records repository, validation and authority boundaries for development coordination. Nothing here authorizes production deployment, production-data access, a LiteBans cutover or a change in punishment authority.

## Repository checkpoint

| Field | Current value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| `main` at PR #53 start | `49ee42c142ccd9e66b7b5fed2c30fc5b4094a052` |
| Latest merged product PR before current work | PR #52 — reason aliases and removed-ID presentation |
| Latest merged coordination PR | PR #50 — RoseChat provider blocker reconciliation |
| Active work | PR #53 — escalation recommendation snapshots across ladder edits; verify live closure |
| Migration boundary | PR #53 adds V15; V1–V14 remain immutable |
| Dormant default | Startup remains non-`ACTIVE`; merging development code does not activate authority |
| Production authority | **LiteBans remains authoritative** |

At PR #53 start there were no open pull requests. Every pre-existing remote branch was verified `ahead_by: 0` relative to `main`, so no unfinished work was displaced.

## Current implementation checkpoint

PR #53 implements one bounded escalation-policy compatibility slice:

- new policy-created cases persist the exact configured recommendation in `punishment_steps` independently from the sanctions actually applied;
- raw ordinal, effective ordinal and selected ladder ordinal are preserved separately so finite-ladder clamping remains historically unambiguous;
- configuration version, selected step label and recommendation sanctions survive restart and later ladder edits;
- an authorized override does not replace the stored recommendation, while actual sanction type and expiration remain authoritative;
- legacy V14 and older cases retain null snapshot fields and are shown as unavailable rather than reconstructed from potentially overridden sanctions;
- malformed stored snapshots fail closed;
- `/case` history shows the frozen policy snapshot before the actual sanction list;
- current policies still interpret future recommendations using the current ladder, with out-of-range ordinals selecting the current final step;
- V15 is append-only and V1–V14 are not edited;
- no existing case, sanction, request, appeal, expiration or audit row is rewritten.

Exact final-head validation, review and merge evidence must be read live from PR #53.

## Harsh-review checkpoint

The separate full-PR review confirmed and fixed two defects before the final tracked-content freeze:

1. effective ordinal alone could not identify the selected recommendation when a finite ladder clamped an out-of-range value to its last configured step, so V15 and the review model now preserve `selected_ordinal` separately;
2. generic Jackson serialization did not follow the established sanction snapshot schema and risked incompatible optional-duration handling, so the implementation now reuses the strict `PunishmentDraftSanctionCodec` format.

Regression coverage proves out-of-range clamping, restart persistence, an applied duration override distinct from the recommendation, legacy null behavior, corrupt-snapshot failure and V14-to-V15 upgrade preservation. The broader escalation requirement remains conservative and separate.

## Prior verified evidence

PR #52 exact feature head `ac08bcce7281caf6425393213c5ef4d48cd99b3e` recorded:

- `Coverage` workflow run `30780118437`: success;
- `Validate Wiki` workflow run `30780118455`: success;
- zero unresolved review threads;
- normal merge commit `49ee42c142ccd9e66b7b5fed2c30fc5b4094a052`.

PR #49 exact feature head `1ad41be3eeca49370694916f386dda0484e3bfa3` recorded Java 21 clean build, unit and MariaDB/Testcontainers suites, Flyway through V14, runtime-JAR checks, coverage/static analysis and zero unresolved review threads in its live PR evidence.

Do not attribute those prior-head results to PR #53.

## Current provider blocker

The supported RoseChat private-message callback and privacy presentation boundary remains blocked because no accessible supported provider repository/API defines callback timing, sender/recipient identity, cancellation and delivery semantics, threading, duplicate identity, version coordinates and privacy-safe evidence fields.

Do not invent an API, reflect against unknown implementation classes, copy provider-owned classes into EnthusiaStaff, or scrape logs as a substitute for a delivery callback.

## Development merge gate

For PR #53 and later implementation PRs, merge only when:

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

1. Verify PR #53's live head, checks, review state, normal merge result, resulting `main` and branch cleanup.
2. Obtain or publish the supported RoseChat provider contract before implementing the private-message callback.
3. If that external input remains unavailable, select one prerequisite-ready escalation-policy slice after fresh goals, development-map, requirements-matrix and code reconciliation; serious-offense decay metadata is the current likely candidate.
4. Keep one coherent item per PR and do not silently combine RoseChat, broader modular configuration or the next escalation slice with PR #53.
5. Complete issue #43 only after the plugin is closer to release and one exact release candidate is pinned.

## Release boundaries

- Never combine evidence from undeclared revisions into one release candidate.
- Keep production credentials, private JARs, databases, logs, evidence and runtime folders out of Git.
- Do not repair Flyway history, rewrite migration history, edit deployed migration bytes or delete legacy LiteBans data.
- Retain backups and legacy data through cutover; legacy removal is a later manual operation.
- Do not represent hosted tests, isolated staging, Pi validation or merge-ref-only validation as production acceptance.
- A merged pull request is a development checkpoint, not deployment or cutover authorization.
