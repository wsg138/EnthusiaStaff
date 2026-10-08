# Discord chat transport foundation

Tracking: `#268` under DiscordSRV retirement umbrella `#264`.

## Confirmed topology

The persistent channel now carries two distinct authenticated peer classes:

- required Paper backend peers such as `SMP` and `HUB`;
- the reserved auxiliary peer `STAFFBOT`.

`STAFFBOT` is authenticated on the same TLS 1.3 + HMAC transport, but it is **not** a durable
moderation backend and is never included in `NetworkOutboxWorker` delivery quorum.

The staged outbound path is:

```text
RoseChat
  -> EnthusiaStaff Paper
  -> existing authenticated persistent channel
  -> Velocity ephemeral chat relay
  -> authenticated STAFFBOT peer
  -> bounded StaffBot chat queue
  -> public-chat JDA identity (Enthusia SMP)
```

The public-chat JDA session is separate from the Staff/moderation JDA session but runs in the same
StaffBot JVM/container. It has a separate token/application-ID fence and owns only public chat
ingress/egress.

The reverse Discord -> Minecraft path is now implemented as a separate staging-only checkpoint.
It routes explicitly to one target backend and enters RoseChat's canonical Discord-origin pipeline
without using the durable moderation inbox or creating an outbound echo loop.

## Outbound wire contract

`ChatBridgeOutboundMessage` is the provider-neutral transport contract.

It contains only:

- stable RoseChat event ID;
- stable Minecraft-side external message ID;
- canonical mirror ID;
- creation and hard-expiry timestamps;
- original Paper backend ID;
- logical RoseChat channel ID;
- Minecraft sender UUID;
- bounded presentation name;
- bounded canonical plain text.

It deliberately contains no JDA, DiscordSRV, Discord role, Discord user, database record, or
moderation-internal type.

`ChatBridgeMessages.OUTBOUND` is `CHAT_BRIDGE_OUTBOUND_V1`.

The JSON payload is limited to 16 KiB. Canonical text is limited to 2,000 characters. The
message lifetime cannot exceed 60 seconds. Routing/presentation identifiers reject control
characters. Unknown JSON properties are rejected so a newer sender cannot silently widen what an
older relay accepts.

## Delivery semantics

Chat remains best-effort and ephemeral:

- no MariaDB outbox;
- no replay after expiry;
- no hours-old delivery after reconnect;
- an ACK confirms only that the next authenticated hop accepted the frame, not that Discord
  displayed it;
- transport failure never blocks Minecraft chat;
- Paper, Velocity, and StaffBot queues are bounded;
- queue saturation, missing routes, disconnects, expiry, or JDA failures drop chat instead of
  creating a retry backlog.

The existing persistent-channel security properties remain in force: TLS 1.3,
HMAC-authenticated envelopes, nonce/timestamp replay protection, frame bounds, explicit peer IDs,
and per-message acknowledgement.

`CHAT_BRIDGE_HEALTH_V1` is a separate short-lived authority-readiness signal. While StaffBot is explicitly AUTHORITATIVE and the validated Discord/JDA lifecycle is resumed,
StaffBot refreshes a 15-second publishing lease every 5 seconds over the authenticated `STAFFBOT`
channel. Velocity forwards it only
to configured Paper backends and never stores it durably. Paper requires a fresh lease before
AUTHORITATIVE suppression and releases legacy suppression after lease expiry.

## Paper -> Velocity SHADOW checkpoint

Merged PR #370 established the first runtime hop:

- EnthusiaStaff Paper mirrors only RoseChat's public outbound bridge API in
  `integration-contracts`; those types remain compile-time-only and are not shaded into the Paper
  JAR.
- Paper installs the RoseChat outbound provider only when
  `discord-chat-bridge.shadow-enabled: true`. The flag defaults **false**.
- The provider binds to the already-authenticated `PersistentChannelClient` when that channel is
  live and unbinds during channel shutdown/reconnect.
- The RoseChat caller never performs a socket write. Paper uses a bounded single-thread in-memory
  relay queue and drops chat when disconnected, expired, or saturated.
- Paper sends `CHAT_BRIDGE_OUTBOUND_V1` with the RoseChat event ID as the authenticated envelope
  message ID.
- Velocity intercepts this message type **before** the durable network inbox. Chat is never recorded
  in `NetworkOutboxStore`.
