# ES-D04 — Account linking and DiscordSRV migration

Status: `ACTIVE`. Priority: 133. Depends on `ES-D01`–`ES-D03` (all `COMPLETE`). Internal package; external provider contracts were inspected before claim.

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

## Active worker state
- Claimed: 2026-08-23 by the owner-authorized Discord sequential worker.
- Starting `main`: `783925e2b49ab4567bd3c3869e43fc03ff6d285f`.
- Branch: `package/es-d04-account-linking`.
- Implementation PR: not yet opened; open a draft after the first coherent implementation checkpoint.
- Migration ceiling at claim: `V19__discord_moderation_persistence.sql`.
- DiscordSRV contract: current public `AccountLinkManager` exposes authoritative link reads plus `link`/`unlink`; do not access its private storage.
- PlayTimePlugin contract: current public `PlaytimeService#getLifetime(UUID)` returns `PlaytimeSnapshot.activeMinutes`; no SQLite read is needed.
- Collision reconciliation: no Discord implementation PR/branch exists; PR #139 is independently parked ES-X03; `package/codacy-website-appeal-transitions` is 0 commits ahead and 167 behind `main`; no competition branch was found.
- Issue #43 remains open; LiteBans remains authoritative; no production Discord data/configuration/deployment/cutover is authorized.
- Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-23-es-d04-account-linking.md`.

## Remaining work
Implement the complete package, add targeted and MariaDB integration coverage, harsh-review the final diff, resolve valid review/static/CI findings, obtain every applicable exact-head gate, merge normally, verify containment/cleanup, and publish terminal state. Do not begin ES-D05 in this worker.
