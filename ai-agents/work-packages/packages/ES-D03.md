# ES-D03 — Authorization and cross-platform policy

Status: `REVIEW`. Priority: 132. Depends on `ES-D01`, `ES-D02` (both `COMPLETE`). Internal package.

## Revisions
- Starting `main`: `3c340d6333d7e25b33b2f2af1e32a5cc15d5ee4b`.
- Branch: `package/es-d03-discord-authorization`.
- PR: #149.
- Current executable repair: `6f0b3a8c264f8f7644a9bfafacb8b6cd29061950`.
- Prior validated candidate `ca66a97949cd8b9733c9039084d6230b2c63fd07` is superseded for merge acceptance because executable code changed afterward.
- Highest live Flyway migration remains `V19__discord_moderation_persistence.sql`; D03 adds no migration.

## Objective and implemented scope
D03 adds the authoritative domain authorization contract for moderation initiated through Discord. It models explicit platform-scoped consequences, runtime-supplied Helper/Mod duration ceilings, permanent/custom gates, read/lifecycle/approval/overturn capabilities, self and equal/higher-staff protection, confirmation-time stale reauthorization, and external enforcement preconditions.

Developer receives Mod-equivalent Discord authority without inheriting Minecraft punishment authority. Every Minecraft mutation continues through the existing `AuthorizationPolicy` and requires final Minecraft punishment-policy revalidation. Discord roles and command origin never grant domain authority. Future entry points must resolve authoritative actor/target staff state and call this service before enforcement.

No bot/API runtime, schema migration, website or competition implementation, production Discord configuration, production/private data, deployment, LiteBans authority change, cutover, or issue #43 acceptance is part of D03.

## Harsh-review repairs
The package has been reviewed and repaired rather than accepted from the first implementation candidate:

- package completion wording was scoped to the domain-service contract instead of claiming enforcement for callers outside this package;
- workspace routing fields were completed;
- null consequence elements are rejected as `IllegalArgumentException` before defensive copying, with regression coverage;
- an independent boundary review fixed the analogous null selected-platform input case with regression coverage;
- independent full-diff review found that an arbitrarily permissive injected `AuthorizationPolicy` could otherwise grant Developer a Minecraft mutation. Commit `6f0b3a8c264f8f7644a9bfafacb8b6cd29061950` now denies Developer Minecraft mutations before the injected Minecraft policy is consulted and adds a deliberately permissive-policy regression test.

The two prior CodeRabbit body nitpicks remain non-blocking under current type invariants unless new evidence makes them defects. Every live review thread must be rechecked after the repaired candidate is reviewed.

## Validation state
The earlier candidate `ca66a97949cd8b9733c9039084d6230b2c63fd07` passed Coverage `32673402553`, Sentinel artifact `32673402584`, Codacy coverage finalization and CodeRabbit status with zero unresolved inline threads. Those results remain truthful historical evidence but cannot authorize merge after executable code changed.

Required next action: validate the exact repaired review candidate through every applicable repository gate, inspect resulting CI/static/review output, harsh-review the complete diff, fix every valid defect, and freeze a new exact product head before terminal publication.

D03 has no Discord runtime or destructive side effect, so production/staging Discord execution is not itself a package acceptance gate. No production secret, role, token, data, deployment, punishment authority, or cutover state may be touched.

## Collision and authority state
Starting `main` is `3c340d6333d7e25b33b2f2af1e32a5cc15d5ee4b`; PR #139 is independently parked ES-X03 work. D03 changed no website, competition or migration implementation path. Reconcile these facts again immediately before final merge.

LiteBans remains authoritative and issue #43 remains separately gated.

## Completion
D03 becomes `COMPLETE` only after the repaired exact head passes all applicable gates, every valid review finding is resolved, PR #149 merges with a normal merge commit, feature-head containment/no unique work is proven, and safe temporary-branch cleanup is completed or its tooling limitation is recorded. Only then may `ES-D04` and `ES-D05` be marked `READY`; this worker starts neither one.

Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-23-es-d03-discord-authorization.md`.
