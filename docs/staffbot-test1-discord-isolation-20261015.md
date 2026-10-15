# StaffBot replacement — bloom-test1 Discord isolation preflight

**Recorded 2026-10-15.** Applies to approved **nonproduction** `bloom-test1` only. This document records read-only observations and prescribes safety gates; it does **not** mean staging deployment, slash-command invocation, Discord changes, or server restart has happened.

## Verified inventory and exposure

A read-only pinned SFTP inventory of `bloom-test1` found the currently installed `EnthusiaStaff-Paper.jar`, a test LumaGuilds build (`LumaGuilds-b401e3d6-UNMERGED-TEST.jar`), DiscordSRV, InteractiveChat and its DiscordSRV addon. This is a shared test server with other unmerged projects and existing player/world/database state, not a disposable fresh installation.

Comparison against `bloom-smp` read-only configuration (actual Discord snowflakes omitted deliberately):

| Property | Observation |
|---|---|
| DiscordSRV `Channels` (global and staff) | **Exactly equal to production** |
| DiscordSRV `DiscordConsoleChannelId` | **Exactly equal to production** |
| DiscordSRV chat↔Minecraft forwarding flags | Enabled on test1; same settings as production |
| DiscordSRV chat-command execution | Enabled on test1 and production; shared command prefix |
| Staff database JDBC URL and username | Different between test1 and production; credentials intentionally not inspected |
| Discord bot tokens / guild separation | **Not proven** (secret values are redacted; never infer isolation) |
| LumaGuilds code / version parity | **Not equal**: test1 uses a different unmerged build; SMP has `LumaGuilds-3.0.23.jar` |
| Production LumaGuilds role writer | Present in deployed JAR; absent from Luma `main` |
| Actual Discord role holder inventory | **Not retrieved**, so no parity asserted |

**Consequences:** The Minecraft test process and Staff database may be different, but **Discord destinations are currently shared**. Never mistake "testing on bloom-test1" for "testing in a separate Discord environment."

## Zero-change default rules

Until a dedicated nonproduction Discord route and safe server configuration are verified:

- Do not run any test which posts in a Discord channel, issues `/console` or legacy `!c`, mutates/reconciles a real Discord role, sends Discord webhook content, invokes the real JDA gateway with production credentials, or triggers legacy join/leave/restart notifications to the shared channels.
- Do not disable/replace DiscordSRV on Bloom SMP, modify its linked-account or role databases, or deploy/cut over production JARs.
- Use only read-only SFTP inventories, offline source/JAR inspection, local Gradle/Python tests, synthetic role/member fixtures, and source-only SHADOW policy calculations until isolation is established.
- Do not overwrite other active workers' test server configs/JARs, and do not expose token values, role/channel IDs, linked users, inventories, or DB dumps in PRs.

## Required preflight before active testing on test1

1. **Pin baseline:** record current test1 plugin filenames, source/test SHA-256, existing configs, LumaGuilds build provenance and restore procedure. Determine which other teams are using this shared test server.
2. **Isolate Discord:** use a dedicated *test Discord guild and channels* with a distinct test bot identity/credentials, or a separately approved no-egress/mock Discord service. Prove its channel/guild IDs do not match production. Config validation alone is insufficient: verify effective runtime after restart without sending a test to production.
3. **Disable legacy remote command exposure in test1 test policy:** no active DiscordSRV console bridge or role writer should share the production guild while testing the new console or managed-role bridge. Confirm the test bot cannot affect production roles/channels.
4. **Isolate data and endpoints:** independently verify all StaffBot/MariaDB, Luma SQLite, LuckPerms, Velocity, HTTP command bridge, role namespaces and proxy secrets target nonproduction instances. Distinct Staff JDBC URLs are useful evidence, **not sufficient** proof.
5. **Preserve rollback:** make a test1-only file/DB snapshot before any activation; write down exact JARs/configs and rollback; avoid touching SMP and persistent Discord account roles.
6. **Run acceptance:** first wrong-guild/channel and off-duty/unlinked/stale actor negative tests, body/HMAC/nonce freshness, command allowlist and no-retry, then safe `list` console read, bounded output, and synthetic managed-role changes only in test guild. For chat, test public/staff/private boundaries, duplicate/echo loops, textual outage fallback, and actual addon thumbnail + tooltip screenshots (IC-01..IC-14). Collect the three complete authorized D13 real SHADOW snapshots using the read-only `tools/d13-parity/` harness.
7. **Restore and inspect:** disable test toggles, restore baseline, compare plugin inventory/hashes, inspect errors and verify no Discord production channel or role saw any change.

## Linked work

- Staff migration umbrella: [EnthusiaStaff #264](https://github.com/wsg138/EnthusiaStaff/issues/264)
- Console staging/security: [`docs/discord-console-bridge-security-20261015.md`](discord-console-bridge-security-20261015.md), merged #629 (source only)
- D13 role preflight: [`docs/d13-parity-staging.md`](d13-parity-staging.md), `tools/d13-parity/`
- Chat acceptance: [EnthusiaStaff #268](https://github.com/wsg138/EnthusiaStaff/issues/268) and PR #568 (opt-in independent renderer; real staging unaccepted)
- Luma role eligibility: [LumaGuilds #13](https://github.com/wsg138/LumaGuilds/pull/13), merged to integration only

**Release gate remains CLOSED.** Production DiscordSRV retirement requires real nonproduction parity, reviewed configuration and explicit owner authorization; approving the use of `bloom-test1` is **not** approval to alter production Discord.
