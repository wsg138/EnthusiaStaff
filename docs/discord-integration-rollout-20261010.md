# Staff Discord Notifications — staged configuration and acceptance

Date: 2026-10-10. **Status: implementation checkpoint, NOT production approval.**
Tracked under [#484](https://github.com/wsg138/EnthusiaStaff/issues/484) and draft [#483](https://github.com/wsg138/EnthusiaStaff/pull/483).

## Current routing contract (Velocity private webhook outbox)

| Destination | Suggested private channel | Produced events |
|---|---|---|
| `logs-staffmode` | `#staff-logs` | Staff mode entry/exit, vanish events, and StaffActionLogger's actual command/teleport/game-mode/container/world actions |
| `punishments` | `#in-game-punishments` | Punishment created, modified, inherited, reversed; request lifecycle **audit** |
| `alerts` | `#staff-reviews` | New `PUNISHMENT_APPROVAL_REQUIRED`; `ALT_EVASION_REVIEW` from a same-network peer on login with a live sanction; `ALT_MUTED_CHAT_ATTEMPT` when an inherited-muted account attempts chat; delivery-health events |
| `reports` | `#staff-reviews` or a private `#reports` | Report-created and report-updated events (existing reporter-safe presentation) |

**Every route must be configured** when the Velocity webhook outbox worker is enabled; it currently requires all four destination URLs. The existing `#reports` Discord channel being obsolete does not disable the `reports` transport destination. Point it to an approved replacement private channel rather than leaving its webhook missing. Discord webhooks deliver embeds with per-destination colors and bounded whitelisted fields. The default `allowed_mentions.parse` is empty.

Examples of *existing* config keys in Velocity's data-directory `velocity-config.properties`:

```properties
discord.enabled=false
discord.punishments.webhook-environment=ES_DISCORD_PUNISHMENTS_WEBHOOK
discord.reports.webhook-environment=ES_DISCORD_REPORTS_WEBHOOK
discord.logs-staffmode.webhook-environment=ES_DISCORD_STAFFMODE_WEBHOOK
discord.alerts.webhook-environment=ES_DISCORD_ALERTS_WEBHOOK
```

These are environment **variable names**. Do not put webhook URLs, StaffBot tokens, MariaDB passwords, HMAC secrets or network addresses into GitHub, tickets, Discord messages, or public plugin configuration. Use the existing approved private secret source; the Velocity loader also requires its configured Discord route environment to be `STAGING` or `PRODUCTION`.

### Optional exact review-role ping (new)

The Velocity JVM can opt into pinging **one exact configured Discord role in the `alerts` destination only** with the JVM property below. The role ID is not a secret; the role must belong to the intended Staff Discord guild and be permitted to receive webhook mentions.

```text
-Denthusiastaff.discord.alertRoleId=123456789012345678
```

This is a placeholder ID. Never copy this dummy ID to production; use the actual authorized reviewer role. Remove the JVM property to disable pings. Non-alert destinations never mention roles; `@everyone`, `@here`, and uncontrolled user mentions are never enabled. The role setting does not authorize a Discord user to approve punishments—it only selects the alert audience.

### Existing StaffBot setup, separate from webhook delivery

StaffBot has a JDA command/listener/runtime and a private authority bridge. The moderation runtime uses the following keys from its **private** `--moderation-config-file` (or process environment), not from the public Velocity plugin config:

```text
ENTHUSIA_STAFF_BOT_DB_JDBC_URL
ENTHUSIA_STAFF_BOT_DB_USERNAME
ENTHUSIA_STAFF_BOT_DB_PASSWORD
ENTHUSIA_STAFF_BOT_AUTHORITY_URL
ENTHUSIA_STAFF_DISCORD_AUTHORITY_SECRET
ENTHUSIA_STAFF_BOT_AUTHORITY_TRANSPORT
ENTHUSIA_STAFF_BOT_COMPONENT_SECRET
```

The application also has separate private bot token and guild-scoping configuration. See `docs/discord-staff-bot-runtime.md` and `docs/discord-moderation-platform.md` before activating. The bot requires an up-to-date current Minecraft staff rank and current linked actor identity. Do **not** use Discord roles as authorization to issue/revoke punishments.

## What this checkpoint does

- `StaffActionLogger` sends its queued `STAFF_ACTION` outbox records to the **supported** `logs-staffmode` destination; formerly it wrote `staff-action-log` that the Velocity worker did not recognize. Commands log the command identity but omit arguments, avoiding password/private-message leakage. Local JSONL audit and forwarding contracts remain separate.
- All webhook destinations are formatted as compact embeds; auto-mentions are prohibited.
- A new punishment request creates an additional private `PUNISHMENT_APPROVAL_REQUIRED` notification in the alerts channel **without changing the durable original punishment-lifecycle audit in the punishments channel**.
- Low-confidence same-network + sanctioned-account login attempts write hourly deduplicated `ALT_EVASION_REVIEW` Discord alerts with no raw IP. Successful qualified inheritance also produces exactly one `ALT_SANCTION_INHERITED` alert per inherited source sanction, alongside the punishment log.
- Blocked chat attempted by an account already carrying an inherited mute/public mute writes an hourly deduplicated `ALT_MUTED_CHAT_ATTEMPT` alert without storing the private message body. The Paper listener throttles database writes to no more than once every five minutes per online player.
- Events retry through the existing durable outbox. Disabling the webhook route **does not** enable another delivery channel automatically.

## Not yet implemented, required before claiming full owner goals

1. **Discord approve/deny buttons** for Minecraft punishment requests (not the existing Discord-user punishment buttons). Must carry opaque signed request ID, short TTL, replay nonce and actor identity; verify the *current* Discord↔Minecraft link, Staff Mode duty, live rank, target hierarchy and required approval rank. Acquire the existing `PunishmentRequestService` durable lease, then approve/deny through its exact service; never apply the sanction twice or acknowledge before commit. Require a denial reason and show a final case ID on success. Use the authenticated private StaffBot→Paper authority transport; no public/admin command fallback.
2. The unified Review Center/query, unread/claimed/done counts, durable recipient acknowledgement, Discord button state refresh, private evidence views, high-volume digesting and dead-letter recovery.
3. Low-confidence network-review notifications on chat **even when the speaker is not yet muted**; this needs a bounded evidence-index lookup and rate-limiting outside the game thread. `ALT_MUTED_CHAT_ATTEMPT` currently applies only to *already inherited* mute sanctions.
4. Immediate inherited-sanction reconciliation for accounts already online, source changes/expiry/overturn, and unlinks/exemptions. Login-time inheritance alone is incomplete.
5. External punishment sources that bypass EnthusiaStaff, including Polar, are **not** guaranteed in `#in-game-punishments`. They need explicit reviewed adapters—not log scraping.
6. Staging confirmation of JDA guild/role permissions, every webhook URL destination, actual staff messages, per-channel embed presentation, cross-proxy event retry, schema and Java/Bedrock false-positive behavior.

## Required staging acceptance

Verify one complete source event for each type: staff entry, staff-world interaction, sensitive command without arguments, vanish, punishment created, request submitted (punishments audit + review alert), alt evidence join, inherited mute blocked chat. Confirm the expected **private** channel, exactly one optional review-role ping, no public IP or message bodies, and stable delivery after proxy restart. Then test a request denial and approval using current authority and prove the corresponding database case and actual Paper/Velocity enforcement before production rollout.
