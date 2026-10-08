# Discord chat cutover and rollback

Tracking: issue `#268` under DiscordSRV retirement umbrella `#264`.

This document governs the chat portion of DiscordSRV retirement. It does not authorize removal of
DiscordSRV's other responsibilities.

## Authority modes

Paper and StaffBot intentionally require separate explicit configuration.

### Paper

`discord-chat-bridge.mode` accepts:

- `DISABLED` — provider-neutral Discord chat transport is not installed.
- `SHADOW` — replacement transport is installed but RoseChat's legacy DiscordSRV chat path stays live.
- `AUTHORITATIVE` — replacement transport is installed and RoseChat's legacy Discord chat path is
  suppressed only after replacement readiness succeeds.

For backward compatibility, if `mode` is omitted,
`discord-chat-bridge.shadow-enabled=true` still selects `SHADOW`.

Paper `AUTHORITATIVE` also requires:

```yaml
discord-chat-bridge:
  mode: AUTHORITATIVE
  shadow-enabled: false
  authoritative-cutover-ack: true
```

Do not leave the legacy `shadow-enabled` flag true with an explicit non-SHADOW mode.

### StaffBot

Preferred environment variable:

```text
ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_MODE=DISABLED|SHADOW|AUTHORITATIVE
```

The old `ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_ENABLED=true` remains a staging compatibility alias for
`SHADOW` only when `MODE` is omitted.

### Bloom file-backed chat settings (optional, not automatically active)

Some Bloom Startup panels expose only predefined `JAR FILE` / `APP FLAGS` fields
and do not permit arbitrary `ENTHUSIA_STAFF_BOT_*` process environment variables.
On a StaffBot build containing the separate file-backed chat settings change, the
existing production startup can add **one path-only argument** to APP FLAGS:

```text
--chat-bridge-config-file=private-chat-bridge.properties
```

The option reads only allowlisted chat-bridge/public-chat identity keys from a private
Java `properties` file. It cannot change the moderation application token, fixed
StaffBot environment, database or tunnel configuration. Missing, oversized,
duplicate-key, invalid, or conflicting-with-process-environment files fail closed.
The file is capped at 16 KiB and is opened without following a symlink. The
existing default (no argument) still reads chat settings only from environment
variables. Do not put secrets on the command line or into tracked repository files.
See `staff-bot/chat-bridge.properties.example` for placeholders only.

This option is **not available in previously staged StaffBot PR #465 artifacts**
until the new file-backed change is reviewed and a new exact-head artifact is built.
Do not add the argument to the currently running JAR: its parser rejects unknown
flags and startup would fail. Even after merge, configuring the file is not
permission to run AUTHORITATIVE; first complete the pinned private SHADOW acceptance
process and preserve the existing production JAR and /m rollback files.

Production supports two separately acknowledged modes:

**Private-channel migration test, without cutover** (only when the production-SHADOW safety change
is present in the deployed StaffBot build):

```text
ENTHUSIA_STAFF_BOT_ENVIRONMENT=production
ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_MODE=SHADOW
ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_MIGRATION_ACK=I_ACKNOWLEDGE_PRODUCTION_SHADOW_MIGRATION
ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_ROUTES=SMP/global=1541286004298752091
```

The production-SHADOW path pins every Discord route, including any explicitly enabled
Discord-to-Minecraft ingress route, to the fixed private staging channel
`1541286004298752091`. A different channel fails configuration validation. SHADOW does **not**
publish AUTHORITATIVE readiness or suppress RoseChat's legacy DiscordSRV chat. Existing staff
moderation identity and runtime environment must remain production; switching the moderation bot
to the staging application identity is not an acceptable migration technique. This mode also
requires the separate public-chat identity, authenticated Velocity STAFFBOT peer, and TLS/HMAC
secrets to be configured and validated before startup.

The current network preflight evidence and strict verification gates are tracked in
[`discord-chat-bloom-network-preflight.md`](discord-chat-bloom-network-preflight.md).
That document does **not** claim a successful StaffBot-origin connection.

### Velocity STAFFBOT peer secret-source preflight

