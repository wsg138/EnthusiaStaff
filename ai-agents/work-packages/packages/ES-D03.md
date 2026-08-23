# ES-D03 — Authorization and cross-platform policy

Status: `ACTIVE`. Priority: 132. Depends on `ES-D01`, `ES-D02` (both `COMPLETE`). Internal package.

## Objective
Extend EnthusiaStaff domain authorization so Discord moderation is governed by authoritative capability/constraint policy rather than Discord roles or command visibility.

## Scope
Helper/Mod/Developer/Admin/Founder Discord rules; per-action and max-duration constraints; permanent-action gates; custom duration/consequence authority; cross-platform permission checks; view-history/notes/evidence capabilities; revoke/overturn/approval authority; self-punishment prevention; equal/higher staff protection; Discord-role hierarchy as an enforcement precondition only. Preserve the explicit Developer exception: Discord Mod-equivalent authority does not grant Minecraft punishment authority.

## Exclusions
No bot UX, Discord API enforcement, schema redesign beyond strictly necessary D02 follow-up migration, AutoMod, website UI or production permission changes.

## Branch and starting state
- Starting `main`: `3c340d6333d7e25b33b2f2af1e32a5cc15d5ee4b`.
- Branch: `package/es-d03-discord-authorization`.
- Draft PR: open immediately after the first coherent implementation checkpoint.
- Highest live Flyway migration at claim: `V19__discord_moderation_persistence.sql`; D03 adds no migration.
- Only open Staff PR at claim: parked ES-X03 PR #139; no D03/website/competition implementation overlap was found.

## Implementation checklist
- [x] Reconcile live main, open/draft PRs, package branches, current migration boundary and parked global work.
- [x] Define Discord staff operation and consequence policy types without changing Minecraft sanction enums.
- [x] Require explicit per-platform consequences instead of an implicit `BOTH` authorization path.
- [x] Add runtime-supplied Helper/Mod temporary-duration ceilings without inventing production values.
- [x] Preserve Mod-equivalent Discord authority for Developer while reusing existing Minecraft `AuthorizationPolicy` for every Minecraft mutation.
- [x] Model view-history/linked/notes/evidence, end/revoke, sanction approval, overturn request/approval/full-overturn authority.
- [x] Enforce self-target and equal/higher staff protection with Mod/Developer peer status for Discord.
- [x] Keep Discord native role hierarchy outside domain permission and expose it only as an enforcement precondition.
- [x] Add confirmation snapshots and exact-request reauthorization that fails closed on actor/target authority changes.
- [x] Add table-driven rank/action/scope/duration/hierarchy/staleness tests.
- [x] Add developer-facing authorization contract documentation.
- [ ] Open draft implementation PR and inspect exact changed paths.
- [ ] Run full repository exact-head validation and inspect static/review findings.
- [ ] Harshly review the complete diff and repair every valid finding.
- [ ] Freeze final product head and require zero valid unresolved review threads.
- [ ] Merge normally, verify containment/no unique work, clean the temporary branch, and publish terminal state.

## Validation
Table-driven policy tests covering every rank/action/scope/duration/hierarchy edge, stale reauthorization and denial paths; full repository gates and zero valid review findings.

## Completion
All entry points can later call one domain authorization service and cannot elevate through Discord roles, command origin, stale UI or cross-platform defaults.

## Current checkpoint
Implementation, focused tests, contract documentation and active package state are committed as the first coherent checkpoint. No schema, runtime, website, competition, production Discord, deployment, LiteBans authority or issue #43 path is changed.

Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-23-es-d03-discord-authorization.md`.