- Velocity requires the payload `sourceServerId` to equal the authenticated Paper envelope
  `serverId`, and the payload event ID to equal the envelope message ID.
- Velocity uses bounded in-memory queue/dedupe state. Already-admitted duplicates are ACKed without
  duplicate delivery.

Enabling SHADOW does not disable RoseChat's existing DiscordSRV send. DiscordSRV remains the live
Minecraft <-> Discord chat transport.

## Velocity -> StaffBot staging checkpoint

PR #381 adds the next bounded leg while remaining default-off and staging-only:

- Velocity reserves peer ID `STAFFBOT`. Configure its HMAC key only by adding
  `channel.backend.STAFFBOT.secret-environment=...` to Velocity's private runtime config.
- The peer-policy layer removes `STAFFBOT` from the required Paper backend set before
  `NetworkOutboxWorker` is constructed.
- Authenticated `STAFFBOT` application frames are rejected before chat, transfer, staff-mode,
  verification/report, or durable-inbox handlers. Authentication never implies Paper-backend
  application authority.
- When the peer key is configured, Velocity installs one chat sink that sends only
  `CHAT_BRIDGE_OUTBOUND_V1` to `STAFFBOT` and waits for the normal short ACK on the dedicated
  Velocity chat worker.
- Missing/disconnected/rejected StaffBot delivery returns false to the ephemeral relay. There is no
  durable retry path.
- StaffBot connects outbound with `PersistentChannelClient`; transport reconnect is independent
  from core bot readiness.
- StaffBot accepts chat only while a validated Discord identity is current. Disconnect pauses
  admission and clears queued/dedupe state; a revalidated session resumes it.
- The same lifecycle drives `CHAT_BRIDGE_HEALTH_V1` readiness. A paused/unhealthy Discord
  gateway cannot keep Paper's AUTHORITATIVE suppression alive merely because the StaffBot TLS
  socket to Velocity remains connected.
- StaffBot routes only an explicit `sourceServer/logicalChannel -> Discord channel ID` allowlist.
  During this checkpoint every route must target the fixed staging test channel.
- StaffBot uses a dedicated bounded single-thread chat queue and bounded event-ID dedupe.
- JDA egress validates that the target channel belongs to the pinned Enthusia guild and that the bot
  has `VIEW_CHANNEL` + `MESSAGE_SEND`.
- Final Discord content is bounded to 2,000 characters after the `[server] sender: ` prefix.
- Allowed mentions are set to an empty list for every chat send.

StaffBot chat is default-off. The legacy
`ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_ENABLED=true` form remains a staging-only alias for SHADOW when
no explicit mode is present. The preferred control is
`ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_MODE=DISABLED|SHADOW|AUTHORITATIVE`.

SHADOW is normally staging-only. When the explicit production-SHADOW safety gate is built into
StaffBot, production may run SHADOW only with the separate exact migration acknowledgement and
every chat route pinned to the fixed private staging channel. SHADOW leaves DiscordSRV live and
does not authorize suppression. Production AUTHORITATIVE requires its independent cutover
acknowledgement and completed acceptance checks; see `docs/discord-chat-cutover.md`.

## Discord -> Minecraft staging checkpoint

PR #391 adds the reverse ephemeral path while preserving the same migration boundary:

```text
Discord staging channel
  -> StaffBot JDA listener
  -> CHAT_BRIDGE_INBOUND_V1
  -> existing authenticated StaffBot PersistentChannelClient
  -> Velocity StaffBot-only ingress relay
  -> exact configured Paper backend
  -> Paper RoseChat ingress bridge
  -> Bukkit primary thread
  -> RoseChatAPI.dispatchInboundChat(...)
```

The inbound provider-neutral wire type is `ChatBridgeInboundMessage`. It carries a stable event ID,
Discord external/canonical IDs, creation/expiry timestamps, source Discord channel and user IDs,
display name, exact target backend, logical RoseChat channel, and bounded plain text. It uses the
same 16 KiB JSON ceiling, 2,000-character text ceiling, unknown-field rejection, and maximum
60-second lifetime as the outbound checkpoint.

Ingress remains disabled unless the normal staging bridge is enabled **and**
`ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_INGRESS_ROUTES` is set. Its syntax is:

```text
discordChannelId=server/channel
```

