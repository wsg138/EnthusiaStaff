# Punishment history and sanction lifecycle implementation plan

Starting point: `main` at `e5823e109a59dc46de579d0e990ffd4954d7667e`.

## Baseline

- Java 21 Gradle multi-module build with Paper and Velocity runtime JARs.
- Default unreleased project version: `0.1.0-SNAPSHOT`.
- Existing case-scoped sanction mutation service/store with row locks, idempotency keys, append-only sanction/audit events, and operational-mode fencing.
- Existing punishment-request, website appeal, historical player identity, Paper command, authorization, configuration reload, and MariaDB/Testcontainers foundations.
- V11–V13 are checksum-locked and must remain byte-identical.

## Confirmed gaps

- No bounded `/history <player> [page]` command or unified moderation timeline.
- Existing case detail does not show complete request, appeal, sanction, and mutation history.
- Sanction changes target a case and may affect multiple sanctions rather than one exact sanction ID.
- No command-level appeal-linked overturn lifecycle.
- Mutation results do not expose previous/resulting sanction state or expiration.
- No history-specific pagination/presentation configuration.
- Existing permissions do not provide the requested independent history, sensitive-history, and per-mutation controls.
- Hierarchy/target authorization is not revalidated with the locked sanction inside the transaction.

## Sequential implementation

1. Add history domain records and a database-paginated history repository with deterministic newest-first ordering.
2. Extend exact-sanction mutation requests/results with optional appeal/request linkage and detailed before/after state.
3. Harden the JDBC mutation transaction with sanction row locks, service-boundary authorization/hierarchy checks, terminal conflict rules, and duplicate-safe event identities while preserving the durable authoritative-write fence.
4. Add Flyway V14 schema/index support only; do not edit V1–V13.
5. Implement `/history`, extend the established `/case` command for detail output, and add `/estaff sanction reduce|end|revoke|overturn` commands using asynchronous database work and plain Bedrock-readable output.
6. Add separate permissions and validated reloadable history/reason settings.
7. Add unit and MariaDB/Testcontainers coverage for identity resolution, pagination, details, lifecycle semantics, concurrency, idempotency, restart persistence, authority fencing, and reload safety.
8. Update command, permission, configuration, database, roadmap, manifest, blueprint, wiki, and requirements documentation.
9. Review the complete diff and migrations, resolve valid review feedback, validate the exact final head through all configured checks, and merge with a normal merge commit only when clean.

## Current status

Implementation, focused unit tests, MariaDB/Testcontainers integration tests, configuration/reload tests, and project documentation are complete on the feature branch. The first full review pass identified transaction, concurrency, reload-validation, identity-resolution, and documentation defects; those fixes are now committed. Remaining work is exact-head CI, review-thread resolution, artifact verification, and the normal merge gate.

## Boundaries

No deployment, production database/player data, authority activation, LiteBans removal, issue #43 acceptance, Flyway repair, migration-history rewrite, direct main push, rebase, squash, force-push, or unrelated feature work.
