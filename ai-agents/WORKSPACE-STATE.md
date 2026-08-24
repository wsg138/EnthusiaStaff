# Workspace state

Last updated: 2026-08-23

Live GitHub overrides stale records. Detailed package evidence remains in the registries, selected package record, canonical handoff, and PR verification ledgers.

## Current routing

| Field | Value |
| --- | --- |
| Discord program active package | `ES-D03 — Authorization and cross-platform policy` remains in `REVIEW` on `package/es-d03-discord-authorization`; PR #149 is open and non-draft. |
| Discord latest completion | `ES-D02 — Discord persistence and migration schema` remains the latest completed Discord package until D03 terminal merge. |
| D03 executable state | The temporary Developer/Minecraft hard-deny change was rejected after re-reading the approved product contract. The candidate product tree is restored to the previously validated `ca66a97949cd8b9733c9039084d6230b2c63fd07` behavior: Developer gains no Minecraft authority from Discord rank, but independently granted Minecraft/domain authority remains possible through `AuthorizationPolicy`. |
| D03 validation | Frozen product head `ca66a97949cd8b9733c9039084d6230b2c63fd07` has successful Coverage `32673402553`, Sentinel artifact `32673402584`, Codacy coverage finalization and CodeRabbit status. Reuse is permitted only if an exact compare proves the final candidate differs from that frozen head solely in package/state documentation. |
| D03 next work | Prove the exact frozen-head-to-candidate delta, recheck live review/check states and collisions, harsh-review the complete diff, then publish terminal state and merge normally only if every gate is satisfied. |
| Downstream Discord state | `ES-D04` and `ES-D05` remain `PLANNED` until D03 is actually complete. If D03 completes, D04 is the lower-priority-number next candidate; this worker will not start it. |
| Migration state | Live `main` ends at `V19__discord_moderation_persistence.sql`; D03 adds no migration. |
| Collision reconciliation | Starting `main` is `3c340d6333d7e25b33b2f2af1e32a5cc15d5ee4b`. PR #139 remains independently parked ES-X03 work. D03 changes no website, competition, or migration implementation path. |
| D03 blockers | None currently known; final exact-delta/review/merge checks remain actionable. |
| Independently parked package | `ES-X03 — EnthusiaMarket destructive provider` remains independently blocked and is not modified by D03. |
| Production boundary | No production Discord configuration, bot token, player/private production data, deployment, production migration, LiteBans authority change, or cutover is authorized or performed. Issue #43 remains open and LiteBans remains authoritative. |
| Current handoff | `ai-agents/reports/package-handoffs/2026-08-23-es-d03-discord-authorization.md` |

## D03 current review checkpoint

The D03 authorization service models explicit platform-scoped consequences, runtime-supplied limits, Discord rank capabilities, self/equal-higher protection, external role-hierarchy preconditions, Minecraft policy reuse, and confirmation-time stale reauthorization.

A resumed-review hypothesis claimed the injectable Minecraft `AuthorizationPolicy` could improperly elevate Developer. The approved product contract disproves that hypothesis: Developer's Discord Mod-equivalent rank must not itself grant Minecraft authority, but a Developer may act on Minecraft when the existing Minecraft/domain policy independently grants that authority. The service already separates those authorities by consulting `AuthorizationPolicy`; the temporary unconditional deny would have removed an explicitly allowed independent-permission path and is therefore reverted.

## Independent ES-X03 blocker

ES-X03 remains parked on its own recorded blocker. D03 does not alter that package, its provider mirror, or its frozen validation evidence.
