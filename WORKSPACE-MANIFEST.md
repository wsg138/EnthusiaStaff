# EnthusiaStaff workspace manifest

Last updated: 2026-08-02 (America/Indianapolis)

This manifest records repository, validation and authority boundaries for development coordination. Nothing here authorizes production deployment, production-data access, a LiteBans cutover or a change in punishment authority.

## Repository checkpoints

| Field | Current value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Previous major merged checkpoints | PR #27 and PR #37 |
| Current implementation work | PR #46 — punishment history and exact sanction-change lifecycle |
| Historical PR #37 scope | Durable operational state, authoritative-write fencing, migration/cutover coordination, transactional activation, restart recovery, duplicate-safe activation, emergency read-only persistence, migration checksum protection and guarded punishment/request/sanction/alt writes |
| PR #46 scope | Database-bounded player history, complete case detail, exact reduce/end/revoke/overturn actions, request/appeal linkage, append-only audit events, hierarchy/revision validation, safe reloadable presentation settings and concurrency/idempotency coverage |
| Migration boundary | V11, V12 and V13 remain byte-compatible with locked checksums `-2005375055`, `-1787751803` and `1189066017`; V14 is the only migration added by PR #46 |
| Dormant default | Startup remains non-`ACTIVE`; automatic shadow scheduling is disabled unless explicitly configured; missing or invalid activation evidence fails closed |
| Production authority | **LiteBans remains authoritative** |

The final exact PR #46 source head, workflow run and job IDs, runtime JAR identities, coverage result, static-analysis result and merge commit must be recorded in the pull request. PR #37 remains a historical merged checkpoint. A skipped, cancelled, superseded, merge-ref-only or different-revision run is not exact-head evidence.

## Implementation merge gate

PR #46 may be marked ready and merged when:

- code review is complete and material review threads are resolved;
- the final exact head passes automated tests;
- the clean Java 21 build and runtime packaging pass;
- MariaDB/Testcontainers integration tests and migration checksum tests pass;
- runtime JAR inspection and provider API leak checks pass;
- static analysis and configured coverage upload pass;
- history pagination remains database-bounded and audit history remains append-only;
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

Before issue #43 is complete, do not deploy any current implementation as the production cutover release candidate, begin a real production shadow window, activate EnthusiaStaff authority, disable or remove LiteBans, perform the final production migration or authorize a live cutover.

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

1. Finish PR #46 review and exact-head validation.
2. Merge PR #46 as a normal dormant feature without deploying it or changing authority.
3. Continue the next actual moderation or staff feature from the roadmap.
4. When the plugin is closer to release, pin one release candidate and complete issue #43.
5. Keep LiteBans authoritative until separate production cutover authorization exists.

## Release boundaries

- Never combine evidence from undeclared revisions into one release candidate.
- Keep production credentials, private JARs, databases, logs, evidence and runtime folders out of Git.
- Do not repair Flyway history, rewrite migration history, edit deployed V11–V13 bytes or delete legacy LiteBans data.
- Retain backups and legacy data through cutover; legacy removal is a later manual operation.
- Do not represent hosted tests, isolated staging, Pi validation or merge-ref Wiki validation as production acceptance.
- A merged pull request is a development checkpoint, not deployment or cutover authorization.

## Punishment history lifecycle feature

| Item | Location / rule |
| --- | --- |
| Player history | `domain/history`, `JdbcModerationHistoryStore`, `/history` |
| Case detail | `ModerationHistoryStore.caseDetail`, `/case [view] <case-id>` |
| Exact sanction changes | `ExactSanctionChangeRequest`, `JdbcExactSanctionMutationStore`, `/estaff sanction ...` |
| Schema | Flyway V14 only; V1–V13 remain locked |
| Authority | Existing durable operational-mode fence plus transaction-bound hierarchy/revision validation |
| Production boundary | No deployment, no authority activation, LiteBans remains authoritative, issue #43 remains open |
