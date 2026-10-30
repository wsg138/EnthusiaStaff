# StaffBot Discord gateway reconnect diagnostics

This document describes the **read-only** diagnostic lines added for StaffBot's
two existing JDA gateway sessions. It does not change reconnect or chat behavior,
provision tokens, enable Discord ingress, or require a new Discord application.

## What to watch

- `discord_gateway_session_disconnected role=staff`: the staff-command JDA session lost its gateway connection.
- `discord_gateway_session_disconnected role=public_chat`: the **separate existing** public-chat JDA session lost its gateway connection; the runtime pauses outbound chat until its identity check succeeds.
- `discord_gateway_session_restored role=... mode=resumed`: JDA resumed the same session.
- `discord_gateway_session_restored role=... mode=recreated`: JDA established a new session.
- `discord_gateway_session_restored role=... mode=ready`: JDA Ready fired after a prior disconnect. Initial process startup does **not** emit a recovery line.
- `staff_bot_ready` / `public_chat_ready`: the **separate**, required runtime identity and permission checks succeeded.

Disconnect fields: `remote` (whether Discord closed it), `close_code`
(mapped numeric Discord close code or `unknown`), `close_kind` (mapped enum
name or `unmapped`), `total_events`, and `episode_events`. Restored fields:
`downtime_ms` (monotonic elapsed duration) and episode/total counts.

A JDA session-recovered line **does not** prove Discord identity validation or
application functionality. Confirm the corresponding `*_ready` line afterward.
Two back-to-back disconnect callbacks without a resume count as one outage
episode. No close-frame payloads, token values, Discord IDs, message text, URLs
or stack-trace contents are copied to these diagnostic logs.

## Operator acceptance and safe rollback

Keep the current running StaffBot process untouched until the updated JAR
passes hosted checks and a separately approved maintenance window is arranged.
This diagnostic-only change can be tested on a separate TEST StaffBot run or,
later, with a **StaffBot-only** stop/JAR swap/start. Do not restart Velocity,
SMP, TEST Paper, or DiscordSRV and do not run the previous six-file installer.

For a later deployment, retain the existing known-good
`EnthusiaStaff-StaffBot.jar` under an unused rollback name **before** replacing
it. Confirm both `staff_bot_ready` and `public_chat_ready`. If either is
missing, restore the former JAR and leave configuration unchanged.

When investigating repeated disconnections, collect only the new
`discord_gateway_session_*` lines and nearby `*_ready` timestamps, excluding
credentials and unrelated user messages. Mapped codes help classify whether
Discord requested a reconnect, invalidated a session, or reported an
authentication issue; an `unknown` code may mean a network-level closure and
must not be interpreted as a particular Discord fault.

The existing Cloudflare tunnel can independently fall back from QUIC to HTTP/2;
those tunnel messages are **not** Discord gateway close codes.
