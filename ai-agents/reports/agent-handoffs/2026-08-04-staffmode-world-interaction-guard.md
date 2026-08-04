# PR #62 handoff — staff-mode world interaction guard

Date: 2026-08-04

## Repository and work item

- Repository: `wsg138/EnthusiaStaff`
- Work item: prevent active staff-mode profiles from changing ordinary gameplay state through uncovered Paper interaction events
- Starting `main`: `8173ad4fcd2b675598ebcb53cd1d1dbc23cb340b`
- Branch: `fix/staffmode-world-interaction-guard`
- Pull request: [#62](https://github.com/wsg138/EnthusiaStaff/pull/62)

## Scope and implemented behavior

A dedicated Paper listener now blocks active staff-mode players from:

- breaking or placing blocks;
- filling or emptying buckets;
- harvesting blocks;
- interacting with blocks or triggering physical block interactions;
- ordinary and precise-hitbox entity interaction;
- armor-stand manipulation;
- shearing entities;
- consuming items;
- fishing.

Left- and right-click air interactions remain available so dedicated staff tools can keep their normal air-click path. Ordinary players are unaffected. The listener reuses the existing `StaffModeManager.active(UUID)` runtime state and is registered with the existing Paper runtime composition.

## Files and architecture

- `paper/src/main/java/net/enthusia/staff/paper/staff/StaffModeWorldInteractionListener.java`
- `paper/src/main/java/net/enthusia/staff/paper/staff/StaffModeWorldInteractionPolicy.java`
- `paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java`
- `paper/src/test/java/net/enthusia/staff/paper/staff/StaffModeWorldInteractionPolicyTest.java`

The listener is a thin Bukkit adapter. The directly tested policy owns the active-session and air-versus-world interaction decisions. No parallel staff-session state, scheduler, persistence, command, or configuration system was introduced.

## Harsh-review findings and fixes

1. Replaced a custom per-event cancellation callback with Bukkit's shared `Cancellable` contract.
2. Added an explicit `PlayerInteractAtEntityEvent` handler rather than assuming superclass dispatch covers precise hitbox interactions.
3. Kept transition/recovery fencing outside this PR because the public staff-mode query currently exposes confirmed active state only; widening that lifecycle contract requires a separate bounded state-model change.

## Tests and coverage expectations

`StaffModeWorldInteractionPolicyTest` proves:

- ordinary players keep every `Action` path;
- active staff mode blocks mutation events;
- active staff mode allows left/right air clicks;
- active staff mode blocks left/right block clicks and physical interactions.

The event listener is thin platform wiring and may have low direct line coverage. Its underlying decisions are directly tested, and exact-head build/Paper compilation plus Pi staging, when available, must prove the Bukkit event types and registration.

## Migrations and compatibility

- No schema change.
- V16 remains the highest migration.
- V1–V16 remain immutable.
- Java 21, Paper/Folia-safe event handling, Velocity, MariaDB, Java clients and Geyser/Floodgate behavior remain unchanged outside this Paper interaction surface.

## Validation and merge gate

Read exact-head build, test, coverage, static-analysis, CodeRabbit, Codacy, review-thread, Pi and artifact evidence live on PR #62. Do not reuse evidence from an earlier head. Merge only after current `main` is included, the complete diff is reviewed, all applicable available checks are terminal and acceptable, and zero valid unresolved review threads remain.

## Production boundary

This is dormant development work only. It does not authorize deployment, production database or player-data access, production Discord use, punishment-authority activation, LiteBans changes, issue #43 acceptance, migration repair, or cutover.

## Next recommended item

After PR #62 completes, freshly select one remaining priority-one vanish or freeze restriction/lifecycle item. Do not begin it in the PR #62 session.
