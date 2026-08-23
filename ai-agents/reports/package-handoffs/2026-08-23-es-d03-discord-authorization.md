# ES-D03 Discord authorization — active handoff

Status: `ACTIVE`.

## Start evidence
- Selected through the dedicated Discord program lane after D01/D02 completion.
- Starting `main`: `3c340d6333d7e25b33b2f2af1e32a5cc15d5ee4b`.
- Branch: `package/es-d03-discord-authorization`.
- D03 had no pre-existing branch or PR.
- Only open Staff PR was independently parked ES-X03 PR #139. Its changed paths are Market/component/provider work and do not own D03 `domain/.../auth` paths.
- The old `package/codacy-website-appeal-transitions` branch is 0 commits ahead of current main. No competition branch was found.
- Highest live Staff Flyway migration is `V19__discord_moderation_persistence.sql`; D03 adds no migration.

## First coherent checkpoint
D03 adds one domain authorization service and typed request/decision contracts for later Discord entry points. The policy has no Discord-role or command-origin authorization input. It supports explicit per-platform consequences, runtime-supplied duration ceilings, Helper/Mod/Developer/Admin/Founder rules, existing Minecraft-policy reuse, Developer's Discord-only Mod-equivalent exception, self/equal-higher staff protection, external Discord-role-hierarchy preconditions, and confirmation-time stale-state reauthorization.

Focused tests cover the full rank/operation matrix, Helper and Mod/Developer consequence limits, permanent/custom gates, explicit cross-platform plans, Developer Minecraft denial, existing Minecraft custom-policy preservation, target hierarchy, role-precondition separation, stale reauthorization and structurally invalid requests.

Developer contract: `docs/discord-authorization.md`.

## Safety boundaries
No bot runtime, Discord API call, bot token, production Discord role/configuration change, website path, competition path, schema migration, production data access, LiteBans authority change, deployment, cutover or issue #43 acceptance is part of D03.

## Next action
Open the draft PR from this checkpoint, inspect the exact diff, then run and follow every applicable exact-head repository/static/review gate. Fix valid findings before freezing and normal merge.
