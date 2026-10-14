# DiscordSRV retirement — owner preflight

Status: **PREPARATION ONLY** (2026-10-14). DiscordSRV remains live. This page separates Discord settings that can be inspected today from future, coordinated deployment and cutover. It is NOT permission to disable DiscordSRV.

## 1. Discord configuration to verify now

1. **Correct app:** Find the production StaffBot application in the Discord Developer Portal. Confirm it belongs to the intended guild. Never paste tokens or private configuration into chat/issues.
2. **Slash commands:** On Discord desktop open **Server Settings → Integrations → StaffBot → Manage**. StaffBot moderation commands use disabled default permissions: explicitly grant them in private channels to the required staff roles, not @everyone.
3. **Reports:** Allow **/review-queue** in private staff channels. Helpers/Developers may select only **view:reports** at the bot rank gate; only qualified reviewers should see punishment/alt queues. Discord command visibility never grants Minecraft moderation permission.
4. **Approvals:** Allow **/review-request** only to intended Mod+ reviewers. Also review **/moderate**, **/punish**, **/linked**, **/history**, **/notes**, and configured investigation/console commands individually.
5. **Channels:** Verify staff-only visibility for report reviews, punishment records, staff logs, and alt alerts. Preserve a separate private test channel. Existing approved channels may be reused. Regular members must not read reporter identities or staff evidence.
6. **Permissions:** Verify View Channel, Send Messages, Read Message History and Use Application Commands where needed. Rich chat messages later need Embed Links and Attach Files. Bot role hierarchy must permit only specifically authorized role/kick/ban effects, not Administrator by default.
7. **Intents:** Discord Developer Portal → Applications → correct app → Bot. The production **21-day account-age gate** needs Server Members Intent and Kick Members permission. Discord→Minecraft incoming chat/investigation content needs Message Content Intent; simple report slash commands do not.
8. **Account-age policy:** A new production StaffBot build defaults to kicking newly joined human Discord accounts younger than 21 full days unless explicitly disabled or an exact-ID bypass applies. Confirm intended policy and durable bypass file BEFORE any StaffBot-only update. Do not perform a live join/kick test.

For each item record PASS, BLOCKED or NOT RUN and a sanitized screenshot, not token text or player identities.

Official permissions guide: https://support-apps.discord.com/hc/en-us/articles/26501869403159-Command-Permissions

## 2. Audits possible without SMP/Velocity changes

- Back up the existing DiscordSRV configuration and capture its actual role mapping and chat/console routing; do not disable live handlers.
- Inventory all DiscordSRV consumers: InteractiveChat-DiscordSRV addon, LumaGuilds, PlayTime numeral roles, Staff link compatibility, console, RoseChat and any other server plugins. DiscordSRV cannot be removed while active consumers require its classes.
- Identify role ownership and which roles DiscordSRV vs LumaGuilds vs PlayTime currently manages; avoid future unauthorized role removal.
- ES-D13 replacement PR #178 was **closed unmerged**. Its parity tests and production role cutover are not done. Run replacement reconciliation only in approved staging SHADOW mode before any production enablement.
- Prepare test staff accounts (Helper, Developer, Mod+) and ordinary/linked-primary/linked-alt/unlinked users, without making live punishments or leaking their identities.
- Inspect but do not change the private StaffBot authority/TLS/DB requirements; confirm which endpoints the **currently running Paper and Velocity JARs** provide.

## 3. StaffBot-only update (later, separately authorized)

1. Select the exact source SHA of a reviewed merged build, verify SHA-256 of the StaffBot executable, and check Paper/DB API compatibility.
2. Back up the existing root StaffBot JAR, runtime private secrets/config and persistent account-age bypass file.
3. Stop **StaffBot only**. Replace only the StaffBot JAR and restart only StaffBot. SMP, Velocity and DiscordSRV remain unchanged.
4. Verify startup app/guild identity fence, health/readiness, private slash commands and read-only behavior. Do not enable destructive Discord enforcement, role-sync ENFORCE, or AUTHORITATIVE chat as part of this upgrade.
5. On failure, stop the new bot and restore the previous executable. Never bypass authentication to make it start.

See [StaffBot README](../staff-bot/README.md) for startup options.

## 4. Mandatory gates before disabling DiscordSRV

- Deploy matching accepted **Paper, Velocity and StaffBot** builds with properly authenticated STAFFBOT peer, fresh readiness lease and exact symmetric channel routing.
- Complete private **SHADOW** bidirectional Minecraft↔Discord chat acceptance: no echo/duplicates, safe sender-only mentions, linked primary vs alt, revoked links, no private channel leaks, source colors/styles, InteractiveChat item/inventory/Ender-chest PNGs, attachment failure fallback, reconnect/outage/queue behavior.
- Validate actual signed StaffBot↔Paper report buttons, punishment approvals and offline permissions with disposable accounts; a local unit test does not prove production authority.
- Verify Velocity's four independent report/punishment/staff/alert webhooks deliver and retry correctly. Source fix for MariaDB 1020 must still be validated after deployment.
- Migrate/accept **all** DiscordSRV responsibilities: roles, console, links, guild/numeral plugins, chat and InteractiveChat addon before uninstalling DiscordSRV.
- Obtain separate explicit owner signoff and a proven rollback. Never remove DiscordSRV just because chat is bridged.

Existing detailed procedures: [Chat cutover](discord-chat-cutover.md), [Shadow acceptance](staffbot-chat-shadow-acceptance.md), [Role sync](discord-role-sync.md), [Webhook delivery](discord-delivery.md).

## Unverified as of this source audit

Live Discord command ACLs/intents/role hierarchy, live full rich-render parity, real report button/authority actions, independent role/console migrations, and durable webhook recovery are **NOT RUN**. Source/CI passing or a successful StaffBot login does not establish those facts.

Tracked under [DiscordSRV program #264](https://github.com/wsg138/EnthusiaStaff/issues/264) and [chat migration #268](https://github.com/wsg138/EnthusiaStaff/issues/268).
