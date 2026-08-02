# EnthusiaStaff workspace manifest

Last updated: 2026-08-02 (America/Indianapolis)

This manifest records repository, validation and authority boundaries for development coordination. Nothing here authorizes production deployment, production-data access, a LiteBans cutover or a change in punishment authority.

## Repository checkpoints

| Field | Current value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Previous major merged checkpoint | PR #27 |
| Current implementation work | PR #37 — Harden LiteBans cutover coordination |
| PR #37 scope | Durable operational state, authoritative-write fencing, migration/cutover coordination, transactional activation, restart recovery, duplicate-safe activation, emergency read-only persistence, migration checksum protection and guarded punishment/request/sanction/alt writes |
| Migration boundary | V11, V12 and V13 remain byte-compatible with locked checksums `-2005375055`, `-1787751803` and `1189066017`; scanner exclusions belong in `.codacy.yml` |
| Dormant default | Startup remains non-`ACTIVE`; automatic shadow scheduling is disabled unless explicitly configured; missing or invalid activation evidence fails closed |
| Production authority | **LiteBans remains authoritative** |

The final exact PR #37 source head, workflow run and job IDs, runtime JAR identities, coverage result, static-analysis result and merge commit are recorded in the pull request and issue #43 acceptance record. A skipped, cancelled, superseded, merge-ref-only or different-revision run is not exact-head evidence.

## Implementation merge gate

PR #37 may be marked ready and merged when:

- code review is complete and material review threads are resolved;
- the final exact head passes automated tests;
- the clean Java 21 build and runtime packaging pass;
- MariaDB/Testcontainers integration tests and migration checksum tests pass;
- runtime JAR inspection and provider API leak checks pass;
- static analysis and configured coverage upload pass;
- the feature remains dormant and fail closed by default;
- merging does not deploy an artifact or activate EnthusiaStaff authority.

Use a normal merge commit. Do not rebase, squash, force-push or push directly to `main`.

## Production cutover gate

Issue #43 blocks production activation and cutover authorization. It does not block merging dormant, reviewed implementation code.

Issue #43 must remain open until one exact release candidate completes:

- representative sanitized-backup restore, migration, rerun and checksum comparison;
- interrupted migration and restart recovery;
- an uninterrupted 168-hour shadow window with seven complete summaries;
- maintenance fencing and final incremental migration;
- ambiguous activation retry and duplicate safety;
- emergency freeze and restart persistence;
- rollback and idempotent reconciliation;
- Velocity/HUB/SMP, Java and Bedrock/Geyser acceptance;
- provider-present and provider-missing behavior;
- database, queue, dead-letter, process-kill, saturation and latency scenarios;
- operator review of backup, restore, credentials, permissions, prior artifacts and rollback staffing.

Before issue #43 is complete, do not deploy PR #37 as the production cutover release candidate, begin a real production shadow window, activate EnthusiaStaff authority, disable or remove LiteBans, perform the final production migration or authorize a live cutover.

## Related repositories

| Repository | Role | Current boundary |
| --- | --- | --- |
| `wsg138/EnthusiaStaff-Staging` | Reusable staging controls and evidence validation | Controls may be merged dormant; no workflow dispatch, production credentials, restore, migration, shadow or cutover testing is implied |
| `wsg138/enthusia-site` | Private punishment and appeal website | Auth/session/CSRF/media/rate-limit work and private staging remain separate |
| `wsg138/EnthusiaCurrency` | Economy moderation snapshots and plans | Provider implementation and cross-plugin staging remain separate |
| `wsg138/EnthusiaCommend` | Persistent reputation restrictions | Provider implementation and enforcement staging remain separate |
| `wsg138/EnthusiaAutoClicker` | Versioned bounded client evidence | Provider implementation and handshake staging remain separate |
| `wsg138/EnthusiaMarket` | Stall moderation and escrow-safe behavior | Provider implementation and transaction-compatible staging remain separate |
| Intended `wsg138/Enthusia-RoseChat` | Moderation/staff channels and evidence bridge | Repository/API remains missing or inaccessible; do not invent an integration |

Each related project remains an independent Git repository. Histories must not be flattened into EnthusiaStaff, and provider-owned API classes must not leak into the Paper or Velocity runtime JARs.

## Current development route

1. Finish PR #37 review and exact-head validation.
2. Merge PR #37 as dormant infrastructure without deploying it or changing authority.
3. Finish and merge reusable staging safety controls without dispatching a workflow or beginning acceptance.
4. Continue the next actual moderation or staff feature from the roadmap.
5. When the plugin is closer to release, pin one release candidate and complete issue #43.
6. Keep LiteBans authoritative until separate production cutover authorization exists.

## Release boundaries

- Never combine evidence from undeclared revisions into one release candidate.
- Keep production credentials, private JARs, databases, logs, evidence and runtime folders out of Git.
- Do not repair Flyway history, rewrite migration history, edit deployed V11–V13 bytes or delete legacy LiteBans data.
- Retain backups and legacy data through cutover; legacy removal is a later manual operation.
- Do not represent hosted tests, isolated staging, Pi validation or merge-ref Wiki validation as production acceptance.
- A merged pull request is a development checkpoint, not deployment or cutover authorization.
