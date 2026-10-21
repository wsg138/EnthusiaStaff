# Isolated TEST-only StaffBot chat runner

Status: **source-only candidate, not deployed**. Production StaffBot and DiscordSRV must not be restarted for this trial. Track under issue #264.

## Purpose

The normal StaffBot entry point starts the primary staff Discord application before it starts a secondary chat application. Running a second full StaffBot against the shared main guild could duplicate staff commands and account-age listeners. StaffBotIsolatedChatApplication avoids that by launching **only** the separate chat-only JDA gateway and authenticated Velocity transport, without moderation, age gate, staff-command listeners, role sync, database reads, account linking or web service.

The runner pins the STAGING environment to the existing Enthusia Discord guild and the approved private #staff-bot-testing channel. Its single accepted outbound route is TEST/global to the pinned channel. It rejects extra routes, Discord-to-Minecraft ingress, AUTHORITATIVE mode, the staff Discord application identities and missing chat-only credentials. The normal StaffBot entry point is unchanged.

**Known parity limitation:** With no identity DB/read service, this isolated runner presents Minecraft usernames, not Discord-linked main-account mentions. S02–S05 remain blocked even if plain text and attachments work.

## Required hold points before starting

1. Obtain a separately controlled staging JVM/container, not the production StaffBot service, and record the candidate source SHA and shaded JAR SHA-256. Do not modify production StaffBot startup or its current chat configuration file c.
2. Verify the chat-only Discord application has view/send/attachment/embed access only to the approved private channel. Do not grant staff command, kick, guild role, or production messaging permissions.
3. Verify private Velocity TLS trust-store and the **dedicated STAFFBOT_TEST authenticated peer**, with a separate HMAC secret-environment entry, then verify network reachability without logging secrets. This peer must be explicitly registered by the proxy operator in a separately approved, reversible maintenance window. Do not reuse production STAFFBOT peer identity or HMAC key, and do not change or restart live Velocity just to make the trial work. Without a separate proxy peer this isolated runner intentionally **cannot connect**.
4. Coordinate TEST Paper maintenance and rollback. Disable test DiscordSRV's configured legacy console destination, chat forwarding, command execution, other announcements, and any unrelated guild writer before enabling Paper SHADOW. A currently invalid legacy token is not durable containment.
5. Run tools/staffbot-chat-preflight/check.py against *real protected* candidate file copies, selecting --staffbot-environment staging for this separate process. Require every check PASS; that alone does not prove JDA permissions or Velocity/Paper readiness.
6. Preserve the production DiscordSRV path, SMP/global route, moderation, links and role writers. Do not uninstall the addon during the text-only trial.

## Startup only after all hold points

Put the chat bridge settings in a private file in the isolated runtime's working directory. Use the existing allowlisted StaffBot chat-file format. Include SHADOW, exactly one TEST/global mapping to the trusted pinned channel, no ingress, a distinct chat-only application ID/token, TLS, HMAC peer credentials and transport address. Never commit that file or expose secrets through a shell argument. The primary StaffBot Discord token is **not required**. Before a smoke test can connect, an authorized proxy operator must add a separate, **disabled-by-default** backend secret environment mapping for STAFFBOT_TEST, with its own HMAC key matching the isolated runner's CLIENT_SECRET, and perform a reviewed Velocity rollout. Never copy or overwrite the STAFFBOT production peer secret. Until that happens, the expected safe result is an unauthenticated/disconnected test peer and a failed smoke check.

Build exact-head shaded StaffBot JAR with Java 25, then perform a **nonposting** smoke test:

~~~text
java -cp EnthusiaStaff-StaffBot.jar net.enthusia.staff.discordbot.StaffBotIsolatedChatApplication --chat=private-chat.properties --smoke-test
~~~

The smoke test exits successfully only if the separate Discord chat application identity, pinned guild/private channel, and authenticated Velocity transport become ready. It sends no chat message, does not start the primary moderation bot, and does not turn on TEST Paper SHADOW. It cannot establish that TEST Paper is actually publishing.

After smoke success, start one supervised instance in the isolated service without the smoke argument:

~~~text
java -cp EnthusiaStaff-StaffBot.jar net.enthusia.staff.discordbot.StaffBotIsolatedChatApplication --chat=private-chat.properties
~~~

Observe isolated_chat_discord_ready, Velocity transport connectivity using the distinct STAFFBOT_TEST peer, and rollback capability. Only then consider enabling **TEST Paper SHADOW**, and send synthetic TEST Minecraft-origin text to the private channel. Stop the test and restore TEST-only baseline if there are public posts, unintended mentions, duplicates, command/role changes, source leaks or chat disruption.

## Safety and remaining gates

- This is a separate JVM and entry point, with no full staff gateway, moderation runtime, account-age gate or incoming Discord chat.
- The gateway uses the separate chat application. With empty ingress, it requests no guild member or Message Content intents and registers no staff interaction listeners.
- Readiness requires both the pinned Discord identity and the distinct STAFFBOT_TEST Velocity TLS authenticated peer. Connected does not mean the TEST backend is publishing.
- This staging runner lacks Discord account-link lookup and cannot satisfy linked-main mention cases. Do not count them as passed.
- IC-01..IC-14 rich item/tooltip visual parity and D13 roles/console/moderation acceptance remain distinct production cutover requirements.
- The process needs an authorized service manager to supervise and stop it; do not launch untracked SSH background processes or change the live StaffBot process.

**No runtime deployment or Discord test has been performed by creating this runbook.**
