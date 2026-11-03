# StaffBot replacement — bloom-test1 Discord isolation preflight

**Recorded 2026-10-15; amended 2026-10-16 for owner-approved private-channel testing in the existing Enthusia Discord guild.** Applies to approved **nonproduction** `bloom-test1` only. This document records read-only observations and prescribes safety gates; it does **not** mean staging deployment, slash-command invocation, Discord changes, or server restart has happened.

## Verified inventory and exposure

A read-only pinned SFTP inventory of `bloom-test1` found the currently installed `EnthusiaStaff-Paper.jar`, a test LumaGuilds build (`LumaGuilds-b401e3d6-UNMERGED-TEST.jar`), DiscordSRV, InteractiveChat and its DiscordSRV addon. This is a shared test server with other unmerged projects and existing player/world/database state, not a disposable fresh installation.

Comparison against `bloom-smp` read-only configuration (actual Discord snowflakes omitted deliberately):

| Property | Observation |
|---|---|
| DiscordSRV `Channels` (global and staff) | **Exactly equal to production** |
| DiscordSRV `DiscordConsoleChannelId` | **Exactly equal to production** |
| DiscordSRV chat↔Minecraft forwarding flags | As of 2026-10-16 both disabled in test1's on-disk config; live SMP still enabled. Effective runtime remains separately unverified. |
| DiscordSRV chat-command execution | As of 2026-10-16 disabled on test1; enabled on SMP. Test1 still has a shared legacy console destination configured. |
| Staff database JDBC URL and username | Different between test1 and production; credentials intentionally not inspected |
| StaffBot Discord guild/application routing | Source pins STAGING and PRODUCTION to the same Enthusia guild but different Discord application IDs; actual running bot credentials and permissions **not yet verified** |
| LumaGuilds code / version parity | **Not equal**: test1 uses a different unmerged build; SMP has `LumaGuilds-3.0.23.jar` |
| Production LumaGuilds role writer | Present in deployed JAR; absent from Luma `main` |
| Actual Discord role holder inventory | **Not retrieved**, so no parity asserted |

**Consequences:** The Minecraft test process and Staff database may be different, but **Discord destinations are currently shared**. Never mistake "testing on bloom-test1" for "testing in a separate Discord environment."

## Follow-up read-only runtime/config findings (2026-10-16)

- Current test1 startup log reports DiscordSRV rejecting an **invalid bot token** and disabling itself. Do **not** fix or borrow a production token for this test: failed legacy login is temporary containment, not proof that the shared Discord routes are permanently isolated.
- Test1 Paper config has `channel.enabled: true` and `server-id: TEST`, but **no `discord-chat-bridge` section**. The existing StaffBot SHADOW chat-only file `c` targets the pinned private channel but has an `SMP/global` route, which **does not match `TEST/global`**. Runtime connectivity remains unverified.
- The owner approved private-channel testing in the existing Enthusia Discord guild; `#in-game-chat` is a comparison-only baseline. The chat-only application's effective identity, permissions and `public_chat_ready` have not been independently confirmed.
- A [read-only configuration preflight](../tools/staffbot-chat-preflight/README.md) checks source/backend routing, legacy console exposure and private-channel collisions without printing secret material. Synthetic tests are included. Passing does **not** authorize runtime activation.

## Zero-change default rules

Until the pinned private test-channel route and safe test-server configuration are verified:

- Do not run any test which posts in a Discord channel, issues `/console` or legacy `!c`, mutates/reconciles a real Discord role, sends Discord webhook content, invokes the real JDA gateway with production credentials, or triggers legacy join/leave/restart notifications to the shared channels.
- Do not disable/replace DiscordSRV on Bloom SMP, modify its linked-account or role databases, or deploy/cut over production JARs.
- Use only read-only SFTP inventories, offline source/JAR inspection, local Gradle/Python tests, synthetic role/member fixtures, and source-only SHADOW policy calculations until isolation is established.
- Do not overwrite other active workers' test server configs/JARs, and do not expose token values, role/channel IDs, linked users, inventories, or DB dumps in PRs.

## Required preflight before active testing on test1

