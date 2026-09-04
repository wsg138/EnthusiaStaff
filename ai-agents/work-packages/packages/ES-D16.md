# ES-D16 — Moderation console real-data read bridge

Status: `BLOCKED` / `PARKED_BLOCKED`. Priority: 135.5. Depends on `ES-D03`, `ES-D05`, `ES-D06`, and merged web-first moderation foundation PR #186. Internal package.

Run ref: `ES-D16-20260831-real-data-read-bridge`.

## Objective
Connect the owner-approved Cloudflare moderation console to real, read-only Enthusia Discord/Minecraft/moderation data while preserving simulation-only punishment/deletion behavior and the existing D03 authority model.

## Delivered implementation
Frozen reviewed executable head `066b97f4344ab83d3e226b3f4ff3ab614dee6430` on `package/es-d16-moderation-read-bridge` / PR #187 implements the full read bridge plus panel-only Bloom staging transport:

- real selected-target identity, linked-account, sanction/history, case/note, channel/category, and bounded Discord-message reads through existing D06/domain authority;
- loopback-only moderation read API `127.0.0.1:8766` with explicit DTO allowlists, D03 authorization, actor/guild/target binding, HMAC authentication, expiry/replay resistance, and bounded body/page/rate controls;
- target-bound hosted Discord launch tickets and Worker session ingress/proxying without browser access to internal credentials;
- bounded JDA REST reads with staff view/history permission fences, filters/pagination, replies/references, attachments, edited timestamps, and exact message context;
- file-backed Bloom Staff Bot/Paper configuration preserving one authoritative MariaDB and no Staff Bot Flyway/mutation path;
- staging-only `bloom-private-split` Paper authority transport with private/loopback source fencing, signed replay-resistant requests/responses, and private-host resolution pinning;
- Staff Bot supervision of panel-uploaded `cloudflared` using a tunnel-token file while the read origin remains loopback-only;
- simulation-only punishment, deletion, and permission-override controls.

## Explicit exclusions
D16 performs no warn/mute/kick/ban/restrict/unmute/unban/unrestrict mutation, Discord message deletion, LiteBans authority change, moderation-database mutation, permission-override application, production Discord/Minecraft configuration or data access, issue #43 acceptance, or cutover.

## Frozen exact-head validation — PASS
Exact executable head `066b97f4344ab83d3e226b3f4ff3ab614dee6430` is frozen:

- Coverage/full Java 21 `33683792916` / job `100426714267`: PASS; full clean build/integration tests, 27 provider API source types / zero runtime leaks, JaCoCo 52.08% lines / 42.18% branches / 54.38% instructions; validation artifact `9867619687`, digest `sha256:c52610d6913e85d80f8397fc898344f0b530e979adb42f7439346168687e34fb`.
- Moderation Web Validation `33683792884`: PASS.
- Staff Bot Configuration Cache `33683792893`: PASS.
- Staff Bot PR Artifact `33683792982` / job `100426291034`: PASS; artifact `9867301625`; JAR SHA-256 `f546bbb418e4d38b3f1a1eea3f4621739bd6d1e75351c9cd73f0ce39e1056b60`.
- Sentinel Restart Artifact `33683792967` / job `100426290606`: PASS; artifact `9867310817`; Paper JAR SHA-256 `0bc62c09742fe0eae96a1725e52a64756a761bf134023da5ce71438de6627944`.
- Codacy Static Code Analysis: PASS, zero annotations/no new valid findings.
- Exact-head CodeRabbit: no actionable findings; all historical correctness threads remain resolved.

## Protected Cloudflare staging — PASS
Guarded dispatcher `33688117871` verified `main` `44f284606813d133b6b2813cdc6cbe8924c5d7af` and exact D16 head `066b97f4344ab83d3e226b3f4ff3ab614dee6430`. Permanent staging run `33688133318` / job `100440387112` then passed on exact `066b97f4344ab83d3e226b3f4ff3ab614dee6430`:

- tunnel `enthusia-moderation-read-staging` and protected CNAME configured;
- ingress remains `moderation-read-staging.enthusia.info` → `http://127.0.0.1:8766` with fail-closed 404 fallback;
- 14 moderation-web tests and Wrangler dry-run passed;
- Worker version `5fb4931b-65a7-4df7-9444-ad354323e228` deployed;
- origin health/private fence, first-use launch, authenticated session, and replay rejection passed;
- runtime remained staging simulation-only and the raw Discord bot token was not uploaded to Cloudflare.

The workflow's `Require fixed private tunnel provisioning` step is failure-only and was correctly skipped because provisioning succeeded. Historical run `33530157844` remains truthful non-passing HTTP-403 history but is no longer the blocker. Staging Discord Message Content entitlement is verified present; D16 still does not subscribe to the Message Content Gateway intent.

## Current blocker — owner-operated Bloom live acceptance
No authenticated Bloom/DuckPanel mutation surface is available to this worker. Exact unblock:

1. deploy the exact validated Staff Bot and Paper artifacts to the authorized non-production Bloom staging splits;
2. configure runtime-only database/private-authority/component/token/tunnel files per `docs/staff-bot-staging-ui-preview.md`;
3. keep ports `8766` and `8771` non-public and keep both splits in the same private split group;
4. start/restart staging Paper first, then Staff Bot;
5. complete sanitized live acceptance proving private authority connectivity, actor/guild/target authorization, real D06 identity/link/sanction/history reads, bounded Discord message/channel reads, and truthful outage behavior without exposing private values;
6. if acceptance passes, reconcile moving `main`, rerun invalidated gates if executable state changed, merge PR #187 normally, prove containment/cleanup, and publish `COMPLETE`.

Until that condition changes, preserve PR #187 and its implementation branch unmerged. Do not begin D07 as part of this D16 worker.

Canonical blocked handoff: `ai-agents/reports/package-handoffs/2026-09-02-es-d16-bloom-live-acceptance-blocked.md`.
