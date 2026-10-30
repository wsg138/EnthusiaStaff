# StaffBot chat staging — fail-closed configuration preflight

This is a **read-only** checker for the owner-approved test path:

- `bloom-test1` Minecraft backend `TEST`;
- the existing Enthusia Discord guild's pinned private `#staff-bot-testing` channel;
- production `#in-game-chat` used only as a visual comparison baseline.

It reads config files **locally** and never connects to Discord, Velocity, Paper, MariaDB, or any network service. It reports *only check names and PASS/BLOCKED*, not configuration values, IDs, tokens, passwords or usernames. Running it does not turn on SHADOW or grant permission for a live test.

## Inputs

Use copies of these **existing** configuration sources in an authorized, secure local folder, not the repository. Do not add token-bearing config copies to Git.

- `--paper`: test backend `plugins/EnthusiaStaff/config.yml`;
- `--discordsrv-test`: test backend `plugins/DiscordSRV/config.yml`;
- `--discordsrv-live`: *read-only* SMP DiscordSRV config, used only to detect collisions;
- `--chat`: the **candidate** chat-only StaffBot properties file (may contain credentials; do not share it);
- `--staffbot-environment`: **required** running StaffBot environment, `production` or `staging`; this must match the actual process, not merely a filename;
- `--staffbot-environment-source`: checked-out `staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffBotEnvironment.java`.
- `--isolated-test`: optional flag for the separate test-only JDA runner in draft #658; requires `--staffbot-environment staging`.
- `--velocity-config`: read-only copy of Velocity `plugins/enthusiastaff/config.properties`; required with `--isolated-test`.
- `--production-chat`: read-only copy of the currently deployed production StaffBot public-chat settings file (e.g. `c`); required with `--isolated-test`. Keep the whole file private, including its token.

Run from the repository root after gathering authorized local config copies:

```sh
python tools/staffbot-chat-preflight/check.py \
  --paper PRIVATE_TEST_PAPER.yml \
  --discordsrv-test PRIVATE_TEST_DISCORDSRV.yml \
  --discordsrv-live PRIVATE_READONLY_SMP_DISCORDSRV.yml \
  --chat PRIVATE_CHAT_BRIDGE.properties \
  --staffbot-environment production \
  --staffbot-environment-source staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffBotEnvironment.java
```

For the **separate TEST-only** runner, use this **complete, alternative command**. Set the environment to **staging instead of production**; do not pass both environments:

```sh
python tools/staffbot-chat-preflight/check.py \\
  --paper PRIVATE_TEST_PAPER.yml \\
  --discordsrv-test PRIVATE_TEST_DISCORDSRV.yml \\
  --discordsrv-live PRIVATE_READONLY_SMP_DISCORDSRV.yml \\
  --chat PRIVATE_ISOLATED_TEST_CHAT.properties \\
  --staffbot-environment staging \\
  --staffbot-environment-source staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffBotEnvironment.java \\
  --isolated-test \\
  --velocity-config PRIVATE_READONLY_VELOCITY.properties \
  --production-chat PRIVATE_READONLY_PRODUCTION_CHAT.properties
```

The isolated checks require `channel.enabled=true`, the existing `STAFFBOT` and `TEST` peer mappings, and a separate `STAFFBOT_TEST` mapping using a secret **environment-variable name distinct from every other configured peer**. They deliberately do not inspect the secret values or prove that HMAC material differs. Velocity must reject shared key material at startup. The candidate chat application ID must also differ from the running production public-chat application ID, not merely from the staff bot IDs. The checker never inspects or outputs bot tokens. Actual token-to-ID binding, active-session exclusivity, TLS connectivity and permissions still need independent proof.

**Exit status:** `0` means all config checks passed, `2` means blocked or unreadable. Neither result establishes that a bot is connected or ready.

## Existing StaffBot process: shared-JDA TEST peer

For the existing Bloom StaffBot and Discord public-chat session, use the
alternative --shared-jda-test mode. This does not start another bot or JDA.
Do not combine with --isolated-test or --production-chat.

Use only local protected read-only copies of the production StaffBot chat
properties, TEST/SMP DiscordSRV configs and Velocity configuration, including
the private Velocity secrets.properties file. Never commit or disclose them.

```sh
python tools/staffbot-chat-preflight/check.py \
  --paper PRIVATE_TEST_PAPER.yml \
  --discordsrv-test PRIVATE_TEST_DISCORDSRV.yml \
  --discordsrv-live PRIVATE_READONLY_SMP_DISCORDSRV.yml \
  --chat PRIVATE_READONLY_PRODUCTION_STAFFBOT_CHAT.properties \
  --staffbot-environment production \
  --staffbot-environment-source staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffBotEnvironment.java \
  --shared-jda-test \
  --velocity-config PRIVATE_READONLY_VELOCITY.properties \
  --velocity-secrets PRIVATE_READONLY_VELOCITY_SECRETS.properties
```

The primary StaffBot route remains exactly SMP/global to the pinned private
channel. TEST/global is derived by the runtime when the default-off TEST switch
is explicitly enabled. Read-only checks compare decoded HMAC material against
the STAFFBOT and STAFFBOT_TEST values in the **provided** Velocity secret file,
but environment overrides can take precedence at startup. Runtime peer handshakes,
JDA identity and Discord permissions require separate evidence. The checker
never writes configs or sends any Discord messages.

## Rules checked

1. The pinned private channel doesn't equal the global, staff or console DiscordSRV destination on test or production.
2. Paper identifies its backend in `network.server-id` (not an unrelated YAML key) and has `discord-chat-bridge.mode: SHADOW` (or backward-compatible explicit shadow flag), with the authenticated channel configured.
3. StaffBot's candidate is **SHADOW**, has the exact production migration acknowledgement when the running StaffBot environment is `production`, and has **exactly one route** from that Paper backend's `<server-id>/global` to the pinned private channel, has no ingress, and has a distinct chat-only application identity/credential.
4. Test-server legacy DiscordSRV has Minecraft↔Discord chat forwarding OFF, chat-command execution OFF, and **no console Discord destination**. A disconnected/invalid bot token is **not** an acceptable substitute for this durable containment.

### Discovered on 2026-10-16

Read-only inspection showed the current `bloom-test1` Paper server ID is `TEST`, its chat-bridge section is absent (therefore disabled), and the existing chat-only StaffBot file `c` routes `SMP/global` to the private channel. This route cannot carry `TEST/global` traffic. The test server also had a configured legacy console destination, although its chat forwarding and chat command execution were disabled. Its latest startup log reports DiscordSRV disabled because its bot token is invalid. These are **observations, not an instruction to modify or repair credentials**.

### Gates this tool cannot verify

For the isolated candidate, no success is possible while Velocity lacks `STAFFBOT_TEST`; this is intentional. The checker cannot inspect or verify the actual peer secrets.

It cannot independently prove that `--staffbot-environment` matches the running process or that a live process accepted the migration acknowledgement. It does not verify connected bot applications, effective Discord channel permissions, readiness messages, live Paper/Velocity peer registration, current running config vs on-disk files, Minecraft Staff Mode, player/privacy boundaries, real item tooltip fidelity, database isolation, or rollback proof. Do not run any private-channel message, real command or role mutation until those independent gates are resolved and the shared test server's users are coordinated.

To run the **offline tests** (which use synthetic IDs/credentials only):

```sh
python -m unittest discover -s tools/staffbot-chat-preflight -p "test_*.py" -v
```

Related: `docs/staffbot-test1-discord-isolation-20261015.md`,
`docs/staffbot-chat-shadow-acceptance.md`, issues #264 and #268.
