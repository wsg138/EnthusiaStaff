# ES-D04 — Account linking and DiscordSRV migration

Status: `READY`. Priority: 133. Depends on `ES-D01`–`ES-D03` (all `COMPLETE`). Internal package; inspect external provider contracts before changes.

## Readiness
ES-D03 completion satisfies this package's remaining internal dependency. A future worker must still perform fresh live GitHub collision reconciliation and inspect the current DiscordSRV and PlayTimePlugin provider contracts before claiming implementation work.

## Objective
Make EnthusiaStaff the durable Discord↔Minecraft link authority while importing legacy DiscordSRV relationships without forcing relink.

## Scope
Two-direction one-use five-minute link codes; replacement invalidation; verified online-account completion; confirmed unlink; audited staff recovery/reassignment; historical links; PlayTimePlugin public-service active-minute main-account selection with 25% hysteresis and staff override; idempotent DiscordSRV link import; temporary main-link mirroring required for legacy role sync.

## Required safety
One Minecraft UUID cannot have two current Discord owners. Races/replays/restarts fail closed. Never read PlayTimePlugin SQLite directly; if provider active-playtime data is unavailable preserve the existing automatic main instead of guessing zero. Never commit legacy production link data.

The D04 full-diff review must also reconcile the D02 persistence refactor so there is exactly one authoritative link mutation path. `JdbcDiscordModerationPersistenceStore.link(...)` currently delegates to `JdbcDiscordLinkRepository`, while `JdbcDiscordIdentityRepository` still retains an older package-private `link(...)` implementation. Remove or deliberately consolidate that dead duplicate rather than allowing two mutation implementations to drift.

Validation must explicitly cover unlinking a Minecraft account and later linking that same UUID to a different Discord account: the old link remains historical, the new Discord becomes the only current owner, current subject resolution is correct, and no prior historical record is silently destroyed.

## Exclusions
No bot moderation panel, punishment enforcement, role-sync replacement, AutoMod or production import execution.

## Validation
Concurrency/replay/expiry/import-idempotency/restart tests, provider-present/missing tests, MariaDB integration, explicit unlink→different-Discord relink coverage, full repository gates and review.
