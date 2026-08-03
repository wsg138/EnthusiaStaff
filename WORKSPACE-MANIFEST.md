# EnthusiaStaff workspace manifest

Last updated: 2026-08-02 (America/Indiana/Indianapolis)

This manifest records repository, validation and authority boundaries for development coordination. Nothing here authorizes production deployment, production-data access, a LiteBans cutover or a change in punishment authority.

## Repository checkpoint

| Field | Current value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current verified `main` at PR #50 start | `d07cb888952fde575a4f8245571f8d1ebc858b63` |
| Latest merged product PR | PR #49 — modular report configuration and safe reload |
| Latest merged feature head | `1ad41be3eeca49370694916f386dda0484e3bfa3` |
| Latest merge commit | `d07cb888952fde575a4f8245571f8d1ebc858b63` |
| Coordination record | PR #50 — post-PR #49 reconciliation and RoseChat provider blocker; verify live closure |
| Migration boundary | V14 is latest; V1–V14 are immutable |
| Dormant default | Startup remains non-`ACTIVE`; merging development code does not activate authority |
| Production authority | **LiteBans remains authoritative** |

At PR #50 start there were no open pull requests. Every remaining remote branch was verified `ahead_by: 0` relative to `main`, so no unfinished work was displaced.

## Latest verified implementation evidence

PR #49 recorded exact feature-head evidence for `1ad41be3eeca49370694916f386dda0484e3bfa3`:

- Coverage workflow run `30774370125`, job `91566952409`: success;
- exact checkout assertion matched the frozen head;
- Temurin/OpenJDK 21.0.9;
- `./gradlew clean build test jacocoTestReport jacocoRootReport checkRuntimeJars --no-daemon`;
- successful full build and unit/MariaDB/Testcontainers suites;
- Flyway validated 14 migrations through V14;
- no migration was added or edited by PR #49;
- PR #49 had zero unresolved review threads before merge.

No pull-request-triggered workflow run was returned for merge commit `d07cb888952fde575a4f8245571f8d1ebc858b63`. Do not convert that absence into a success or failure claim.

## Current provider blocker

The next recorded report-system feature is a supported RoseChat private-message callback and privacy presentation boundary. It is blocked because the intended provider repository/API is not available:

- installed-repository search returned no `Enthusia-RoseChat` repository;
- public repository search under `wsg138` returned no repository with that name;
- no supported callback artifact, version or lifecycle contract is present in this workspace.

Do not invent an API, reflect against unknown implementation classes, copy provider-owned classes into EnthusiaStaff, or scrape logs as a substitute for a delivery callback.

Required input is an accessible supported provider repository or artifact defining callback timing, sender/recipient identity, cancellation and delivery semantics, threading, version coordinates, duplicate identity and privacy-safe evidence fields.

## Development merge gate

For normal implementation PRs, merge only when:

- the complete scoped behavior is implemented and harsh-reviewed;
- every confirmed defect and merge blocker is fixed;
- the final branch is synchronized appropriately with `main`;
- exact final-head Java 21 build, tests, migration checks, runtime-JAR inspection, static analysis, coverage and documentation checks pass as configured;
- no unresolved valid review thread remains;
- workspace state and an immutable handoff are included;
- exact evidence is recorded in the PR without changing the feature SHA;
- a normal merge commit is used.

For documentation-only PR #50, validate the exact final head with the repository's available documentation/wiki and static-analysis workflows, record checks that actually ran, and do not imply that unavailable implementation or production evidence exists.

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

1. Verify PR #50's live merge or blocker state, resulting `main`, unresolved-thread count and branch cleanup.
2. Obtain or publish the supported RoseChat provider contract before implementing the private-message callback.
3. If that external input remains unavailable, re-check live GitHub and select the highest-priority prerequisite-complete feature from the goals, development map and requirements matrix.
4. Keep one coherent item per PR and do not silently combine the blocked RoseChat work with another feature.
5. Complete issue #43 only after the plugin is closer to release and one exact release candidate is pinned.

## Release boundaries

- Never combine evidence from undeclared revisions into one release candidate.
- Keep production credentials, private JARs, databases, logs, evidence and runtime folders out of Git.
- Do not repair Flyway history, rewrite migration history, edit deployed migration bytes or delete legacy LiteBans data.
- Retain backups and legacy data through cutover; legacy removal is a later manual operation.
- Do not represent hosted tests, isolated staging, Pi validation or merge-ref-only validation as production acceptance.
- A merged pull request is a development checkpoint, not deployment or cutover authorization.