Multiple entries use semicolons. During migration every inbound Discord channel must be the pinned
staging test channel and must select an already-configured symmetric outbound route. The runtime
does not infer a reverse route from `ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_ROUTES`, because multiple
Minecraft routes may intentionally share one Discord staging channel.

When at least one ingress route is configured, StaffBot additionally requests JDA
`GUILD_MESSAGES` and `MESSAGE_CONTENT`. The Discord application must therefore have the Message
Content privileged intent enabled for this staging bot. Without ingress routes, those intents are
not requested.

Ingress admission rules are deliberately repeated at each hop:

- JDA ignores bot and webhook messages and accepts only the pinned guild/channel route.
- JDA converts Discord-native user/role/channel mentions and custom emoji to readable display text before transport; raw Discord mention syntax is not delegated to RoseChat's legacy Discord provider.
- StaffBot revalidates source Discord channel, target backend, logical RoseChat channel, expiry,
  connection state, and payload bounds before enqueueing.
- StaffBot sends on one bounded worker and waits for the normal short ACK before dequeuing the next
  message, preserving bounded backpressure.
- Velocity accepts `CHAT_BRIDGE_INBOUND_V1` only from authenticated peer `STAFFBOT`; that peer
  remains denied from punishment, staff-mode, transfer, verification, and durable-inbox handlers.
- Velocity validates event ID, expiry, and exact allowlisted Paper target and forwards on a bounded
  in-memory relay only.
- Paper diverts inbound chat before `PaperNetworkMessageHandler`, so it is never written to the
  durable moderation/network inbox.
- Paper validates proxy identity, event ID, target backend, expiry, queue/dedupe bounds, then
  schedules the final RoseChat call on Bukkit's primary thread.
- RoseChat receives a provider-neutral `InboundChatMessage`, creates its existing Discord-proxy
  sender + wrapped `RoseMessage`, and uses the canonical Discord-to-Minecraft path. Existing Staff
  preflight remains active and Discord-origin messages are not offered back to the outbound bridge.

ACKs remain hop-local admission acknowledgements, not end-to-end delivery receipts. Disconnect,
expiry, queue saturation, invalid routes, missing RoseChat channels, or provider errors drop the
message. There is no durable retry or replay backlog.

## Styled Minecraft -> Discord staging checkpoint

The next outbound checkpoint adds a separate styled frame while preserving
`CHAT_BRIDGE_OUTBOUND_V1` byte-for-byte as the fallback path.

RoseChat now exposes an optional provider-neutral styled render bridge. When installed, one public
Minecraft chat event can carry:

- the existing canonical plain player input and stable event identity;
- resolved message-body plain text;
- resolved message-body Discord Markdown;
- resolved message-body Adventure JSON;
- resolved full in-game chat-line plain text;
- resolved full-line Discord Markdown;
- resolved full-line Adventure JSON.

This is transported as `CHAT_BRIDGE_RENDERED_V1` in `ChatBridgeRenderedMessage`.
The dedicated encoded payload ceiling is 384 KiB. Each Adventure JSON representation is limited to
64 KiB UTF-8, Markdown to 8,192 characters, and plain render text to 4,096 characters. The existing
60-second lifetime, exact source-server binding, event-ID binding, explicit route allowlist, bounded
queues, and bounded dedupe remain in force.

The rendered frame is metadata only. It **does not** carry item/inventory PNGs or arbitrary binary
attachments. InteractiveChat-generated artifacts will use a separate bounded artifact contract.

The styled route is:

```text
RoseChat resolved transport render
  -> EnthusiaStaff Paper styled bridge
  -> CHAT_BRIDGE_RENDERED_V1
  -> Velocity styled ephemeral relay
  -> authenticated STAFFBOT peer
  -> StaffBot styled ingress
  -> JDA final send
```

Important migration behavior:

- RoseChat builds the styled render only when an external styled bridge is installed.
- The render parser is independent of RoseChat's legacy DiscordSRV provider. Member/channel/custom
  emoji lookup is not delegated to DiscordSRV.
- If the styled bridge is absent, disconnected, oversized, queue-saturated, or rejects admission,
  RoseChat falls back to the existing plain `CHAT_BRIDGE_OUTBOUND_V1` provider.
- Both styled and V1 frames bypass the durable moderation/network inbox.
- Velocity authorizes styled outbound frames only from configured Paper backend peers and forwards
  them only to authenticated `STAFFBOT`.
