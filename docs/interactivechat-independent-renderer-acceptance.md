# InteractiveChat independent renderer: acceptance and rollback

Status: **review gate only; no production approval**. Companion to #268 and draft #568 (source reviewed through 2026-10-20; staging screenshots and lifecycle acceptance still pending). This document is deliberately separable from the renderer implementation and contains no production settings or secrets.

## Architecture and decision

The proposed Paper-side `IndependentRichChatArtifactProvider` discovers InteractiveChat's active item, inventory, and Ender chest placeholder expressions. It checks sender permissions, captures the corresponding material/count/slot snapshot on the player's Folia entity scheduler, then renders Java2D PNG cards using a worker executor. It uses no DiscordSRV or addon Java classes. The existing `RichChatArtifactService` adapts optional PNGs into a bounded wire bundle. The outbound RoseChat render bridge accepts **only public Minecraft-origin messages** before invoking that service and sends from its own bounded worker, so Minecraft chat must remain independent of image/Discord availability.

This is **functional placeholder coverage**, not visual equivalence. A successful unit test cannot establish production item fidelity or actual addon removal readiness.

| Feature | Legacy addon | Independent proposal #568 | Acceptance evidence needed |
|---|---|---|---|
| Item placeholder | Rendered Minecraft item image | Material name and stack count card | Check configured pattern and item permission; inspect a simple and custom item |
| Inventory placeholder | Textured inventory layout | Up to 54 named/numbered slots | Inspect all slots, equipment/offhand positioning, empty rows and holder permissions |
| Ender chest | Rich Ender chest preview | Material/count slot card | Test owner permission and live 27-slot chest |
| Textures, resource packs | Addon-specific textured art | **Not implemented** | Explicit operator acceptance or future implementation |
| Lore, enchantment glint | Addon-specific visual enrichment | **Not implemented** | Explicit acceptance; ensure no private lore details are invented |
| Custom model data | Addon may use custom art | **Not implemented** | Test highly customized items and document degraded quality |
| Attachments and delivery | Existing addon transport | PNG wire bundle and text frame | Verify same-event ID, ordering, bounds, and Discord presentation |
| DiscordSRV dependency | Hard dependency in upstream addon | No addon classes imported in native provider | Physically stage without addon and DiscordSRV; check complete plugin inventory before any uninstall |

## Risk findings in draft #568

1. **Addon preference is fixed in draft source, not integration-accepted.** Draft #568 now discovers and registers the installed addon before considering the independent fallback, matching the provider Javadoc. IC-01/IC-03 still require addon-present/absent tests, including runtime re-registration, to prove there is no quality downgrade or duplicate service.
2. **The lifecycle paths need integration tests.** `refreshInteractiveChatRenderer()` closes and re-registers services; disabling InteractiveChat closes both; disabling the addon closes both and tries native registration. Tests must prove there is never an orphan or duplicate service registration and that service routing works after enable/disable/quit/shutdown.
3. **Snapshot freshness is not send-time fidelity.** Inventory is read on the later Folia entity callback, after which the snapshot is immutable. Draft #568 now drops captures at/after 2 seconds from the event (or hard expiry), and rechecks online status and permissions. A snapshot captured promptly can finish rendering until hard message expiry, but changes within that 2-second window may affect content. IC-08/IC-09 still require live testing.
4. **Reflection/permission correctness is unproven against real plugin binaries.** `getKeyword()` patterns, module flags and permission nodes must be verified on the pinned InteractiveChat build. Expanded draft regression tests cover immutable capture, permissions, quit, expiration and worker failures, but do not instantiate a real Bukkit service or exercise a live Folia player scheduler.
5. **Missing plugin/worker failures must be observable without disrupting text.** Draft tests exercise provider close, late capture, hard expiry, queue rejection, generic worker failure and single queued execution; acceptance still must verify operator diagnostics and real text-only delivery.

## Synthetic acceptance matrix

Perform these cases on an isolated Paper/Folia test server with no production tokens or public Discord routing. Record each case ID, exact plugin/JAR hashes, permissions, player/channel, placeholder configuration, artifacts, text delivery, logs, and whether the service registered.

| ID | Condition | Expected |
|---|---|---|
| IC-01 | Flag defaults off; addon installed | Existing addon behavior unchanged |
| IC-02 | Opt-in on; no addon; InteractiveChat enabled | Native PNG card + ordinary public text |
| IC-03 | Opt-in on; addon also enabled | Deterministic documented precedence; no duplicate services |
| IC-04 | InteractiveChat missing/disabled or bad reflection API | No PNG; normal public text, no fatal startup |
| IC-05 | No item/inventory/Ender permission for sender | No artifact for denied kind; public text intact |
| IC-06 | Escaped placeholder, alternate-case placeholder, custom regex | No artifact for escaped text; configured patterns only |
| IC-07 | Oversized custom item, extreme lore/model data, full inventory | Bounded snapshot/PNG; no crash or unbounded data |
| IC-08 | Inventory changes between message and scheduled capture | Defined snapshot timing; no cross-player or inconsistent read |
| IC-09 | Player quits or transfers, plugin disables, provider closes mid-request | No stale PNG after expiry; no lingering service or crash |
| IC-10 | Worker saturated/rejected, ImageIO failure, unfinished render | Safe text-only fallback; queue remains bounded |
| IC-11 | Concurrent requests from several players | Correct event ownership; no cross-player contents |
| IC-12 | Staff/private/DM/muted/blocked chat or wrong server route | No public artifact or frame, canonical moderation/routing remains enforced |
| IC-13 | Discord transport unavailable, slow ACK, reconnect | Minecraft chat remains responsive; stale artifacts are not replayed |
| IC-14 | Client presentation of item, inventory, Ender chest and attachment | Real Discord display inspected and differences accepted in writing |

Acceptance must cover success, rejection, rollback and authorization, with exact-head tests and analyzers; local Java2D PNG tests alone are insufficient.

## Reversible staging and owner gates

1. Keep `discord-chat-bridge.independent-rich-renderer-enabled: false` by default. Confirm plugin/JAR inventory and clean baseline. Never remove DiscordSRV from production for this test.
2. Enable only in an isolated channel/server with synthetic player identities, deliberately scoped sender permissions and existing chat transport in **SHADOW**. No public **AUTHORITATIVE** or role/console writer cutover.
3. Run IC-01 through IC-14, including restart/reload and failure injection. Capture screenshots and bounded message/attachment evidence without revealing real inventories or credentials.
4. On any leak, chat disruption, renderer fault or quality rejection: set the opt-in flag back to `false`, disable the candidate on staging, restore the prior verified artifacts, and revalidate normal chat.
5. Primary coordinator approves integration of #568 only after the PR head has relevant tests, build/analyzer checks, hosted Codacy with zero new valid findings, and review-thread closure. DiscordSRV uninstall is a separate umbrella #264 decision requiring every dependent plugin's parity and deployment proof.

The accompanying `RichChatAcceptanceGuardTest` tests the **already-existing public relay boundary on main**, including staff-channel rejection, optional provider failure/timeout, and oversize fallback. It is not a substitute for #568's missing Folia/player/inventory integration tests.
