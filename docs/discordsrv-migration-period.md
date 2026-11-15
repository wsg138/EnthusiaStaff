# DiscordSRV migration-period deployment

This runbook starts a reversible production migration from DiscordSRV to EnthusiaStaff without deleting DiscordSRV prematurely.

It intentionally contains no live credentials, Discord tokens, database passwords, SFTP settings, host keys, or production role IDs.

## What is safe to move first

Move ownership in this order:

1. **Account linking** — EnthusiaStaff becomes the player-facing `/link` owner while its existing transition adapter mirrors authoritative link changes back to DiscordSRV.
2. **StaffBot runtime** — deploy the current EnthusiaStaff StaffBot so current moderation, notification, role-sync shadowing, and authenticated console-bridge code are available.
3. **Legacy managed-role parity** — run the current LuckPerms -> Discord role synchronizer in `shadow` and investigate every unexplained drift before any legacy role writer is disabled.
4. **Provider-neutral managed roles** — migrate LumaGuilds and EnthusiaPlaytime to the shared `discord-platform-api` provider once the provider runtime is available.
5. **Minecraft <-> Discord chat/rendering** — replace/refactor the DiscordSRV transport used by InteractiveChatDiscordSrvAddon while preserving RoseChat moderation and InteractiveChat rendering.
6. **Retire remaining optional hooks** — verify RoseChat, EnthusiaAdvancements, OreAnnouncer, the Staff authority transition bridge, and other soft integrations boot and behave correctly without DiscordSRV.
7. **Remove DiscordSRV last** — only after no required runtime edge remains.

Do not physically remove DiscordSRV while InteractiveChatDiscordSrvAddon still has a hard dependency on it.

## Existing `/link` transition behavior

EnthusiaStaff already owns the canonical Discord <-> Minecraft link data.

During the migration period, when DiscordSRV is present and its public AccountLinkManager is available, EnthusiaStaff mirrors its canonical current-main link state back into DiscordSRV. This lets old consumers continue reading DiscordSRV while new player-facing link/unlink operations originate in EnthusiaStaff.

The live network also has a Skript `/link` alias that historically forwards directly to `discordsrv link`. Change that alias to `enthusiastaff:link` on each backend when beginning the migration period. Leave `/discordsrv ...` itself available temporarily for controlled legacy diagnosis.

Acceptance:
- create a new link through EnthusiaStaff and verify the legacy DiscordSRV view converges;
- unlink through EnthusiaStaff and verify the legacy mirror clears;
- relink and restart the backend;
- verify LumaGuilds/PlayTime legacy consumers continue to see the mirrored account during the transition;
- do not overwrite a conflicting authoritative EnthusiaStaff link merely to match DiscordSRV.

## StaffBot deployment

The standalone StaffBot is the Java/JDA runtime built from this repository's `staff-bot` module. An older deployment script for another support-bot repository does not deploy this runtime.

Build a release candidate from the exact reviewed EnthusiaStaff commit:

```text
./gradlew clean :staff-bot:check :staff-bot:shadowJar
```

Deploy the resulting `EnthusiaStaff-StaffBot-*.jar` to the existing Bloom StaffBot server using the established production file/startup procedure. Preserve the existing token, database, authority, component-signing, Cloudflare tunnel, and enforcement configuration rather than recreating secrets.

Run the documented StaffBot smoke test before the normal production start.

## Staff role-sync migration

Configure the currently approved Minecraft-group -> Discord-role mapping through:

```text
ENTHUSIA_STAFF_BOT_ROLE_SYNC_MAPPINGS=<group>=<role-id>;<group>=<role-id>
ENTHUSIA_STAFF_BOT_ROLE_SYNC_PROTECTED_ROLE_IDS=<role-ids-never-managed>
ENTHUSIA_STAFF_BOT_ROLE_SYNC_MODE=shadow
ENTHUSIA_STAFF_BOT_ROLE_SYNC_INTERVAL_SECONDS=300
ENTHUSIA_STAFF_BOT_ROLE_SYNC_BATCH_SIZE=25
ENTHUSIA_STAFF_BOT_ROLE_SYNC_DB_USERNAME=<restricted-writer>
ENTHUSIA_STAFF_BOT_ROLE_SYNC_DB_PASSWORD=<secret>
```

Production starts in `shadow`. Shadow mode must not add/remove Discord roles. It records desired/observed managed-role state so differences can be investigated while DiscordSRV remains the writer.

Do not obtain or commit the mapping by copying secret-bearing DiscordSRV configuration into the repository. Supply the approved role IDs through the runtime configuration.

## Discord console replacement

The authenticated command bridge replaces the useful part of DiscordSRV's remote console without exposing unrestricted RCON-like authority.

