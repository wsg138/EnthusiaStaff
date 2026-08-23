# Workspace state

Last updated: 2026-08-23

Live GitHub overrides stale records. Detailed package evidence remains in the registries, selected package record, canonical handoff, and PR verification ledgers.

## Current routing

| Field | Value |
| --- | --- |
| Discord program latest completion | `ES-D02 — Discord persistence and migration schema` is `COMPLETE` through PR #148. Frozen product head: `9f0d9bb44ab6929bec3bf652e0c9b3467104b423`. |
| Discord dependency state | `ES-D01` and `ES-D02` are `COMPLETE`. `ES-D03 — Authorization and cross-platform policy` is newly dependency-complete and `READY` for a fresh worker; it is not started in the D02 run. |
| D02 migration state | Final live collision check found `main` still through V18; D02 owns forward migration `V19__discord_moderation_persistence.sql`. Exact-head clean-install/upgrade MariaDB/Testcontainers validation passed. |
| Collision reconciliation | Immediately before terminal publication, D02 was seven commits ahead / zero behind unchanged `main` `1a8b70755fa48f780c228c47943b8afaef746f36`. The only other open Staff PR was independently parked ES-X03 PR #139. D02 changes no website or competition path. |
| Independently parked package | `ES-X03 — EnthusiaMarket destructive provider` remains `BLOCKED` / `PARKED_BLOCKED` on its independent validation condition and was not modified by D02. |
| Production boundary | No production Discord configuration, bot token, player/private production data, deployment, production migration, LiteBans authority change, or cutover was authorized or performed. Issue #43 remains open and LiteBans remains authoritative. |
| Current handoff | `ai-agents/reports/package-handoffs/2026-08-23-es-d02-discord-persistence-complete.md` |

## D02 terminal evidence

Frozen product head `9f0d9bb44ab6929bec3bf652e0c9b3467104b423` passed Coverage/full Java 21 workflow `32661521865` / job `97248320623` with MariaDB/Testcontainers integration tests, runtime-JAR inspection, aggregate JaCoCo and successful Codacy coverage finalization. Coverage measured 50.25% lines, 40.76% branches and 52.67% instructions. Validation artifact `9498959764` has digest `sha256:c80a26841485a3a27dcf6f6f670767a48eb6f7dbe42d55c4858b2c335cbfc152`.

Sentinel Restart Artifact workflow `32661521891` / job `97248320518` passed on the same product head; artifact `9498878851` has digest `sha256:6a0ab81859325b881f3f134677d0cdd95ac9505ea335bc973372732ba471c86b`. CodeRabbit was successful and live PR #148 review threads were zero before terminal publication.

## Independent ES-X03 blocker

ES-X03 remains parked on its own recorded blocker. D02 did not alter that package, its provider mirror, or its frozen validation evidence.
