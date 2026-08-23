# Workspace state

Last updated: 2026-08-23

Live GitHub overrides stale records. Detailed package evidence remains in the registries, selected package record, canonical handoff, and PR verification ledgers.

## Current routing

| Field | Value |
| --- | --- |
| Discord program active package | `ES-D02 — Discord persistence and migration schema` is `ACTIVE` on `package/es-d02-discord-persistence`, starting from `main` `1a8b70755fa48f780c228c47943b8afaef746f36`. |
| Discord dependency state | `ES-D01` is merged/`COMPLETE`; `ES-D03` remains blocked until D02 completes. No second Discord package is active in this worker. |
| D02 migration ownership | Current `main` contains Flyway migrations through V18. D02 owns the next available V19 only while that remains true; live `main` must be rechecked before any collision-sensitive merge. |
| Collision reconciliation | The only open Staff PR at claim is parked `ES-X03` PR #139. No `package/es-d*` continuation exists. `package/codacy-website-appeal-transitions` has no commits ahead of current `main`. Website/competition paths are excluded from D02. |
| Independently parked package | `ES-X03 — EnthusiaMarket destructive provider` remains `BLOCKED` / `PARKED_BLOCKED` on owner-controlled runtime-host readiness. Its frozen PR #139 remains untouched. |
| Production boundary | No production Discord configuration, bot token, player/private data, deployment, production migration, LiteBans authority change, or cutover is authorized. Issue #43 remains open and LiteBans remains authoritative. |
| Current handoff | `ai-agents/reports/package-handoffs/2026-08-23-es-d02-discord-persistence-active.md` |

## D02 execution state

The package is implementing the D01 subject/identity contract in the existing MariaDB using forward-only schema, transactional/revision-safe repositories, safe legacy UUID backfill, and bounded durable maintenance/reconciliation state. The implementation must pass focused MariaDB/Testcontainers clean-install and upgrade validation plus the repository exact-head Java 21/static/review gates before normal merge.

## Independent ES-X03 blocker

ES-X03 remains parked until the owner-controlled validation host can complete trusted Paper readiness within the existing window. D02 must not alter that package, its provider mirror, or its frozen validation evidence.
