# bloom-test1 private Discord chat SHADOW — audited activation plan

**Status as of 2026-10-16: candidate configuration preflight PASS; runtime acceptance NOT RUN.**
Owner approved using `bloom-test1` and a private channel in the **existing Enthusia Discord guild**. The working live `#in-game-chat` channel is **comparison only**; never route test output there.

This is a **source and operations runbook**, not a deployment authorization or evidence that any changes were made on a server. Preserve the live DiscordSRV path and all production moderation, console and Discord roles.

## Read-only evidence and deployed artifacts

Pinned SFTP inventories, sanitized copies of on-disk config, and **downloaded JAR bytecode inventories** were inspected on 2026-10-16. No live tokens, raw linked identities, or Discord snowflake values are included here.

| Subject | Observed state |
| --- | --- |
| Test Paper JAR | `EnthusiaStaff-Paper.jar`, 11,426,475 bytes; SHA-256 `b68522fa6e702a16794e4c15aa85037754bf3177fb4f56ed69b1cb093ec59012` |
| Current StaffBot JAR | `EnthusiaStaff-StaffBot.jar`, 19,007,496 bytes; SHA-256 `3c87f08b9fc0dede97384d4bf4e12c7f91e68eb109c2ef10c1b1e5a5db80c4f3` |
| JAR capability check | Paper contains `DiscordChatBridgeMode` and the outbound wire contract. StaffBot contains `PublicChatDiscordConfiguration`, `StaffBotChatBridgeConfiguration`, `StaffBotEnvironment` and the outbound wire contract. **Class presence does not establish runtime activation or wire compatibility.** |
| Test Paper on disk | `server-id: TEST`; authenticated `channel.enabled: true`; no `discord-chat-bridge` section, so SHADOW is **not** explicitly selected |
| Live StaffBot private chat-only file `c` | `SHADOW`, only the pinned private outbound channel and no ingress, but its single route is `SMP/global` rather than `TEST/global`; effective process startup / secondary JDA readiness unverified |
| Test DiscordSRV on disk | Minecraft↔Discord forwarding both false; chat-command feature false; **legacy console destination still populated and equal to SMP**. The test server's current startup log shows the old DiscordSRV bot token is invalid and its login was disabled. A failed login is **not** a durable safety control. |
| Velocity on disk | Backend secret-environment declarations exist for `TEST` and `STAFFBOT`. There is no verified connected-peer or Discord readiness proof. |
| Role/console safety | Main guild role mutations, server-wide join/kick tests, and all production console dispatches remain **out of scope**. |

The JAR hashes are an **observed baseline**, not an instruction to deploy either JAR. They may become stale when other workers update servers.

## Real-input offline preflight, performed

