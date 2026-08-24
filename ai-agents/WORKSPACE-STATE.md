# Workspace state

Last updated: 2026-08-23

Live GitHub overrides stale records. Detailed package evidence remains in the registries, selected package record, canonical handoff, and PR verification ledgers.

## Current routing

| Field | Value |
| --- | --- |
| Discord program active package | `ES-D03 — Authorization and cross-platform policy` is in `REVIEW` on `package/es-d03-discord-authorization`; PR #149 is open and non-draft. |
| Discord latest completion | `ES-D02 — Discord persistence and migration schema` remains the latest completed Discord package through PR #148 while D03 is revalidated. |
| D03 current executable head | `6f0b3a8c264f8f7644a9bfafacb8b6cd29061950` fixes a fail-closed Developer/Minecraft authority gap found during independent full-diff review. |
| D03 prior evidence | Earlier exact-head evidence for `ca66a97949cd8b9733c9039084d6230b2c63fd07` remains truthful historical evidence but is superseded for merge acceptance because executable code changed afterward. |
| D03 next work | Run and inspect every applicable exact-head gate on the current review candidate, harsh-review the complete resulting diff, resolve every valid finding, then publish terminal state and merge normally if green. |
| Downstream Discord state | `ES-D04` and `ES-D05` remain `PLANNED` until D03 is actually complete. If D03 completes, D04 is the lower-priority-number next candidate; this worker will not start it. |
| Migration state | Live `main` ends at `V19__discord_moderation_persistence.sql`; D03 adds no migration. |
| Collision reconciliation | Starting `main` is `3c340d6333d7e25b33b2f2af1e32a5cc15d5ee4b`. PR #139 remains independently parked ES-X03 work. D03 changes no website, competition, or migration implementation path. |
| D03 blockers | None currently known; ordinary exact-head validation/review remains actionable. |
| Independently parked package | `ES-X03 — EnthusiaMarket destructive provider` remains independently blocked and is not modified by D03. |
| Production boundary | No production Discord configuration, bot token, player/private production data, deployment, production migration, LiteBans authority change, or cutover is authorized or performed. Issue #43 remains open and LiteBans remains authoritative. |
| Current handoff | `ai-agents/reports/package-handoffs/2026-08-23-es-d03-discord-authorization.md` |

## D03 current review checkpoint

The D03 authorization service models explicit platform-scoped consequences, runtime-supplied limits, Discord rank capabilities, self/equal-higher protection, external role-hierarchy preconditions, Minecraft policy reuse, and confirmation-time stale reauthorization.

Independent full-diff review found that the public injectable `AuthorizationPolicy` seam could otherwise allow a permissive implementation to grant a Developer a Minecraft mutation. Commit `6f0b3a8c264f8f7644a9bfafacb8b6cd29061950` now denies Developer Minecraft mutations before consulting that injected policy and adds a regression test using a deliberately permissive policy. This preserves the explicit Discord-only Developer exception as a service invariant rather than an accident of `DefaultAuthorizationPolicy`.

## Independent ES-X03 blocker

ES-X03 remains parked on its own recorded blocker. D03 does not alter that package, its provider mirror, or its frozen validation evidence.