StaffBot sends a signed request to a Paper-local endpoint. Paper then verifies:
- the HMAC-bound request, timestamp and nonce;
- target-server and command allowlists;
- the canonical Discord <-> Minecraft link;
- the actor's current Minecraft rank and required permission immediately before execution;
- durable request-ID/idempotency state.

Only configured commands can execute. Ambiguous execution failures are not blindly retried, and returned output is bounded/redacted.

### Same-Bloom-host setup

When StaffBot and Paper processes run on the same Bloom machine, keep the bridge on loopback HTTP. HMAC provides request/response integrity and authentication, while loopback avoids exposing the plaintext transport to the network. HTTPS is unnecessary complexity unless the endpoint later crosses hosts.

Each Paper process requires a unique local port.

Example StaffBot configuration:

```text
ENTHUSIA_STAFF_BOT_COMMAND_BRIDGE_ENDPOINTS=<smp-id>=http://127.0.0.1:8772/v1/discord-command;<hub-id>=http://127.0.0.1:8773/v1/discord-command
ENTHUSIA_STAFF_BOT_COMMAND_BRIDGE_SECRET=<same-32+-character-secret-used-by-Paper>
ENTHUSIA_STAFF_BOT_COMMAND_BRIDGE_TIMEOUT_MILLIS=3000
```

Example Paper process settings for SMP:

```text
ENTHUSIA_STAFF_COMMAND_BRIDGE_ENABLED=true
ENTHUSIA_STAFF_COMMAND_BRIDGE_BIND_HOST=127.0.0.1
ENTHUSIA_STAFF_COMMAND_BRIDGE_PORT=8772
ENTHUSIA_STAFF_COMMAND_BRIDGE_SECRET=<shared-secret>
ENTHUSIA_STAFF_COMMAND_BRIDGE_RULES=<command>|<minimum-rank>|<required-permission>|<max-args>
```

Use a different port for HUB and every additional Paper process. Keep the command list narrow; do not approximate unrestricted DiscordSRV console by allowlisting every command.

Acceptance:
- linked/on-duty authorized staff can run an explicitly allowlisted harmless command;
- off-duty, unlinked, insufficient-rank, disallowed-command and wrong-server requests are rejected;
- duplicate request IDs do not re-execute;
- stopping one Paper endpoint does not affect ordinary Minecraft server operation.

## Moderation website

The production moderation website is the Cloudflare Worker/custom-domain deployment at `https://staff.enthusia.info`.

After the StaffBot candidate is deployed, build and deploy the matching current `moderation-web` source using the repository's production PowerShell deployer and the already-established local production token file:

```text
moderation-web/deploy-production.ps1 -TokenFile <existing-production-bot-token-file>
```

Do not replace production secrets just to redeploy. The deployer builds the Worker and derives the matching signing material expected by the StaffBot/private read API.

The private moderation read endpoint remains behind the configured Cloudflare Tunnel; do not expose the Bloom loopback read service directly.

## Player notification acceptance

After the StaffBot notification-preview change is deployed, use:

```text
/notification-test type:warning
/notification-test type:mute
/notification-test type:ban
```

The command sends the invoking linked staff member the production-format DM and creates no punishment/case or Discord enforcement mutation.

Real Discord punishments already notify for warning, mute, kick, ban and channel restriction. The separate linked-Minecraft notification worker currently covers Minecraft bans; expansion to other Minecraft consequences is a separate reconciliation task.

## Known remaining physical-removal blockers

DiscordSRV must stay installed until these are resolved or explicitly retired:

- LumaGuilds account-link/guild-role integration;
- EnthusiaPlaytime numeral-role integration;
- InteractiveChatDiscordSrvAddon hard dependency and its Minecraft <-> Discord rendering/transport path;
- InteractiveChat/RoseChat soft DiscordSRV hooks that are still intentionally used;
- EnthusiaAdvancements Discord rendering through the InteractiveChat addon;
- any desired OreAnnouncer DiscordSRV behavior;
- EnthusiaStaff/authority-bridge migration adapters, which can be removed only after the transition period.

The provider-neutral `discord-platform-api` contract is present, but the shared managed-role provider runtime still must be completed before LumaGuilds and PlayTime can be moved off their legacy DiscordSRV adapters.

## Final removal gate

Before deleting the DiscordSRV JAR:
- canonical EnthusiaStaff linking has been the live player-facing path through a restart window;
- legacy link mirroring has shown no unresolved ownership conflicts;
- role-sync parity is clean and all required role consumers use the Enthusia provider;
- Discord console use has moved to the authenticated command bridge;
- the InteractiveChat/RoseChat Discord chat path no longer requires DiscordSRV;
- every known soft consumer has been tested with DiscordSRV absent;
- DiscordSRV-specific Skript aliases/listeners are removed;
- a full HUB/SMP restart proves no required plugin disables itself or loses expected Discord functionality.

Keep the rollback simple during migration: re-enable the old alias/writer while DiscordSRV is still installed rather than reinstalling it after premature removal.