1. **Pin baseline:** record current test1 plugin filenames, source/test SHA-256, existing configs, LumaGuilds build provenance and restore procedure. Determine which other teams are using this shared test server.
2. **Isolate Discord messages within the existing Enthusia guild (owner-approved):** use the separate STAGING StaffBot application identity already defined by `StaffBotEnvironment`, and the pinned private `#staff-bot-testing` text channel from `docs/staffbot-chat-shadow-acceptance.md`. `#in-game-chat` is the **read-only visual baseline**, never a staging output target. Check actual IDs/permissions internally without committing them. Verify the pinned test channel differs from production `global`, `staff`, and `DiscordConsoleChannelId`, and that the staging bot can post **only** to the private test channel. Confirm its Discord identity/permissions from runtime, not merely its source enum; ordinary source configuration does not prove effective permissions. Staging `SHADOW` must have no production chat route and no default ingress route.
3. **Contain existing legacy test-server DiscordSRV:** its `global`, `staff`, and console destinations currently equal production. Before an active `bloom-test1` chat test, ensure legacy DiscordSRV test-server outbound chat/console/restart announcements cannot post into live Enthusia channels; stage any test1-only config adjustment with a rollback and check other projects using test1. Never change SMP DiscordSRV or its `#in-game-chat` output. **Console tests** may only dispatch to the isolated Minecraft test backend with fail-closed Staff Mode and a specific allowlisted private channel; no production command target.
4. **Isolate data and endpoints:** independently verify all StaffBot/MariaDB, Luma SQLite, LuckPerms, Velocity, HTTP command bridge, role namespaces and proxy secrets target nonproduction instances. Distinct Staff JDBC URLs are useful evidence, **not sufficient** proof.
5. **Preserve rollback:** make a test1-only file/DB snapshot before any activation; write down exact JARs/configs and rollback; avoid touching SMP and persistent Discord account roles.
6. **Run acceptance:** begin with chat-only SHADOW (private `#staff-bot-testing` output; normal `#in-game-chat` as reference) and prove wrong-channel rejection, public/staff/private boundaries, duplicate/echo isolation, textual outage fallback, and addon thumbnail + tooltip visual comparisons (S01–S18, IC-01–IC-14). Inbound testing remains disabled until explicitly scoped to `bloom-test1`. For console, run off-duty/unlinked/stale-actor, wrong-channel, body/HMAC/nonce freshness, allowlist/no-retry rejection tests first; only then, if nonproduction Paper endpoints and command permissions are confirmed, try a harmless `list` on test1. **Do not test real role mutations or destructive Discord join/kick actions in the main Enthusia guild.** Role parity remains read-only/synthetic; collect three complete D13 SHADOW windows via `tools/d13-parity/` without Discord writes.
7. **Restore and inspect:** disable test toggles, restore baseline, compare plugin inventory/hashes, inspect errors and verify no Discord production channel or role saw any change.

## Linked work

- Staff migration umbrella: [EnthusiaStaff #264](https://github.com/wsg138/EnthusiaStaff/issues/264)
- Console staging/security: [`docs/discord-console-bridge-security-20261015.md`](discord-console-bridge-security-20261015.md), merged #629 (source only)
- D13 role preflight: [`docs/d13-parity-staging.md`](d13-parity-staging.md), `tools/d13-parity/`
- Chat acceptance: [EnthusiaStaff #268](https://github.com/wsg138/EnthusiaStaff/issues/268) and PR #568 (opt-in independent renderer; real staging unaccepted)
- Luma role eligibility: [LumaGuilds #13](https://github.com/wsg138/LumaGuilds/pull/13), merged to integration only

**Release gate remains CLOSED.** The owner approved use of the **main Enthusia Discord guild's private testing channel**, not production chat output, role mutation, server-wide moderation, slash-command execution against production, or DiscordSRV retirement. Runtime configuration and permissions must be checked before the first private-channel test post.

## Read-only recheck on 2026-10-21

Fresh SFTP comparison reconfirmed that test1 and SMP legacy DiscordSRV
global/staff channel maps and console destinations are **still identical**.
On-disk test1 forwarding in both directions and console-command execution
remain disabled, but test1's legacy console destination is still populated.
Test1 EnthusiaStaff config has an explicit backend ID and channel section,
but still no `discord-chat-bridge` SHADOW section. Runtime loaded state
and actual Discord JDA permissions were not inspected. The private
report-notification safety tests added in #657 are offline mocks only.
No active chat/console/role test, server restart, SFTP mutation, or live
Discord message was performed. **Staging gate remains BLOCKED.**