The merged `tools/staffbot-chat-preflight/check.py` from [#642](https://github.com/wsg138/EnthusiaStaff/pull/642) ran against **sanitized copies of current on-disk configuration** and source-pinned channel identity. The checks print no channel IDs or credentials:

- **Actual on-disk state:** **10 PASS, 3 BLOCKED**, exit 2. Blocked: Paper chat SHADOW not enabled, StaffBot route `SMP/global` cannot receive `TEST/global`, and test DiscordSRV console destination still configured.
- **Sanitized candidate only (never deployed):** **13 PASS, 0 BLOCKED**, exit 0 after exactly those three corrections. The candidate's bot-token field is a fixed *presence marker*, **not the real credential**; its PASS cannot verify any token, online bot, JDA intent, effective guild permission, TLS/HMAC peer or application session.
- Source suite: 11/11 synthetic regression cases pass; #642 merged to `main` with green hosted CI and zero new Codacy findings.

These local sanitized candidate files reside in an ignored Windows build/audit directory; they are not server configs, not deployable, and not committed.

## Three exact candidate changes — TEST only

1. **On `bloom-test1` Paper config only**, append the missing configuration at the YAML root, retaining its existing `network.server-id: TEST` and authenticated channel fields:

   ```yaml
   discord-chat-bridge:
     mode: SHADOW
   ```

   The feature is opt-in and must not use `AUTHORITATIVE` or the cutover acknowledgement. Confirm the deployed Paper JAR supports the selected mode and the installed RoseChat bridge API before activation.

2. **On `bloom-test1` DiscordSRV config only**, clear `DiscordConsoleChannelId: ""`. Keep Minecraft→Discord chat false, Discord→Minecraft chat false and Discord chat-command execution false. Because its legacy channel map still shares production IDs, **disable any other announcements, webhooks, startup/shutdown messages and role writers that could reach production**; confirm their effective behavior rather than relying on the currently invalid test token. Never replace the invalid test token with the production bot token.

3. **On a separately controlled, nonproduction chat-only StaffBot runtime or approved isolated trial configuration**, use `SHADOW`, a **single** `TEST/global=<PINNED_PRIVATE_TEST_CHANNEL_ID>` outbound mapping, **no ingress**, and the existing distinct chat-only Discord application, with permission to view/send/attach/embed **only** in the private test channel. Do **not** overwrite production StaffBot file `c` or switch the running production `SMP/global` route and restart primary StaffBot for convenience. Test-only chat configuration must not acquire moderation roles, command execution or account-link write authority.

   The pinned channel value must come from trusted deployed `StaffBotEnvironment.STAGING.testChannelId()`, never a guessed ID or arbitrary user-supplied routing token. No real token, secret, password, ID or account data belongs in source, issues or PR comments.

**Dependency:** Step 3 currently lacks verified independent runtime deployment / reload support. Do not proceed to Step 1 or Step 2 in isolation if no safe chat-only receiver will be available; the test server is used by other projects and restarting it is not free of consequences.

## Mandatory activation hold points

Before the first message in the private Discord channel:

1. Coordinate test1 restart/service windows; record all test1 JAR hashes, private config backups and restore paths. Back up **test1 only**; do not touch the production Paper/Velocity/StaffBot files.
2. Verify private test-channel visibility and send/embed/attach permissions for the **chat-only** application; reject broad role permissions and any public channel visibility. Observe bot application identity and active `public_chat_ready` from its own running logs. Source or file existence is not proof.
3. Verify the authenticated `TEST` Paper peer and auxiliary `STAFFBOT` peer on Velocity, correct separate environment keys and TLS/HMAC alignment. `TEST` appears in Velocity config, but this is not a successful handshake.
4. Use the **real unredacted configs only in authorized, private storage** to run `tools/staffbot-chat-preflight/check.py`; require all checks PASS. A successful sanitized candidate check by itself is insufficient.
5. Recheck that all production DiscordSRV destinations remain excluded from test1 activity and that the test process cannot invoke live server commands or guild-wide role changes. Keep StaffBot chat ingress disabled for initial outbound tests.
6. Activate **only** test1 Paper SHADOW and an isolated test chat-only runtime with verified rollback. Observe readiness; only then send a synthetic text message from test1 and compare a private test-channel post against the existing live DiscordSRV presentation. Run [S01–S18](staffbot-chat-shadow-acceptance.md) and IC-01..IC-14, including thumbnail + Minecraft-style tooltip and private/staff no-leak checks.
7. Immediately halt and restore test1 baseline on any post to a public/staff/console production channel, unexpected user mention, loop, unintended moderation action, duplicate, JDA ambiguity, peer failure, or Minecraft chat disruption.

## Rollback

Stop the **test-only** chat bridge, restore the exact prior **test1** Paper and DiscordSRV configs/JARs, revert only the isolated chat route, and verify legacy production DiscordSRV remains untouched. Compare stored hashes and private test-channel output. Do **not** restart or alter the live SMP, its DiscordSRV bot, the production StaffBot moderation process, or guild role holders merely to complete the trial.

## Status boundary

**Read-only verification and candidate preflight complete. Active integration smoke test not yet run. Release/cutover gate remains closed.** Production DiscordSRV retirement must also separately prove console, account linking, managed roles, InteractiveChat output, staff-rank parity, and rollback under [#264](https://github.com/wsg138/EnthusiaStaff/issues/264).