Before editing live Velocity `config.properties`, confirm which channel-secret source it
currently uses. Adding the peer declaration
`channel.backend.STAFFBOT.secret-environment=ES_CHANNEL_STAFFBOT_SECRET`
changes the complete set of required channel secrets.

`PrivateChannelSecrets` deliberately requires an **all-or-nothing** source:

- When any configured channel-secret environment variable is set, **all** configured
  channel-secret environment variables must be present, including the new
  `ES_CHANNEL_STAFFBOT_SECRET`. A partial environment fails closed.
- When no channel-secret environment variable is set, the protected runtime
  `channel.properties` must contain **exactly** the required properties: existing proxy
  and TLS-password secrets, every configured backend secret, and the new
  `channel.backend.STAFFBOT.secret`. A missing or extra property fails closed.
- The HMAC value used for `STAFFBOT` must match StaffBot's
  `ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_CLIENT_SECRET`; the existing Velocity proxy
  HMAC must match StaffBot's `ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_PROXY_SECRET`.
  Keep all key values private. **Never** put raw secrets in GitHub, this document,
  or the public `config.properties`.
- Ensure StaffBot can validate the Velocity TLS endpoint with a truststore containing
  its trusted public certificate. Never distribute Velocity's private TLS keystore
  to StaffBot. Preserve old JARs and settings for rollback.

A JAR upload or a new peer-config line alone does not establish a working bridge.
Verify the secret source, public-chat JDA identity, reachable TLS host, and live
process environment before an approved SHADOW restart.

**Production cutover** (only after the staging acceptance matrix passes):

```text
ENTHUSIA_STAFF_BOT_ENVIRONMENT=production
ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_MODE=AUTHORITATIVE
ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_CUTOVER_ACK=I_ACKNOWLEDGE_DISCORDSRV_CHAT_CUTOVER
```

AUTHORITATIVE requires complete symmetric inbound coverage for every distinct Discord channel
used by outbound routes. This prevents the globally suppressed legacy Discord inbound path from
leaving any routed Discord channel without a replacement Discord -> Minecraft entry.

Production never accepts the legacy boolean by itself. The migration acknowledgement is **not**
a substitute for the separate AUTHORITATIVE cutover acknowledgement.

## What AUTHORITATIVE changes

The replacement transport must already have:

- RoseChat plain outbound bridge;
- RoseChat styled outbound bridge;
- Discord -> Minecraft inbound bridge;
- authenticated Paper -> Velocity channel connection;
- a fresh authenticated StaffBot Discord-publishing readiness lease forwarded through Velocity.

When InteractiveChat is enabled, at least one `RichChatArtifactProvider` must be registered.
Today that can be the temporary InteractiveChatDiscordSrvAddon compatibility adapter; later the
permanent renderer companion can satisfy the same provider-neutral readiness check.

Only after those readiness checks pass does EnthusiaStaff call RoseChat's
`suppressLegacyDiscordChat()` registration.

The registration affects only RoseChat's legacy Discord chat path. It does not disable:

- RoseChat public chat;
- Staff moderation/preflight;
- Minecraft -> Minecraft/Bungee routing;
- provider-neutral outbound publication;
- provider-neutral inbound Discord chat;
- DiscordSRV itself or its unrelated features.

The suppression registration is released **before** replacement bridge teardown or Paper channel
unbind. Releasing it makes RoseChat's legacy Discord chat path eligible again.

StaffBot publishes a short-lived readiness lease only while it is explicitly configured
AUTHORITATIVE and its validated Discord/JDA chat lifecycle is resumed. Velocity forwards that lease ephemerally to Paper; it is never written to
the moderation/network inbox. Paper refuses AUTHORITATIVE suppression without a fresh lease and
checks lease expiry once per second. A JDA disconnect, StaffBot pause, transport loss, or missed
heartbeat therefore restores legacy chat eligibility without waiting for a Paper restart.

## Staging acceptance matrix

Do not enter production `AUTHORITATIVE` until all applicable rows have been observed on a
controlled server with the actual dependency set.

