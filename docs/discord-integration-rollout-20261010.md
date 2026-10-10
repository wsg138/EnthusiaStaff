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

The application also has separate private bot token and guild-scoping configuration. See `docs/discord-staff-bot-runtime.md` and `docs/discord-moderation-platform.md` before activating. The bot requires an up-to-date current Minecraft staff rank and current linked actor identity. Do **not** use Discord roles as authorization to issue/revoke punishments. The new `/review-request` slash command is default-disabled at the Discord application-command permission layer: explicitly authorize its use only for the intended reviewers after connecting StaffBot. It rechecks active Minecraft Staff Mode and the live reviewer permission at the private Paper authority endpoint, so granting a Discord role by itself cannot approve anything.

## What this checkpoint does

- `StaffActionLogger` sends its queued `STAFF_ACTION` outbox records to the **supported** `logs-staffmode` destination; formerly it wrote `staff-action-log` that the Velocity worker did not recognize. Commands log the command identity but omit arguments, avoiding password/private-message leakage. Local JSONL audit and forwarding contracts remain separate.
- All webhook destinations are formatted as compact embeds; auto-mentions are prohibited.
- A new punishment request creates an additional private `PUNISHMENT_APPROVAL_REQUIRED` notification in the alerts channel **without changing the durable original punishment-lifecycle audit in the punishments channel**. It includes the exact private slash-command review action.
- Low-confidence same-network + sanctioned-account login attempts write hourly deduplicated `ALT_EVASION_REVIEW` Discord alerts with no raw IP. Successful qualified inheritance also produces exactly one `ALT_SANCTION_INHERITED` alert per inherited source sanction, alongside the punishment log.
- Blocked chat attempted by an account already carrying an inherited mute/public mute writes an hourly deduplicated `ALT_MUTED_CHAT_ATTEMPT` alert without storing the private message body. A *below-threshold suspected alt* with no active mute also gets a bounded database relationship/source-sanction check, and if related to a sanctioned account, sends a review-only `ALT_SUSPECTED_CHAT` event. The Paper listener throttles either path to no more than once every five minutes per online player.
- Events retry through the existing durable outbox. Disabling the webhook route **does not** enable another delivery channel automatically.

## Not yet implemented, required before claiming full owner goals

1. **Bot-owned approve/deny buttons are still missing.** A private **`/review-request` slash command now exists in StaffBot** (2026-10-10 checkpoint) with `request-id`, `decision` (Approve / Deny choice), and `note` (required on denial). A private signed StaffBot→Paper `POST /v1/staff-reviews/{approve|deny}` bridge verifies the current linked Minecraft actor, current Paper LuckPerms rank and exact reviewer permission, ACTIVE authoritative mode, active Staff Mode session, target hierarchy, and the existing durable `PunishmentRequestService` claim/commit. The bot sends a case ID only after an approved, committed response; errors are never reported as approval success. Current message embeds show a slash-command instruction to review requests. **Bot-owned inline buttons, their signed short-lived component IDs, denial modal and in-place state refresh still need implementation**. There is no public command/console fallback.
2. The unified Review Center/query, unread/claimed/done counts, durable recipient acknowledgement, Discord button state refresh, private evidence views, high-volume digesting and dead-letter recovery.
3. Low-confidence network-review notifications on chat **are now coded** through a bounded asynchronous relationship-and-active-source-sanction lookup. The `ALT_SUSPECTED_CHAT` alert is queued only when a below-85%-policy-grade relationship already exists and the related account is banned or muted. No chat text is stored, and alerts are deduplicated per source sanction/hour. **Load/performance, private evidence accuracy and end-to-end Discord delivery still require live staging validation.** `ALT_MUTED_CHAT_ATTEMPT` remains a separate code path for an already inherited mute.
4. Immediate inherited-sanction reconciliation for accounts already online, source changes/expiry/overturn, and unlinks/exemptions. Login-time inheritance alone is incomplete.
5. External punishment sources that bypass EnthusiaStaff, including Polar, are **not** guaranteed in `#in-game-punishments`. They need explicit reviewed adapters—not log scraping.
6. Staging confirmation of JDA guild/role permissions, every webhook URL destination, actual staff messages, per-channel embed presentation, cross-proxy event retry, schema and Java/Bedrock false-positive behavior.

## Required staging acceptance

Verify one complete source event for each type: staff entry, staff-world interaction, sensitive command without arguments, vanish, punishment created, request submitted (punishments audit + review alert), alt evidence join, inherited mute blocked chat. Confirm the expected **private** channel, exactly one optional review-role ping, no public IP or message bodies, and stable delivery after proxy restart. Then test `/review-request` denial and approval using current authority and prove the corresponding database case and actual Paper/Velocity enforcement before production rollout. Current tests compile/pass for the new signed target and negative authorization gates, but **there has been no complete live Discord-to-Paper approval test and no Java/Bedrock staging**.
