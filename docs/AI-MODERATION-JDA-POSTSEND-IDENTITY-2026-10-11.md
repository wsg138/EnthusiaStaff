# AI moderation bridge: actual JDA post-send identity (staged, default off)

Status: infrastructure only. **No API publisher is installed, no Discord
message deletion is enabled, and production behavior is unchanged.** This
stage is deliberately restricted to the existing configured **STAGING**
chat bridge. Do not enable cutover or punishments.

## Captured identity

When `JdaDiscordGateway` completes a public Minecraft → Discord post,
its returned JDA `Message.getId()` is the actual Discord snowflake.
The optional `DiscordChatPostObserver` receives a
`DiscordChatPublication` containing:

- exact Discord guild and channel IDs from the actual destination;
- the **JDA-returned**, never synthesized, Discord message snowflake;
- RoseChat's original `canonicalMessageId`;
- original undecorated chat text (`plainText` for V1, or
  `canonicalPlainText` for styled/chat-artifact posts).

This is supported on all three delivery paths: ordinary text, rendered
text fallback, and rendered text + file attachments. No observer is
registered by the current application, so **the new code sends nothing to
the Moderation API by itself**.

The optional observer can be installed **only once before JDA starts**,
with an existing configured staging chat bridge. It is disabled
automatically for production environments. It must execute only
nonblocking bounded queue admission, not HTTP or Discord deletion on the
Discord delivery thread. Exceptions in the observer cannot cause a
successful Discord send to be retried or converted to failure; the
identity handoff is best effort. Posted text is never logged.

## Future moderated bridge integration gates

1. Introduce an explicitly staging-only, default-off publisher with
   `mirror:register` credentials separate from `moderate`; secrets
   stay in private config files, never Git or server logs.
2. Send `POST /v1/mirror-aliases` only for verified public RoseChat
   messages: `canonical_message_id`, original undecorated
   `canonical_text`, `platform=discord`,
   `channel_profile=discord_general` or `discord_gaming`,
   actual Discord `scope_id` (guild), `channel_id`, and posted
   `external_message_id` (JDA snowflake).
3. Preserve **all original registration fields on retries**; different
   channel profile/identity/content must get HTTP 409 and must not
   be interpreted as confirmation. Never match/delete by chat text,
   usernames, or an invented Discord ID.
4. Reconcile late `BLOCK` decisions only against the exact returned
   structured `related_messages` refs on the intended platform.
   Deduplicate deletion operations across restarts, respect exclusions,
   and prove failure/retry behavior in an isolated test guild.
5. Prove bridge never forwards private/staff/ticket content, and add
   real end-to-end staging tests covering original and mirrored
   deletion after delayed classification and after a restart.
6. Keep AI strikes, mutes, bans, and punishment recommendations
   disabled. Production deployment requires separate owner approval
   and a validated model; neither is present now.

## Verification

Verified on a clean, separate Windows local checkout using the project's
Gradle wrapper and Java runtime:

- `:staff-bot:test`: PASS
- `:staff-bot:check`: PASS, including shaded jar runtime/integrity inspection
- New `DiscordChatPublicationTest`: PASS

GitHub Actions may be unable to allocate hosted runners under the
account's strict $0 Actions overage budget; local checks must not
be misrepresented as hosted CI. No paid compute or live connection used.