| Case | Expected Minecraft -> Discord | Expected Discord -> Minecraft |
| --- | --- | --- |
| Normal public chat | one clean StaffBot message, correct RoseChat sender/rank/text | one RoseChat message, no echo back to Discord |
| Legacy `&` / section colors | no raw formatting codes leak | readable text reaches RoseChat |
| Hex/RGB chat | readable Discord fallback; exact Adventure render retained internally | readable text reaches RoseChat |
| Bold/italic/underline/etc. | clean supported Discord Markdown where representable | no malformed formatting injection |
| InteractiveChat held item | styled text plus bounded PNG artifact | n/a |
| InteractiveChat inventory | styled text plus bounded PNG artifact | n/a |
| InteractiveChat Ender chest | styled text plus bounded PNG artifact | n/a |
| Escaped InteractiveChat placeholder | no artifact for escaped placeholder | n/a |
| Oversize image/artifact | text-only fallback, no duplicate Discord message | n/a |
| Missing attachment permission | text-only fallback | n/a |
| Linked Minecraft account | optional cached linked display label; no raw Discord ID | n/a |
| Unlinked Minecraft account | normal Minecraft-only sender prefix | n/a |
| Discord user/role/channel mention text | mentions disabled on send | readable normalized text, no delegated DiscordSRV parsing |
| Bot/webhook Discord message | n/a | ignored |
| Private/non-public RoseChat channel | no public Discord export | only explicitly configured public route accepted |
| StaffBot queue saturation | Minecraft chat still succeeds; bridge may drop | Discord message may drop; no durable backlog |
| Velocity/Paper disconnect | legacy send restored on Paper channel unbind in AUTHORITATIVE mode | no stale replay |
| StaffBot JDA/Discord disconnect | readiness lease expires and legacy send is restored | ingress pauses until Discord identity is revalidated |
| RoseChat reload/disable | legacy suppression released | bridge revalidated after return |
| InteractiveChat/rich-provider loss | legacy suppression released/revalidated; cutover must not proceed without a rich provider while InteractiveChat is enabled | n/a |
| Duplicate transport frame | no duplicate final chat send | no duplicate Minecraft delivery |

## Shadow validation sequence

1. Keep Paper in `SHADOW`.
2. Keep StaffBot in staging `SHADOW`, or in explicitly acknowledged production `SHADOW` with all
   routes pinned to the fixed private staging channel. Never use AUTHORITATIVE as a test mode.
3. Route only the pinned staging test channel.
4. Leave DiscordSRV fully live.
5. Compare the replacement output against Minecraft/RoseChat semantics, not DiscordSRV's formatting
   quirks.
6. Exercise every applicable row in the acceptance matrix.
7. Verify bounded queue/drop behavior and no durable chat replay.
8. Verify server/channel route maps are exact and symmetric where inbound is enabled.
9. Verify the StaffBot application has Message Content intent only when inbound routes are configured.
10. Verify no public message can create Discord mentions.

## Production cutover sequence

Production cutover is a deliberate configuration operation, not a merge side effect.

1. Record the current known-good DiscordSRV/RoseChat configuration for rollback.
2. Configure the production StaffBot TLS/HMAC channel peer and explicit route maps.
3. Verify every production Discord channel ID belongs to the pinned Enthusia guild and StaffBot has
   `VIEW_CHANNEL` + `MESSAGE_SEND`.
4. Enable Message Content intent if production inbound routes are configured.
5. Start StaffBot with `MODE=AUTHORITATIVE` and the exact cutover acknowledgement.
6. Configure Paper with `mode: AUTHORITATIVE` and `authoritative-cutover-ack: true`.
7. Restart/apply the restart-only chat configuration using the normal deployment process.
8. Confirm the RoseChat authority issue is clear, the authenticated Paper channel is connected, and
   a fresh StaffBot Discord-publishing readiness lease is being accepted.
9. Send one controlled Minecraft message and confirm exactly one Discord message appears.
10. Send one controlled Discord message and confirm exactly one Minecraft/RoseChat message appears.
11. Test at least one InteractiveChat artifact if InteractiveChat is installed.
12. Observe normal chat long enough to detect duplicate/loop/routing behavior before proceeding with
    any DiscordSRV jar removal.

Do **not** remove DiscordSRV merely because chat cutover succeeds. Complete the separate account-link,
managed-role, console, and any other DiscordSRV responsibility migrations under #264 first.

