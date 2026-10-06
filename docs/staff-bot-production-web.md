# Private production moderation workspace

The production Discord Staff Bot and private moderation read API run in the same Bloom Staff Bot split. A separate Cloudflare Worker serves the HTTPS workspace at `https://staff.enthusia.info`. The bot issues two-minute, one-use links from `/moderate`, **Moderate User**, and **Moderate Message**. The Worker creates a short-lived staff session and signs exact read requests to `https://moderation-read.enthusia.info`, which reaches the bot's loopback listener through a dedicated remotely managed Cloudflare Tunnel. Every read checks current HUB staff authority and Discord guild membership.

The site remains private behind signed staff launches. Visiting it directly yields HTTP 401. Production actions use the bot's explicitly enabled Discord enforcement service. For the production moderation cutover, the private `m` configuration must contain the complete enforcement policy and `discord-enforcement.enabled=true`. The production page rejects submissions when this service is disabled; it never reports a simulated submission as a live success. Staging remains simulation-only.

## Discord action boundary

The protected Worker verifies session and CSRF before signing an exact `capabilities`, `prepare`, `confirm`, or `status` request. It supplies actor and guild from its server session and binds drafts to a hash of that session's CSRF token. Bloom checks current HUB staff authority for every request. Preparation and confirmation also use the existing D07 authorization, target protection, bot permission and hierarchy checks. Restriction targets must be visible to the actor. Confirmation cannot change the prepared intent.

The confirmation UUID becomes the durable punishment ID for website actions. Repeating the same confirmation reads the existing record instead of scheduling a second punishment. Actor, guild and target ownership are checked before returning a record. Discord effects remain exclusively in the durable worker. The page displays actual processing state, external-effect confirmation and notification outcome. A failed or uncertain response must be checked by its punishment ID before creating a replacement.

Supported Discord effects are warning, mute, kick, ban and one channel/category restriction per submission. Target notifications are included as formatted player DMs with the public reason, player-safe staff explanation, Discord expiry timestamps where applicable, and both appeal routes. Automatically appended staff-only evidence-reference lines are not copied into player DMs. Discord message contents and attachments are not archived by this path. Message deletion remains unavailable.

The production workspace now has explicit Discord, Minecraft and Both scope routing when the corresponding capabilities are active. Minecraft uses the authoritative Paper punishment workflow. Both uses one atomic persistence transaction for the Minecraft case and Discord enforcement intent; it never means “run two unrelated punish commands.” The UI reports the two platforms separately and keeps pending/retry/failure state visible. The confirmation request cannot change either prepared intent. A missing or ambiguous Minecraft/Discord link fails closed. Discord punishment UUIDs remain durable external-effect identities; a Both action additionally references the authoritative Minecraft case ID. The workspace history merges Discord and Minecraft records without pretending Discord records contribute to Minecraft ladder counts.

Enabling enforcement requires a dedicated manageable mute role, protected support scopes and message, and explicit Helper mute / Mod mute / Mod ban / Mod restriction duration ceilings. Do not choose a similarly named player guild role as the mute role. Linked Minecraft bans committed after the StaffBot notification cutover are also delivered to the Discord account that was verified as linked when the punishment was issued; pre-cutover and unlinked cases are not retroactively messaged.

The bot role needs Manage Roles (including channel permission overrides), Kick Members, and Ban Members, plus its existing channel visibility, message sending and history access. Keep it above the dedicated mute role and eligible targets. Administrator and Manage Messages are unnecessary for this path. Enable Message Content access on the application to read normal message text. A privileged intent toggle does not grant guild moderation permissions.

## Minecraft-origin cross-platform gate

Paper-origin Discord/Both punishment scope is disabled unless the Paper runtime has `ENTHUSIA_STAFF_CROSS_PLATFORM_ENABLED=true`. Enabling it also requires `ENTHUSIA_STAFF_CROSS_PLATFORM_DISCORD_GUILD_ID` and the same four canonical Discord ceiling variables used by StaffBot: `ENTHUSIA_STAFF_BOT_DISCORD_HELPER_MAX_MUTE_SECONDS`, `ENTHUSIA_STAFF_BOT_DISCORD_MOD_MAX_MUTE_SECONDS`, `ENTHUSIA_STAFF_BOT_DISCORD_MOD_MAX_BAN_SECONDS`, and `ENTHUSIA_STAFF_BOT_DISCORD_MOD_MAX_RESTRICTION_SECONDS`. Older deployments using the pre-documentation names without `_DISCORD_` remain accepted for compatibility, but new configuration should use the canonical names.

This gate authorizes repository routing only; it is not live-acceptance evidence. Keep it disabled until the D08 staged failure/restart/replay matrix passes on the exact candidate SHA.

## Deployment inputs

- Deploy `moderation-web/wrangler.production.jsonc` to the account owning `enthusia.info`, using the reviewed `moderation-web/deploy-production.ps1` script. Run it locally with `-TokenFile` pointing to the existing production bot token file. The script builds production assets, uploads only the two derived keys to the Worker, and deletes its temporary secrets file. It does not print the token or derived keys.
- Set the Worker's `LAUNCH_SIGNING_KEY_HEX` and `READ_API_SIGNING_KEY_HEX` secrets to SHA-256 digests of the production Discord bot token prefixed by the existing distinct launch and read domain separators. Never upload the raw bot token to Cloudflare. This token-derived signing is bootstrap debt; migrate both sides to dedicated random keys in one coordinated cutover.
- Create a dedicated remotely managed tunnel for `moderation-read.enthusia.info` to `http://127.0.0.1:8766` with a 404 fallback. Keep its connector token in `prod-tunnel` on the Bloom split. The production connector must not reuse the staging tunnel token.
- Keep `cloudflared`, `tp`, `m`, and `prod-tunnel` private on the split. The tunnel token file is passed to `cloudflared` with `--token-file`, not a command-line secret.
- `moderation-web/deploy-production-bloom.py` reads the existing private SFTP details and verifies the pinned Bloom host key. It installs the dedicated tunnel connector (`python moderation-web/deploy-production-bloom.py`), checks the live JAR and enforcement setting (`--audit`), or backs up and uploads the built Staff Bot JAR (`--upload-jar`). It never prints credential values.

## Bloom APP FLAGS

First run the smoke test:

```text
--environment=production --token-file=tp --moderation-config-file=m --tunnel-binary-file=cloudflared --tunnel-token-file=prod-tunnel --moderation-web-url=https://staff.enthusia.info --smoke-test
```

After `staff_bot_smoke_ready environment=production` and a clean exit, remove only `--smoke-test` and start normally. Verify `staff_bot_ready environment=production`, the nine base production commands including both `/moderate` and `/punish`, the enforcement quick commands when enforcement is enabled, the Worker's production health, direct HTTP 401 at `/moderation`, one successful signed launch, read data from the Bloom API, replay rejection, and action capabilities reporting live Discord enforcement. Test with a nonstaff account to confirm that commands, site reads, and action preparation are denied.

## Rollback

Remove `--moderation-web-url`, `--tunnel-binary-file`, and `--tunnel-token-file` from APP FLAGS and restart the prior production JAR. The existing read-only Discord menus return; the new Worker remains inaccessible without valid signed launches. Keep the pre-deployment JAR backup until the web launch has passed staff testing.
