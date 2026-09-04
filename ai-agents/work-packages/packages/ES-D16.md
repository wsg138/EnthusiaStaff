# ES-D16 — Moderation console real-data read bridge

Status: `ACTIVE` / `ACTIONABLE_CONTINUATION`. Priority: 135.5. Depends on `ES-D03`, `ES-D05`, `ES-D06`, and merged web-first moderation foundation PR #186. Internal package.

Run ref: `ES-D16-20260831-real-data-read-bridge`.

## Objective
Connect the owner-approved Cloudflare moderation console to real, read-only Enthusia Discord/Minecraft/moderation data while preserving simulation-only punishment/deletion behavior and the existing D03 authority model.

## Owner production-acceptance authorization
On 2026-09-02 the owner explicitly authorized using the live Enthusia Paper server for temporary D16 authority/real-data acceptance instead of deploying the full `EnthusiaStaff-Paper.jar`. The dedicated `paper-authority-bridge` therefore serves the signed private LuckPerms rank endpoint while destructive Discord/Minecraft moderation remains prohibited.

On 2026-09-04 live read acceptance exposed a clean but unmigrated EnthusiaStaff MariaDB: Staff Bot reads reached Bloom successfully but failed with MariaDB 1146 for `moderation_subject_discord_identities`, surfaced as `ModerationPersistenceException`. The owner then explicitly extended the temporary bridge authorization so it can be useful during the transition: initialize the new EnthusiaStaff database, observe player identity data, and read/import existing DiscordSRV links. The owner also suggested LiteBans transition monitoring but allowed that to remain separate if duplicating it would be excessive.

The revised authorization remains deliberately bounded:

- do **not** deploy the full `EnthusiaStaff-Paper.jar` solely for D16 acceptance;
- the bridge may apply the repository's existing Flyway migrations only to the owner-configured EnthusiaStaff database and may persist transition player/link observations there;
- DiscordSRV is read only: current AccountLinkManager snapshots may be imported through existing conflict/idempotency semantics, but the temporary bridge never invokes legacy link/unlink mutators;
- LiteBans remains authoritative and untouched; use the repository's dedicated SELECT-only migration/shadow system later rather than duplicating its cutover logic inside this bridge;
- no warn/mute/kick/ban/restrict/freeze/inventory/economy/reputation/automod/message-deletion or other player-facing moderation mutation is authorized;
- the LuckPerms rank listener remains private/replay-protected on port `8771`, with no public Bloom allocation;
- the staging Discord bot and Cloudflare moderation workspace remain staging/simulation-only;
- production-derived private values, player data, credentials, raw messages, or reconstructable evidence must not be copied into GitHub, ChatGPT, CI artifacts, or public logs.

## Delivered read bridge
PR #187 implements real selected-target identity, linked-account, sanction/history, case/note, channel/category and bounded Discord-message reads through the existing D03/D06 model; a loopback-only Staff Bot read API on `127.0.0.1:8766`; target-bound Worker sessions/proofs; exact staging-origin CORS; request expiry/replay fences; bounded JDA reads; and simulation-only punishment/deletion/permission-override controls.

The Staff Bot is intentionally read-only: `DiscordStaffReadRuntime` opens JDBC read-only and never runs Flyway. That means an empty database must be initialized by an authorized write-capable runtime before D16 can return authoritative data.

## Temporary transition collector
The owner-authorized bridge now supports an opt-in `collector.properties` alongside the existing `authority.properties`. When present it opens only the narrow `TransitionDataRuntime`, which applies existing migrations and exposes only `PlayerDirectory` plus `DiscordModerationPersistenceStore` to the collector.

Collection behavior is bounded and non-destructive:

- current online player observations are recorded; cached offline linked players are processed in batches of at most 128 per pass;
- DiscordSRV snapshots above 5,000 links are rejected;
- only links with a usable observed Minecraft identity are eligible for import;
- imports reuse `DiscordSrvMigrationService`, `MIGRATED_DISCORDSRV`, stable operation keys, unchanged detection, and conflict preservation;
- no DiscordSRV mutation/mirroring occurs;
- persistence runs on one bounded worker and overlapping passes are skipped;
- logs contain aggregate counts/failure classes only;
- collector failure does not disable the separate LuckPerms authority endpoint.

This collector intentionally does not ingest LiteBans. The existing LiteBans migration/shadow implementation already provides SELECT-only legacy reads, checksums/high-water marks, protected identities, lifecycle/cutover fencing and shadow comparison; that is the correct future transition path and remains outside this narrow D16 bridge extension.

## Historical evidence
Earlier exact-head Java/Codacy/CodeRabbit/artifact and protected Cloudflare staging successes remain historical evidence only after executable heads changed. Failed, cancelled, superseded, and wrong-head runs are never relabeled as passing evidence.

The pre-extension Staff Bot head `e6677e143fcf56a10688e630093e6187310a7d74` passed all hosted PR gates and fixed the synthetic missing-Discord-member authorization classification. The owner deployed that Staff Bot, and a real browser request subsequently proved Worker/session/proof/CORS/tunnel ingress reached the Staff Bot; the remaining 503 was traced to the absent DB schema rather than transport.

## Remaining acceptance

1. freeze and pass exact-head Java, integration, Codacy, CodeRabbit, web/config-cache and Sentinel artifact validation for the transition-enabled bridge;
2. provide the exact authority-bridge JAR/checksum to the owner;
3. owner replaces only the temporary bridge JAR, adds runtime-only `collector.properties`, and performs one controlled Paper restart; existing `authority.properties` remains unchanged;
4. verify sanitized bridge startup, Flyway/schema initialization and an aggregate transition-collector pass without exposing credentials/player data;
5. open a fresh Discord-generated moderation preview and verify real linked identity data; diagnose any remaining truthful source/authorization failure;
6. complete sanitized D16 acceptance for actor/guild/target/session binding, real identity/link/sanction/history semantics, bounded readable Discord messages, unauthorized/replay/outage rejection, and zero destructive mutation/deletion;
7. reconcile moving `main` with normal merge history, rerun every invalidated exact-head gate, update canonical records, merge PR #187 normally, prove containment/cleanup, and publish `COMPLETE`.

## Explicit exclusions still in force
The owner production exception does **not** authorize warn/mute/kick/ban/restrict/unmute/unban/unrestrict mutation, Discord message deletion, permission-override application, LiteBans writes or authority change, issue #43 acceptance, cutover, broad production data export, secret disclosure, or player-facing experimentation. ES-D13 PR #178 and ES-X03 PR #139 remain separate and untouched. Do not begin D07 as part of this D16 worker.