## Immediate rollback

Rollback should favor restoring chat availability over preserving the new transport.

1. Set Paper chat mode to `SHADOW` or `DISABLED` and restart/apply through the normal deployment
   path. Closing/unbinding EnthusiaStaff releases the RoseChat legacy suppression registration.
2. Confirm RoseChat's legacy Discord chat path is active again.
3. Set StaffBot chat mode to `DISABLED` after legacy delivery is confirmed.
4. Keep the exact replacement route/config values available for diagnosis; do not delete logs or
   change multiple unrelated Discord systems simultaneously.
5. Verify one Minecraft -> Discord and one Discord -> Minecraft legacy message before declaring
   rollback complete.

If the Paper process/channel fails unexpectedly, the suppression registration is process-local and
does not survive RoseChat/EnthusiaStaff shutdown. RoseChat therefore returns to its normal legacy
eligibility when the plugin lifecycle restarts without a successful authoritative acquisition.
If only Discord/JDA or StaffBot becomes unhealthy, the short readiness lease expires and the Paper
watchdog releases suppression automatically.

## Current external validation limit

Hosted CI and canonical Pi/Sentinel startup tests do not currently include trusted artifacts for
InteractiveChat, `InteractiveChatDiscordSrvAddon`, or DiscordSRV. They can validate the
EnthusiaStaff build/start/restart contract, but they cannot prove visual parity of the real
InteractiveChat renderer.

Real rich-render acceptance therefore requires either:

- reviewed trusted dependency onboarding for the exact upstream dependency closure; or
- a controlled SMP-like staging environment with the real plugins installed.


## Independent rich renderer migration candidate (not upstream visual parity)

The optional Paper configuration key
\`discord-chat-bridge.independent-rich-renderer-enabled: true\` permits a
provider-neutral image fallback when InteractiveChat is enabled but its
DiscordSRV-based rendering addon is absent/unavailable. The existing staging
addon renderer still takes precedence if available.

The new \`IndependentRichChatArtifactProvider\` reads InteractiveChat's actual
configured item/inventory/Ender chest placeholder patterns and permission
gates through its own API. It snapshots material types, quantities and slot
positions using the player's entity scheduler, then renders bounded PNG
slot-cards on the existing worker pool. No DiscordSRV or
\`InteractiveChatDiscordSrvAddon\` classes, JDA, tokens, or game texture assets
are referenced. Rendering errors yield plain text; ordinary Minecraft chat
continues unchanged. Default is **false**, so existing production behavior is
unchanged. Changing this configuration requires a separately authorized
deployment/restart and SHADOW testing.

**Important limitation:** the independent fallback shows material names,
stack counts and slot positions, not the original addon’s pixel-perfect
Minecraft item sprites, enchantment glint, lore or custom resource-pack
textures. It is an actual PNG renderer but does **not** satisfy final visual
parity by itself. Final physical removal of DiscordSRV still requires owner
acceptance of a documented rendering difference, or further implementation
of texture/metadata features, plus the complete acceptance gates in
[\`discordsrv-full-retirement.md\`](discordsrv-full-retirement.md).

Staging tests before selecting this fallback as the retained implementation:

1. Activate the new Paper renderer only with the new reviewed JAR, private
   chat SHADOW, InteractiveChat and RoseChat; test with DiscordSRV **present**
   while its addon is disabled in the staging plugin set.
2. Send actual unescaped/escaped/permission-restricted item, inventory and
   Ender chest placeholders. Compare artifact routing/position to legacy
   output; explicitly sign off on differences.
3. Verify no server tick stalls, bounded attachments, duplicates, loops,
   staff/private channel leakage, or failed-message replay.
4. Remove both DiscordSRV and its addon from a disposable staging server.
   Verify safe startup, visual output, plain-chat fallback during faults, and
   reconnect/shutdown behavior. This **must not** be substituted with isolated
   unit tests or a Windows-side network probe.
5. Keep the old DiscordSRV plugin/configuration protected for rollback until
   every other account-link, role-sync, guild, numeral and console consumer
   also passes its independent migration gate.
