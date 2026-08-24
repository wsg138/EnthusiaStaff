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

The D04 full-diff review must reconcile D02's persistence refactor so link/unlink/reassignment have one clear transactional owner. The current store routes `link(...)` through `JdbcDiscordLinkRepository` but `unlink(...)` through `JdbcDiscordIdentityRepository`, and that identity repository still retains an older package-private `link(...)` implementation. Consolidate this deliberately and remove the dead duplicate rather than leaving two implementations/owners that can drift.

Unlink must preserve a valid main-account state for the remaining current linked Minecraft accounts. If the unlinked account was the current main and other linked Minecraft accounts remain, the service must immediately choose/persist the correct replacement under the approved automatic/staff-override rules; it must not leave a linked multi-account Discord subject indefinitely without a main. If no Minecraft account remains linked, an absent main is valid.

Validation must explicitly cover unlinking a Minecraft account and later linking that same UUID to a different Discord account: the old link remains historical, the new Discord becomes the only current owner, current subject resolution is correct, and no prior historical record is silently destroyed. It must also cover unlinking the current main with other linked accounts remaining and verify deterministic replacement-main behavior.

## Exclusions
No bot moderation panel, punishment enforcement, role-sync replacement, AutoMod or production import execution.

## Validation
Concurrency/replay/expiry/import-idempotency/restart tests, provider-present/missing tests, MariaDB integration, explicit unlink→different-Discord relink coverage, current-main unlink replacement coverage, full repository gates and review.
