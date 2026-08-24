# ES-D04 Account linking and DiscordSRV migration — active handoff

Status: `ACTIVE`.

## Selection and starting state
- Selected through the owner-authorized Discord program lane after classifying all incomplete `ES-Dxx` packages.
- No actionable Discord continuation existed. `ES-D04` and `ES-D05` were dependency-complete `READY`; D04 won by lower numeric priority 133.
- Starting `EnthusiaStaff:main`: `783925e2b49ab4567bd3c3869e43fc03ff6d285f`.
- Branch: `package/es-d04-account-linking`.
- Implementation PR: pending first coherent checkpoint.
- Migration ceiling: `V19__discord_moderation_persistence.sql`.
- Issue #43: open; LiteBans remains authoritative.

## Collision reconciliation
- No pre-existing `ES-D04`/Discord implementation branch or PR was present.
- Only open EnthusiaStaff PR at claim was independently parked ES-X03 PR #139; it is not touched.
- `package/codacy-website-appeal-transitions` is 0 commits ahead and 167 behind `main`, so it contains no unique website work to collide with D04.
- No competition branch was found.

## Provider contract preflight
- `wsg138/PlayTimePlugin:main` exposes public `org.enthusia.playtime.api.PlaytimeService#getLifetime(UUID)` returning `PlaytimeSnapshot.activeMinutes`; D04 must use this public API and never read PlayTimePlugin SQLite.
- Current `DiscordSRV/DiscordSRV:master` exposes public `AccountLinkManager` reads plus `getLinkedAccounts`, `link`, and `unlink`; D04 may use that provider contract for import/mirroring and must not inspect legacy production storage directly.

## Existing D01/D02 foundation
- `DiscordMinecraftLinkPolicy` already enforces one current Discord owner per Minecraft UUID.
- `DiscordModerationPersistenceStore` already owns transactional/idempotent link/unlink and optimistic main-account changes.
- V19 persists current/historical links and the automatic/staff-override main-account source, but no durable one-use code state exists yet. D04 will add only the forward schema required for its code/recovery contract.

## Package scope
Implement two-direction one-use five-minute link codes, replacement invalidation, verified online Minecraft completion, confirmed unlink, audited staff recovery/reassignment, historical links, PlayTimePlugin lifetime-active main-account selection with 25% hysteresis and staff override, idempotent DiscordSRV import, and temporary main-link mirroring for legacy role sync.

## Safety and exclusions
Races, replays, expiration and restarts fail closed. If PlayTimePlugin active-playtime data is unavailable, preserve the existing automatic main rather than treating missing data as zero. Do not commit legacy production mappings. Do not implement punishment UX/enforcement, role-sync replacement, AutoMod, staff bot runtime, production import execution, production Discord configuration, deployment, LiteBans cutover or issue #43 acceptance.

## Remaining work
Implement product and tests, update documentation, open the draft PR after the first coherent checkpoint, run harsh full-diff review, resolve every valid automated/human/static/CI finding, obtain every applicable exact-head gate, merge normally, verify feature-head containment and safe branch cleanup, publish terminal canonical state, mark newly dependency-complete Discord work without activating it, then stop.
