# ES-D16 — Moderation console real-data read bridge

Status: `ACTIVE` / `ACTIONABLE_CONTINUATION`. Priority: 135.5. Depends on `ES-D03`, `ES-D05`, `ES-D06`, and merged web-first moderation foundation PR #186. Internal package.

Run ref: `ES-D16-20260831-real-data-read-bridge`.

## Objective
Connect the owner-approved Cloudflare moderation console to real, read-only Enthusia Discord/Minecraft/moderation data while preserving simulation-only punishment/deletion behavior and the existing D03 authority model.

## Owner production-acceptance authorization
On 2026-09-02, after D16 had been parked on non-production Bloom live acceptance, the owner explicitly authorized using the live Enthusia Paper server for the remaining temporary D16 authority/real-data acceptance. This is the separate explicit owner authorization required by `ai-agents/AGENTS.md` section 7 and the Discord worker prompt production boundary.

The authorization is deliberately narrowed for safety:

- do **not** deploy the full `EnthusiaStaff-Paper.jar` solely for D16 acceptance;
- instead use the dedicated `paper-authority-bridge` artifact introduced on PR #187;
- the bridge has no commands, no Bukkit event listeners, no database connection, no punishment/mutation adapters, no LiteBans authority change, and no player-facing behavior;
- the bridge reads only current LuckPerms rank permissions and serves the signed D16 staff-rank lookup;
- port `8771` must have no public Bloom allocation; requests are accepted only from private/loopback peers and require short-lived HMAC signatures plus replay protection; responses are signed;
- the staging Discord bot and Cloudflare moderation workspace remain staging/simulation-only; no destructive Discord or Minecraft action is authorized;
- production-derived private values, player data, credentials, raw messages, or reconstructable evidence must not be copied into GitHub, ChatGPT, CI artifacts, or public logs.

## Delivered implementation before the production exception
Reviewed executable head `066b97f4344ab83d3e226b3f4ff3ab614dee6430` on `package/es-d16-moderation-read-bridge` / PR #187 implemented the full read bridge plus panel-only Bloom staging transport:

- real selected-target identity, linked-account, sanction/history, case/note, channel/category, and bounded Discord-message reads through existing D06/domain authority;
- loopback-only moderation read API `127.0.0.1:8766` with explicit DTO allowlists, D03 authorization, actor/guild/target binding, HMAC authentication, expiry/replay resistance, and bounded body/page/rate controls;
- target-bound hosted Discord launch tickets and Worker session ingress/proxying without browser access to internal credentials;
- bounded JDA REST reads with staff view/history permission fences, filters/pagination, replies/references, attachments, edited timestamps, and exact message context;
- file-backed Bloom Staff Bot/Paper configuration preserving one authoritative MariaDB and no Staff Bot Flyway/mutation path;
- staging-only `bloom-private-split` Paper authority transport with private/loopback source fencing, signed replay-resistant requests/responses, and private-host resolution pinning;
- Staff Bot supervision of panel-uploaded `cloudflared` using a tunnel-token file while the read origin remains loopback-only;
- simulation-only punishment, deletion, and permission-override controls.

## Live-server safety bridge checkpoint
After normal reconciliation with canonical `main` `9d0413ec17c73977fc5dc00bb93f3339c473fcb0`, PR #187 advanced to checkpoint `f7a7159610718b0de161308d24f3303b472cb340`.

That checkpoint adds `paper-authority-bridge`, a separate deployable Paper plugin whose descriptor declares only a hard LuckPerms dependency and **zero commands/permissions**. Runtime code consists only of runtime-file validation, private-peer signed/replay-resistant HTTP authentication, LuckPerms rank resolution, a bounded two-thread HTTP endpoint, and clean shutdown. It contains no database/runtime moderation integration and does not register listeners.

The Sentinel artifact workflow now publishes this bridge separately as `enthusiastaff-authority-bridge` while preserving the existing full Paper artifact. Preliminary exact-head Sentinel artifact run `33712473412` passed and published bridge artifact `9877393964` on exact `f7a7159610718b0de161308d24f3303b472cb340`. Full exact-head validation/review is still in progress and this checkpoint must not be deployed until the final head is frozen and all applicable gates are green.

## Historical exact-head validation — PASS
The pre-exception executable head `066b97f4344ab83d3e226b3f4ff3ab614dee6430` passed:

- Coverage/full Java 21 `33683792916` / job `100426714267`: full clean build/integration tests, 27 provider API source types / zero runtime leaks, JaCoCo 52.08% lines / 42.18% branches / 54.38% instructions; validation artifact `9867619687`, digest `sha256:c52610d6913e85d80f8397fc898344f0b530e979adb42f7439346168687e34fb`.
- Moderation Web Validation `33683792884`.
- Staff Bot Configuration Cache `33683792893`.
- Staff Bot PR Artifact `33683792982` / job `100426291034`; artifact `9867301625`; JAR SHA-256 `f546bbb418e4d38b3f1a1eea3f4621739bd6d1e75351c9cd73f0ce39e1056b60`.
- Sentinel Restart Artifact `33683792967` / job `100426290606`; Paper JAR SHA-256 `0bc62c09742fe0eae96a1725e52a64756a761bf134023da5ce71438de6627944`.
- Codacy Static Code Analysis with zero annotations/no new valid findings.
- Exact-head CodeRabbit with no actionable findings and all historical correctness threads resolved.

## Historical protected Cloudflare staging — PASS
Guarded dispatcher `33688117871` verified `main` `44f284606813d133b6b2813cdc6cbe8924c5d7af` and exact D16 head `066b97f...`. Permanent staging run `33688133318` / job `100440387112` passed tunnel/DNS provisioning, 14 moderation-web tests/Wrangler validation, Worker deployment `5fb4931b-65a7-4df7-9444-ad354323e228`, origin/session/replay fences, and simulation-only mode. Raw Discord bot credentials were not uploaded to Cloudflare.

This evidence remains historical after the bridge/code head changed. Any executable change that affects applicable staging behavior requires fresh exact-head evidence under the normal validation policy.

## Remaining acceptance
Before any live-server deployment:

1. finish exact-head Java/Codacy/CodeRabbit/artifact validation for the authority-bridge head;
2. verify the bridge artifact source/checksum and descriptor contains no commands or listeners;
3. configure only runtime secret/port files on the live Paper server and keep port `8771` non-public;
4. configure the staging Staff Bot to use the live Paper server's Bloom-private hostname with `authority.transport=bloom-private-split` and the matching authority authentication value;
5. start/reload the bridge and staging Staff Bot in a controlled window;
6. perform sanitized acceptance proving private authority connectivity, actor/guild/target authorization, real identity/link/sanction/history reads, bounded Discord message/channel reads, truthful outage behavior, and no destructive moderation action;
7. remove/disable the temporary bridge after acceptance if it is no longer needed;
8. reconcile moving `main`, rerun any invalidated gates, merge PR #187 normally only after acceptance, prove containment/cleanup, and publish `COMPLETE`.

## Explicit exclusions still in force
The owner production exception does **not** authorize warn/mute/kick/ban/restrict/unmute/unban/unrestrict mutation, Discord message deletion, permission-override application, LiteBans authority change, issue #43 acceptance, cutover, broad production data export, secret disclosure, or player-facing experimentation. ES-D13 PR #178 and ES-X03 PR #139 remain separate and untouched. Do not begin D07 as part of this D16 worker.
