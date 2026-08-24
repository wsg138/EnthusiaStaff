# Workspace state

Last updated: 2026-08-23

Live GitHub overrides stale records. Detailed package evidence remains in the registries, selected package record, canonical handoff, and PR verification ledgers.

## Current routing

| Field | Value |
| --- | --- |
| Discord program active package | `ES-D04 — Account linking and DiscordSRV migration` is `ACTIVE` on `package/es-d04-account-linking`, claimed from exact `main` `783925e2b49ab4567bd3c3869e43fc03ff6d285f`. |
| Discord latest completion | `ES-D03 — Authorization and cross-platform policy` is `COMPLETE` through PR #149 on normal merge. Frozen validated merge-ready head: `5cd98a719e30eff64d1595f1e219ea70553c66c0`. |
| D04 objective | Durable two-direction five-minute account-linking workflow, PlayTimePlugin-backed main-account selection, DiscordSRV import/mirroring migration support, and audited recovery without production import execution. |
| D04 preflight | No existing Discord implementation PR/branch; current DiscordSRV `AccountLinkManager` and PlayTimePlugin `PlaytimeService` contracts are usable; legacy website branch is 0 ahead/167 behind `main`; no competition branch found. |
| D04 branch | `package/es-d04-account-linking`; implementation PR opens after first coherent checkpoint. |
| Other ready Discord work | `ES-D05 — Staff bot runtime foundation` remains `READY` and is not started by the D04 worker. |
| Migration state | Package start ceiling is `V19__discord_moderation_persistence.sql`; D04 may add only a forward migration if its durable code/recovery contract requires one. |
| Independently parked package | `ES-X03 — EnthusiaMarket destructive provider` remains `BLOCKED` / `PARKED_BLOCKED` on its independent runtime-validation condition and is not modified by D04. |
| Production boundary | No production Discord configuration, bot token, player/private production data, deployment, production migration/import execution, LiteBans authority change, or cutover is authorized or performed. Issue #43 remains open and LiteBans remains authoritative. |
| Current handoff | `ai-agents/reports/package-handoffs/2026-08-23-es-d04-account-linking.md` |

## D04 claim record

Live `main` is `783925e2b49ab4567bd3c3869e43fc03ff6d285f`, the normal merge of D03 PR #149. No D04 branch or PR existed before this claim. The current provider preflight found an exact public PlayTimePlugin API for lifetime active minutes and an exact public DiscordSRV account-link manager for migration/mirroring. No direct provider database read is required or permitted.

The D04 worker owns exactly one Discord package. It must preserve unrelated parked Market work and must not start D05 after D04 terminates.

## Independent ES-X03 blocker

ES-X03 remains parked on its own recorded owner-controlled runtime blocker. D04 does not alter that package, its provider mirror, or its frozen validation evidence.
