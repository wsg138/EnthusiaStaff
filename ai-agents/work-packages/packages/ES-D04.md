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

## Exclusions
No bot moderation panel, punishment enforcement, role-sync replacement, AutoMod or production import execution.

## Validation
Concurrency/replay/expiry/import-idempotency/restart tests, provider-present/missing tests, MariaDB integration, full repository gates and review.
