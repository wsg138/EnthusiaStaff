# ES-D16 — Moderation console real-data read bridge

Status: `IN_PROGRESS`. Priority: 135.5. Depends on `ES-D03`, `ES-D05`, `ES-D06`, and merged web-first moderation foundation PR #186. Internal package.

Run ref: `ES-D16-20260831-real-data-read-bridge`.

## Objective
Connect the owner-approved Cloudflare moderation console to real, read-only Enthusia Discord/Minecraft/moderation data while preserving simulation-only punishment/deletion behavior and the existing D03 authority model.

## Scope
- Real selected-target identity, linked-account, active-punishment, total-history, and offense-relevant-history reads through existing D06/domain services.
- A narrow Bloom-side moderation read API with explicit response DTOs, server-side D03 authorization, actor/guild/target binding, short-lived signed service requests, replay resistance, bounded request sizes/pages, rate limiting, and non-cacheable private responses.
- Cloudflare Worker session ingress/proxying only; no browser access to internal service credentials or direct moderation authority.
- Real Discord guild/channel/category/message reads through bounded on-demand JDA REST access, including exact message context, surrounding messages, bounded pagination, filters, replies/references, attachments, and edited timestamp where Discord exposes it.
- Actual channel/category data for the staged restriction picker, without applying permission overrides.
- Preserve independent incident/evidence selection, violating marking, and future-deletion selection.
- Deterministic adapter/security tests plus sanitized live read-only staging acceptance against the Enthusia guild/staging bot.
- Verify the staging Discord application Message Content privileged-intent state before claiming message-content acceptance; subscribe only to Gateway intents actually required by the architecture.

## Explicit exclusions
- `ES-D07` enforcement and every warn/mute/kick/ban/restrict/unmute/unban/unrestrict mutation.
- Discord message deletion.
- LiteBans, Minecraft, Paper, moderation DB, case, note, or punishment mutation.
- Permission-override application.
- Server-wide or unbounded Discord message scraping.
- Reimplementation of D03 moderation authority or D06 authoritative read services.
- ES-D13, ES-X03, unrelated website, competition, wiki, provider, Market, hosting, or production infrastructure work.

## Dependencies and startup evidence
- `ES-D03`: `COMPLETE` — authoritative linked-staff moderation authorization exists.
- `ES-D05`: `COMPLETE` — staff-bot/JDA runtime exists.
- `ES-D06`: `COMPLETE` — authoritative linked-account/history/active/relevant-history read domain exists.
- PR #186: merged normally to `main` as `a6ee52ca69cf50f39bd7a18237bbc8cdfc9fc51b` before this package branch was created.
- Startup stale review reread exact `main` `a6ee52ca69cf50f39bd7a18237bbc8cdfc9fc51b`; open ES-D13 PR #178 and ES-X03 PR #139 are preserved concurrent work.

## Security/privacy contract
Browser → Cloudflare Worker → authenticated narrow Enthusia moderation read API → Discord/D06/domain/database reads. Browser responses and storage never contain bot tokens, MariaDB/LiteBans/Paper credentials, permanent bearer secrets, or internal service credentials. No credentials in URLs. Protected responses use `Cache-Control: private, no-store`. Private player/message data must not enter CI artifacts, screenshots, analytics, or public logs. Server-side authorization is required for every read; UI visibility is not authority.

## Validation
Required tests cover actor/target/guild binding, expiry/replay/malformed service/session rejection, unauthorized target changes, direct backend rejection, real-data response allowlists, bounded pagination/filter behavior, Discord outage/rate-limit truthfulness, exact message centering, linked-account/history/relevant-history rendering, independent evidence/violation/deletion sets, and proof that no destructive route or adapter is reachable. Exact-head build/tests/static analysis, hosted Codacy/CodeRabbit review, sanitized staged live read acceptance, normal merge, containment, cleanup, and durable handoff are required before `COMPLETE`.

## Resume state
Worker: current owner-directed worker. Branch: `package/es-d16-moderation-read-bridge`. PR: `#187` (open/draft). Last reviewed executable head: `07dcf93359d7be419c6e2af784a1ebf034d1cdcf`; CodeRabbit review `PRR_kwDOTem7rs8AAAABLsvG1g` produced three valid correctness findings. Review-repair executable commit: `41bdb55faf12d3e55d2445948b489ec8e4a845bd`, addressing no-channel filter/limit handling, invalid message-target author IDs, and malformed private-read JSON with regression coverage. Prior exact-head Java 21/Codacy validation passed on `07dcf933...`; validation for the repair head is pending after this state-only synchronization commit. Required staging remains externally blocked by HTTP 403 on the protected Cloudflare token's first account-level named-tunnel API request. Next action: run exact-head hosted build/static analysis on the synchronized branch, reconcile/resolve the reviewed findings, rerun the required exact-head staging workflow, and if the same protected Cloudflare permission blocker remains, publish the canonical `BLOCKED` / `PARKED_BLOCKED` state without weakening the private-tunnel gate.

## Known blockers
The protected staging Cloudflare API token currently receives HTTP 403 on the account-level Cloudflare Tunnel API needed to provision the fixed moderation-read tunnel. This is a required D16 staging gate and must not be bypassed. Exact unblock: grant or replace the protected staging token with the required Cloudflare Tunnel account read/edit authority for the owning account, then rerun exact-head staging and continue Bloom/live read acceptance.