- StaffBot applies the same exact `server/channel -> Discord channel` route map used by V1.
- JDA performs one final send. It uses the resolved full-line Discord Markdown when it fits the
  Discord 2,000-character content limit. If it does not fit, it falls back to the resolved full-line
  plain text before truncation so an oversized message cannot leave broken Markdown delimiters.
- Allowed mentions remain empty, so player content cannot create `@everyone`, role, or user pings.
- Raw Minecraft color codes are never emitted to Discord by this path. RoseChat has already resolved
  color/format tokens before transport.

Discord does not support arbitrary RGB color on individual normal message spans. Exact resolved
RGB/hex/style semantics therefore remain in the Adventure JSON representation for downstream rich
rendering rather than being faked with malformed embeds or leaked formatting codes. The current
staging presentation intentionally favors clean/readable output over DiscordSRV visual quirks.

## Rich artifact checkpoint

Merged PR #404 adds a separate bounded rich-artifact contract rather than embedding binary data in
the chat JSON frame.

The artifact path uses `CHAT_BRIDGE_ARTIFACTS_V1` and is correlated to the same public chat
event/source/channel identity as the rendered text frame. Bounds are enforced before final Discord
send:

- maximum 4 PNG artifacts;
- maximum 256 KiB per file;
- maximum 448 KiB raw aggregate artifact bytes;
- bounded StaffBot artifact cache;
- expiry/reconnect cleanup;
- text-only fallback when optional artifacts are absent or cannot be prepared safely.

StaffBot sends the styled text and available files as one final Discord message when attachment
permissions and preparation succeed. It does not retry a file-bearing REST request as text-only
after submission because delivery is ambiguous and a retry could duplicate chat.

Merged PR #406 adds a temporary staging compatibility provider for the currently installed
InteractiveChat + InteractiveChatDiscordSrvAddon image renderer. It supports held-item, inventory,
and Ender-chest placeholders while keeping live Bukkit player access on the player scheduler.
Async renderer snapshots use a deterministic synthetic UUID so upstream renderer branches cannot
resolve the snapshot back to the online `ICPlayer`. The temporary provider registers at
`ServicePriority.Lowest` so a permanent provider supersedes it automatically.

The specialized upstream player-body inventory renderer is intentionally not used because it
re-enters live player state during async rendering. Exact permanent renderer parity remains a
separate GPL-compatible companion/fork task.

## Sender/account-link presentation

StaffBot may enrich the server prefix using the authoritative current Minecraft -> Discord link
projection when the moderation read runtime is available. This is presentation metadata only.

Rules:

- the RoseChat-resolved Minecraft sender/rank/message line remains unchanged;
- current link reads are cached for 30 seconds in a bounded 4,096-entry cache;
- link lookup failure degrades to the normal unlinked prefix and never blocks Discord delivery;
- Discord roles are never consulted for chat authority or routing;
- StaffBot never performs a Discord REST lookup merely to decorate chat;
- if the linked Discord member/user is already present in JDA's local cache, its escaped display
  name is shown as `[SMP · @DisplayName]`;
- if the authoritative link exists but JDA has no cached display name, the prefix is
  `[SMP · linked]`;
- if no current link is available, the existing `[SMP]` prefix is preserved;
- raw Discord user IDs are never emitted into public chat;
- cached presentation state is cleared with the Discord runtime lifecycle.

The linked display name is ordinary escaped text, not a Discord mention token, and allowed mentions
remain disabled on the final send.

## Authority and remaining scope

The chat transport now has an explicit `DISABLED / SHADOW / AUTHORITATIVE` migration model.
AUTHORITATIVE does not delete or disable DiscordSRV globally. After replacement readiness passes,
EnthusiaStaff acquires RoseChat's reversible legacy-Discord suppression registration only while
the Paper transport surfaces, optional rich-provider requirement, and fresh StaffBot
Discord-publishing lease are all ready. Rollback, bridge teardown, or readiness-lease expiry
releases that registration before replacement teardown.

The exact cutover/rollback procedure and staging acceptance matrix are in
`docs/discord-chat-cutover.md`.

Still separate from the chat transport itself:

- permanent GPL-compatible InteractiveChat renderer companion/fork;
- account-link import/ownership completion;
- managed-role synchronization cutover;
- console forwarding replacement;
- any other remaining DiscordSRV responsibility under #264;
- final DiscordSRV jar/config removal after all owners are migrated and validated.
