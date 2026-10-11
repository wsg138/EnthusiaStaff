# Moderation API: staged post-send mirror publisher (explicit opt-in)

Status: code-only staging integration; **disabled by default**. This feature
neither deletes Discord/Minecraft messages nor activates inference. It has
never been authorized for a live cutover, and a real moderation classifier
has not passed model acceptance.

## Behavior

StaffBot's existing, authenticated, routed public RoseChat chat sends record
the **actual post-send JDA snowflake** in `DiscordChatPublication`. Only
after the staging chat bridge is marked ready, the optional
`AiMirrorRegistrationPublisher` queues a best-effort HTTP request:

- `POST /v1/mirror-aliases` to an explicitly configured private endpoint;
- `Authorization: Bearer <private-file credential>`,
  `X-Client-Id: <registered bridge client>`;
- JSON: `canonical_message_id`, `canonical_text` (original undecorated
  RoseChat text), `platform: discord`, `channel_profile: discord_general`
  or `discord_gaming`, `scope_id` (actual guild), `channel_id`
  (actual JDA destination) and `external_message_id` (actual returned ID).

Only the configured staging guild and current chat-bridge destination
channels are eligible. The HTTP URL must be HTTPS or explicit loopback HTTP,
must have a port and the exact `/v1/mirror-aliases` path, and must not
carry query parameters, fragments or user information. Redirects are
disabled. The queue is bounded to 64, single-threaded and daemonized,
with a 2-second HTTP timeout. API errors, timeouts, 409 conflicts, worker
saturation and shutdown **fail open**, are not retried and never trigger
a Discord repost/deletion or Minecraft sanction. There is no logging of
message text, IDs or tokens.

## Configuration (no settings changed by this PR)

All settings must be supplied in the StaffBot process environment; the
credential itself is stored in a private file, not the environment:

- `ENTHUSIA_STAFF_BOT_AI_MIRROR_REGISTER_ENABLED=true`
- `ENTHUSIA_STAFF_BOT_AI_MIRROR_REGISTER_URL=https://<approved-host>:<port>/v1/mirror-aliases`
- `ENTHUSIA_STAFF_BOT_AI_MIRROR_CLIENT_ID=<dedicated-client-id>`
- `ENTHUSIA_STAFF_BOT_AI_MIRROR_TOKEN_FILE=<private-token-file>`
- `ENTHUSIA_STAFF_BOT_AI_MIRROR_DISCORD_PROFILE=discord_general`
  (or `discord_gaming`, **must match the staged routed channel**)

**Default is disabled** even when the chat relay itself is enabled.
Explicit opt-in is accepted only in `StaffBotEnvironment.STAGING` with
a configured staging chat relay; any attempt to opt in in production
fails startup. The API credential must have **only** `mirror:register`;
the ordinary `moderate` credential does not grant this permission. Do
not commit the credential file or paste it into chat or public logs.

## What this does NOT yet do

- It does not check the model's accuracy or route Minecraft/Discord
  messages into an accepted inference model.
- It does not interpret a returned `BLOCK` decision; HTTP 200 is
  treated only as registration confirmation, not permission to delete.
- It does not handle durable registration retries through process restart
  or a full late-deletion queue.
- It does not delete a Discord post, forward private chat, or impose
  strikes, mutes, bans, or other punishment.

**Before further staging activation**, add integration tests with a
real test-only API for source FINAL/404, 409, timeout, and exact mirror
identity matching. Then add a separate explicitly authorized deletion
bridge that acts only on complete structured `related_messages` IDs
and proves correct behavior across restart and discord failures. No
production deployment or live enforcement without owner approval.

## Verification

Expected: full `:staff-bot:check`, unit tests for default-off,
staging-only guards, channel/guild fencing, serialized source fields,
headers and fail-open behavior; public-repo exact-head CI before merge.
No paid runners/compute, token rotation, production changes or live tests.
