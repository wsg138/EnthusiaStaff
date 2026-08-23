# Workspace state

Last updated: 2026-08-23

Live GitHub overrides stale records. Detailed package evidence remains in the registries, selected package record, canonical handoff, and PR verification ledgers.

## Current routing

| Field | Value |
| --- | --- |
| Discord program active package | `ES-D03 — Authorization and cross-platform policy` is `ACTIVE` on `package/es-d03-discord-authorization`, claimed from exact `main` `3c340d6333d7e25b33b2f2af1e32a5cc15d5ee4b`. |
| Discord latest completion | `ES-D02 — Discord persistence and migration schema` is `COMPLETE` through PR #148. Frozen product head: `9f0d9bb44ab6929bec3bf652e0c9b3467104b423`. |
| D03 scope | Pure domain authorization, focused tests and documentation. No schema, bot runtime, website, competition, production Discord or cutover path is owned by D03. |
| Migration state | Live `main` ends at `V19__discord_moderation_persistence.sql`; D03 adds no migration. |
| Collision reconciliation | At claim, the only open Staff PR was independently parked ES-X03 PR #139. No D03 branch/PR existed; the old website branch was 0 commits ahead of main and no competition branch was found. |
| Independently parked package | `ES-X03 — EnthusiaMarket destructive provider` remains `BLOCKED` / `PARKED_BLOCKED` on its independent runtime-validation condition and is not modified by D03. |
| Production boundary | No production Discord configuration, bot token, player/private production data, deployment, production migration, LiteBans authority change, or cutover is authorized or performed. Issue #43 remains open and LiteBans remains authoritative. |
| Current handoff | `ai-agents/reports/package-handoffs/2026-08-23-es-d03-discord-authorization.md` |

## D03 current checkpoint

The first coherent checkpoint defines an authoritative Discord moderation authorization service with explicit platform consequences, runtime-supplied Helper/Mod ceilings, Mod-equivalent Discord authority for Developer without Minecraft authority inheritance, permanent/custom gates, read and lifecycle capabilities, self/equal-higher staff protection, external-only Discord role-hierarchy preconditions, and stale confirmation reauthorization. Focused table-driven tests and `docs/discord-authorization.md` are included.

Draft PR creation, exact-head repository validation, full-diff review, review/static finding resolution, normal merge, containment and terminal publication remain in this run.

## Independent ES-X03 blocker

ES-X03 remains parked on its own recorded blocker. D03 does not alter that package, its provider mirror, or its frozen validation evidence.
