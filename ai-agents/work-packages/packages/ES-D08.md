# ES-D08 — Cross-platform moderation integration

Status: `IMPLEMENTED_UNVERIFIED`. Priority: 137. Depends on `ES-D07`. The repository implementation is present on the package branch; production activation still requires the documented live acceptance matrix. Internal package.

## Objective
Allow explicit Discord, Minecraft, or Both moderation while preserving separate sanctions/enforcement state under one case.

## Scope
Discord→Minecraft and Minecraft→Discord staff entry points; explicit scope selector/default-by-origin; separate per-platform consequences on confirmation; same case/history context; independently persisted enforcement intents; partial-success/pending/retry/recovery UI and audit; required Minecraft GUI/ladder/domain integration. Never silently infer `Both`.

## Authority boundary
LiteBans/production authority remains unchanged unless separately authorized. Implement against supported EnthusiaStaff domain/application services and shadow/staging paths as necessary; do not route around current authority fencing.

## Validation
Cross-platform permission/hierarchy tests, missing-identity selector behavior, atomic intent persistence, one-side outage/restart/retry tests, duplicate/replay tests, staged Discord/Minecraft failure matrix and full repository gates.


## Current implementation

- Discord/web origin: the existing private moderation workspace exposes explicit Discord, Minecraft, and Both choices. Both sends distinct platform intents and confirmation is bound to one immutable confirmation UUID.
- Minecraft origin: the central punishment GUI defaults to Minecraft and exposes an explicit review-time scope selector for Minecraft, Discord, or Both. Discord/Both require exactly one current linked Discord identity.
- Both persists the authoritative Minecraft case and Discord enforcement intent atomically. Discord external effects remain owned by the standalone StaffBot durable worker.
- Minecraft and Discord delivery states are displayed independently. Lost-response/restart recovery reuses deterministic identifiers or the original confirmation UUID and reads durable state before permitting a replacement.
- Current policy, linked identity, staff hierarchy, and platform authorization are revalidated at confirmation. A missing or changed identity fails closed.
- Paper-side Discord/Both entry is separately gated by `ENTHUSIA_STAFF_CROSS_PLATFORM_ENABLED=true` plus `ENTHUSIA_STAFF_CROSS_PLATFORM_DISCORD_GUILD_ID` and the same Discord duration ceilings used by StaffBot.

## Remaining acceptance

Do not mark this package `STAGING_VERIFIED` until the Discord/Minecraft staged failure matrix covers normal success, Discord outage/retry, Minecraft/network-delivery delay, process restart, lost confirmation response, duplicate replay, missing/changed link, protected target, and the final full repository gates on the exact candidate SHA.
