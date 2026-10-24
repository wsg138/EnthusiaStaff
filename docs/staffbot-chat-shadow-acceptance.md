# StaffBot private SHADOW chat acceptance

Tracking: #268, #577, #578. This is a **controlled staging checklist**, not approval to change production chat routing, restart the SMP, or remove DiscordSRV.

## Before any remote change

1. Record exact merged Git commit, SHA-256 of both Paper and StaffBot JARs, plus running versions of Paper, Velocity, RoseChat, InteractiveChat, InteractiveChatDiscordSrvAddon, and DiscordSRV. Preserve the current working JARs/configuration for rollback.
2. Use a **separate staging StaffBot Discord application** and the pinned private `#staff-bot-testing` channel. Set StaffBot `ENTHUSIA_STAFF_BOT_ENVIRONMENT=staging` and `ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_MODE=SHADOW`. Set Paper `discord-chat-bridge.mode: SHADOW` only in a controlled environment. Validate authenticated peer/routing configuration without printing secrets.
3. All outbound routes must target only the private test channel. Enable an inbound route only for a deliberate reverse-direction test, with exact matching target backend and logical channel. Message Content intent is needed for inbound routes.
4. Keep the existing DiscordSRV production bot, chat path, and other DiscordSRV functions active. Do not set AUTHORITATIVE or any cutover acknowledgement. Do not restart live SMP/Velocity as part of this checklist.
5. For each test, keep the original Minecraft text/placeholder, timestamp, the **legacy** DiscordSRV output, StaffBot's private-channel output, and (for images) a screenshot of the expanded tooltip. Do not record passwords, HMAC keys, bot tokens, or private chat.

## Test cases and required behavior

| ID | Send/action in controlled staging | Required observable result |
| --- | --- | --- |
| S01 | Unlinked public Minecraft chat | Exactly one StaffBot message formatted `MinecraftUsername: text`; no rank/reset/server prefix |
| S02 | Linked Minecraft **main** account | Exactly one message formatted `@DiscordUser: text`, pinging only that verified user |
| S03 | Linked alternate Minecraft account | `MinecraftUsername: text`, **not** the linked main's Discord mention |
| S04 | Linked account without a selected main | Minecraft username only |
| S05 | Change main account or revoke the Discord link, then send immediately | No stale mention of the former main/Discord account; test within the old 30-second cache window |
| S06 | Body containing literal `@everyone`, `@here`, role/user tags, and Discord Markdown | No unintended user, role, or everyone notifications; text is readable |
| S07 | RoseChat color codes, `<reset>`, rank decorations, and InteractiveChat internal tokens | No raw control codes, rank prefixes, or transport tokens in final message |
| S08 | Held item with a custom name, enchantments, lore, attributes and durability | Item thumbnail and full tooltip image appear as a proper Discord embed; compare with legacy renderer |
| S09 | Two item placeholders in one message | Matched item/tooltip pairs, no mismatched attachment or orphan image |
| S10 | Escaped item placeholder | No item artifact created for the escaped occurrence |
| S11 | Inventory and Ender-chest placeholders | PNG embeds for each available preview; correct contents and no live-state/threading errors |
| S12 | Map or other provider PNG preview (if supported) | Valid preview is retained as an embed, not silently dropped |
| S13 | Oversize/missing/erroring tooltip renderer | Readable chat stays available; no duplicate outbound message |
| S14 | Missing attachment permission in isolated test channel | Text-only fallback; no duplicate |
| S15 | Discord-to-Minecraft inbound test (only if explicitly enabled) | Exactly one normalized RoseChat message; no echo back to Discord |
| S16 | StaffBot disconnect/route failure or queue saturation in controlled staging | Minecraft chat remains available; no delayed replay or duplicate after reconnect |
| S17 | Private/non-public Minecraft channel | No export to the StaffBot public-chat route |
| S18 | Offline/unverified identity storage | Sender fails closed to Minecraft name; no speculative Discord mention |
| S19 | Discord account-age bypass logic, in a disposable guild only | Authorized bypass persists; never test destructive join/kick behavior on the live guild |

## Gate and evidence

Record **PASS, FAIL, BLOCKED, or NOT APPLICABLE** against each ID with a screenshot/log reference, actual plugin versions, UTC timestamp, and tested build hashes. `BLOCKED` is not a pass. Compare full tooltip appearance (custom resources, item art, lines, and colors) against legacy DiscordSRV; successful source compilation alone does not prove visual parity.

**Stop** if the private test emits to a production channel, any unintended Discord mention fires, a duplicate post appears, a plugin restarts unexpectedly, or Minecraft chat is affected. Disable the staging bridge and restore the previously recorded configuration/JARs only through the normal authorized maintenance process. Keep DiscordSRV in service until every cutover owner and separate subsystem is validated.

See `docs/discord-chat-cutover.md` for the authoritative-cutover requirements and rollback sequence.