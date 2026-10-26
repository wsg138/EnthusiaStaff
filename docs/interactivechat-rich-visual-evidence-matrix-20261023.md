# IC-01–IC-14 visual evidence sheet — source-only proposal

Status: **NOT EXECUTED / NOT ACCEPTED** (2026-10-23). Source reference: #268, draft #568.
This is an operator checklist, not permission to change live servers, send messages, remove DiscordSRV,
or start an isolated TEST peer. The private StaffBot coordinator owns all staging actions.

## Entry gates (coordinator approval required)

1. Confirm that the isolated `STAFFBOT_TEST` peer exists and is authenticated, with no fallback to
   the production `STAFFBOT` peer. Verify the chat-only JDA session, private channel permissions,
   `TEST/global` allowlist and disabled Discord ingress. PR #658 alone does **not** create the peer.
2. Re-run the current real-config private preflight; no blocked checks. Confirm the shared TEST
   server is free for a maintenance window and take test-only config, plugin and database
   snapshots for rollback. Source-only code review and sanitized preflight are insufficient.
3. Verify the test DiscordSRV instance cannot post chat, console, announcements, or any other
   output to a production destination; keep live SMP DiscordSRV and `#in-game-chat` unchanged.
   Restrict test sender identities and all item contents to synthetic non-sensitive data.
4. Record exact source commit, hashes of Paper/StaffBot/Velocity JARs, active addon and IC
   versions, relevant module flags/regex/permission nodes, and test mode. Begin with
   `SHADOW` and native fallback **off**. Do not enable `AUTHORITATIVE`.
5. Obtain a reference image from existing **production** DiscordSRV + InteractiveChat
   rendering without posting any test message into production. Compare that read-only
   reference with the private test channel's StaffBot output. If the reference scenario cannot
   be recreated without sending to production, mark the visual comparison BLOCKED.

## Screenshot and evidence protocol

For each case, record UTC time, case ID, build/hash, synthetic scenario, effective provider
(addon/native/none), placeholder expression and permission, event/correlation ID (redacted),
Minecraft local-chat observation, private-channel message and expanded-image screenshots,
service registration count, bounded render diagnostics, and PASS/FAIL/BLOCKED/N/A.
For image scenarios, save **two side-by-side captures at matched client zoom**:
**A** = legacy addon baseline (`#in-game-chat` read-only); **B** = StaffBot private
`#staff-bot-testing` channel. Also capture an expanded tooltip so text, glyphs, enchantments,
attributes, durability and color can be compared. Record explicit, approved differences
rather than treating valid PNGs as pixel parity. For missing-addon cases, use addon A
as the visual-quality reference, but expect intentionally reduced native fidelity in B.

| ID | Staging stimulus and required screenshot/evidence | Passing result / fail condition |
|---|---|---|
| IC-01 | Native flag off, addon enabled; item with custom name/lore. A/B embed + full tooltip and service inventory | Addon icon/tooltip selected; no native card, orphan attachment or duplicate service |
| IC-02 | Native flag on, addon absent, IC present; item, full inventory, Ender chest. B three expanded cards; A baseline for quality | Text and bounded material/slot PNGs; explicit **degraded** quality accepted, no missing item metadata falsely claimed |
| IC-03 | Flag on, both providers enabled; repeat IC-01 with service inventory before and after reload | Addon wins consistently; no duplicate providers, no downgrade on re-registration |
| IC-04 | IC absent/disabled or malformed placeholder API; B message + plugin lifecycle log | Exactly one readable text message, no image, no startup failure |
| IC-05 | Revoke each of item/inventory/Ender permissions separately; B + authorization log | No denied-kind artifact or inventory access; public text unaffected |
| IC-06 | Escaped, case-changed and custom-regex tokens; A/B text capture | Only truly configured, unescaped tokens generate artifacts; no raw transport marker leakage |
| IC-07 | Full 54 slots and synthetic item with long lore/model data; B expanded image + size log | PNG/wire size limits respected; missing fidelity explicitly annotated, no crash |
| IC-08 | Change item between send and player-thread callback; record before/after synthetic inventory and B image | Observed content is **capture-time**, not claimed send-time; missing or changed preview documented |
| IC-09 | Quit/transfer, disable addon and close provider while work is queued; B + service count and log | No stale image after hard expiry/close; pending work resolves text-only; no service leak |
| IC-10 | Saturate/reject worker and simulate encoder/render fault; B + bounded queue log | No stuck chat or late image, one text message, bounded backpressure |
| IC-11 | Simultaneous distinct synthetic inventories on multiple players; B artifact/event mapping | Correct owner/event correspondence, no cross-player image |
| IC-12 | Send staff/private/DM and denied/muted public chat on TEST; route log, B absence | No public StaffBot artifact/frame; moderation and channel boundaries preserved |
| IC-13 | Take isolated TEST Discord transport offline/reconnect; local Minecraft chat + private-channel timeline | Minecraft chat continues, no old replay, duplicate, or mixed routing |
| IC-14 | Matched item thumbnail + native tooltip, inventory, Ender and attachments; A/B at same zoom | Coordinator signs off any visual differences; source/unit checks are not substitute for this approval |

## Strict fail/rollback

**Immediately stop the isolated test bridge** for an unintended production post/mention,
cross-player or private inventory leak, chat disruption, unexpected role/console effect,
duplicate or stale delivery, unapproved fidelity regression, or unbounded queue.
Restore only the recorded TEST configs/JARs and isolated peer changes through authorized
maintenance. Confirm normal TEST Minecraft chat and the existing production DiscordSRV
path still work. Leave `#268` and `#568` open and visual status **BLOCKED** until
all required evidence exists. Addon/DiscordSRV removal remains a separate cutover gate.

## Source-level residual limit

The Folia player scheduler captures inventory **after** the chat event. A two-second
freshness cutoff rejects late captures but cannot guarantee exact send-time contents:
changes occurring within that window are observed by the scheduled callback.
To guarantee event-time state, the canonical Minecraft chat path must carry an
immutable, authorized item/inventory snapshot taken on the player's owning thread
at the event boundary, before the asynchronous relay; a background provider cannot
retroactively reconstruct that state. This requires separate architecture review.
