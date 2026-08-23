# ES-D03 — Authorization and cross-platform policy

Status: `READY`. Priority: 132. Depends on `ES-D01`, `ES-D02` (both `COMPLETE`). Internal package.

D03 became dependency-complete when D02's validated terminal state was published with PR #148. It is eligible for a fresh Discord-program worker after live GitHub reconciliation; the D02 worker must not begin it.

## Objective
Extend EnthusiaStaff domain authorization so Discord moderation is governed by authoritative capability/constraint policy rather than Discord roles or command visibility.

## Scope
Helper/Mod/Developer/Admin/Founder Discord rules; per-action and max-duration constraints; permanent-action gates; custom duration/consequence authority; cross-platform permission checks; view-history/notes/evidence capabilities; revoke/overturn/approval authority; self-punishment prevention; equal/higher staff protection; Discord-role hierarchy as an enforcement precondition only. Preserve the explicit Developer exception: Discord Mod-equivalent authority does not grant Minecraft punishment authority.

## Exclusions
No bot UX, Discord API enforcement, schema redesign beyond strictly necessary D02 follow-up migration, AutoMod, website UI or production permission changes.

## Validation
Table-driven policy tests covering every rank/action/scope/duration/hierarchy edge, stale reauthorization and denial paths; full repository gates and zero valid review findings.

## Completion
All entry points can later call one domain authorization service and cannot elevate through Discord roles, command origin, stale UI or cross-platform defaults.
