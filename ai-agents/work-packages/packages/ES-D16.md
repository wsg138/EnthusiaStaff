# ES-D16 — Moderation console real-data read bridge

Status: `BLOCKED` / `PARKED_BLOCKED`. Priority: 135.5. Depends on `ES-D03`, `ES-D05`, `ES-D06`, and merged web-first moderation foundation PR #186. Internal package.

Run ref: `ES-D16-20260831-real-data-read-bridge`.

## Objective
Connect the owner-approved Cloudflare moderation console to real, read-only Enthusia Discord/Minecraft/moderation data while preserving simulation-only punishment/deletion behavior and the existing D03 authority model.

## Delivered implementation
Frozen reviewed executable head `a009f4f5f857cf86a859be0d314264568d181670` on `package/es-d16-moderation-read-bridge` / PR #187 implements:

- real selected-target identity, linked-account, active-punishment, total-history, and offense-relevant-history reads through existing D06/domain services;
- a narrow loopback moderation read API with explicit response DTO allowlists, server-side D03 authorization, actor/guild/target binding, HMAC service requests, expiry/replay resistance, bounded body/page/rate limits, and private no-store responses;
- target-bound hosted Discord user/message launch tickets and Worker session ingress/proxying without browser access to internal service credentials;
- bounded on-demand JDA channel/category/message REST reads with permission fences, pagination, filters, replies/references, attachments, edited timestamps, and exact message context;
- real channel/category data for the staged restriction picker while keeping all punishment, deletion, and permission-override behavior simulation-only;
- deterministic security/adapter/rendering regression tests for authorization, replay, malformed requests, limits/filters, target binding, service failure, and destructive-route absence.

## Explicit exclusions
D16 performs no warn/mute/kick/ban/restrict/unmute/unban/unrestrict mutation, Discord message deletion, LiteBans/Minecraft/Paper/moderation-database mutation, permission-override application, production Discord configuration change, production-data access, issue #43 acceptance, or cutover.

## Frozen exact-head validation
Executable head `a009f4f5f857cf86a859be0d314264568d181670` is frozen. Repository-controlled gates are terminal:

- Coverage/full Java 21 `33529631291` / job `99929426510`: `PASS`; `./gradlew clean build jacocoAggregateReport runtimeJars` completed successfully with full integration tests, runtime inspection of 27 provider API source types / zero leaks, JaCoCo 51.70% lines / 41.74% branches / 54.02% instructions, and validation artifact `9809522236` digest `sha256:00f970e5dc8a3d2823d4dae4d226be1cff8d71abcf601658f2405eb383aef1d6`.
- Moderation Web Validation `33529631190`: `PASS`.
- Staff Bot Configuration Cache `33529631174`: `PASS`.
- Staff Bot PR Artifact `33529631246`: `PASS`.
- Sentinel Restart Artifact `33529631254`: `PASS`.
- Codacy Static Code Analysis `99929501999`: `PASS`, zero annotations.
- Codacy Diff Coverage `99932381673`: `PASS`, 33.57%, no repository gate defined.
- CodeRabbit exact-head review `5080380849`: bound to `a009f4f5f857cf86a859be0d314264568d181670`, no new findings. The three earlier correctness findings (no-channel filters/limit, message-target author validation, malformed JSON 400 handling) were repaired with regression coverage and every visible thread is resolved/confirmed addressed.

Missing, historical failed, queued, superseded, different-head, or diagnostic evidence is not called passing.

## Required staging — BLOCKED
Exact-head moderation staging run `33530157844` / job `99930994457` checked out `a009f4f5f857cf86a859be0d314264568d181670` and passed Worker build/deploy, fixed staging origin health/private fence, first-use protected launch, authenticated session access, and replay rejection. The Worker deployment version was `932b8255-f49a-4ab9-abc8-4152d675f845`.

The first account-level Cloudflare Tunnel API request (`GET /accounts/<account>/cfd_tunnel`) returned HTTP 403. Therefore no named-tunnel configuration or DNS mutation succeeded. The workflow correctly failed the mandatory `Require fixed private tunnel provisioning` gate. This is non-passing product/staging evidence, not zero-execution infrastructure unavailability, and it must not be bypassed or relabeled.

## Exact unblock and resume
Grant or replace the protected staging Cloudflare API token with the required Cloudflare Tunnel account read/edit authority for the owning account. Then:

1. reconcile live `main`, PR #187, and the unchanged implementation head;
2. rerun required exact-head moderation staging through the fixed private tunnel;
3. complete sanitized Bloom/live read acceptance, including the staging Discord application Message Content privileged-intent state required by the package contract;
4. repair any newly discovered valid finding and rerun invalidated exact-head gates if executable state changes;
5. merge PR #187 normally only after every required gate passes, then prove containment/cleanup and publish `COMPLETE`.

Until that condition changes, preserve PR #187 and its implementation branch unmerged. Do not begin destructive D07 work as part of D16 continuation.

Canonical blocked handoff: `ai-agents/reports/package-handoffs/2026-09-01-es-d16-cloudflare-tunnel-blocked.md`.
