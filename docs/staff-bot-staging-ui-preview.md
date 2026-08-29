# Staff bot staging moderation UI preview

This runbook is for the owner-directed Discord moderation UI prototype. It is not ES-D07 punishment enforcement.

## Safety model

The preview remains staging-only. It can be enabled in either of these explicit ways:

- environment configuration: `ENTHUSIA_STAFF_BOT_ENVIRONMENT=staging` together with `ENTHUSIA_STAFF_BOT_UI_PREVIEW=true`; or
- the dedicated-host convenience CLI: `--staging-ui-preview` together with `--token-file=<path>`.

The convenience CLI always selects the existing `STAGING` environment and rejects an explicitly configured production environment. The normal production configuration cannot enable preview mode. The existing fixed staging Discord application, guild, and test-channel identity fence still applies before interactions are enabled.

When preview mode is enabled, the process does not initialize the D06 moderation database/authority runtime. The preview workflow uses deterministic in-memory sample data and has no punishment service, Discord moderation adapter, Minecraft/Paper authority adapter, or persistence adapter. Final confirmation only changes the in-memory preview state.

## Bloom Generic-JDA panel

Place a runtime-only file named `staging-bot-token.txt` in the bot server filesystem. The file must contain only the Discord **staging bot token**; a normal trailing newline is acceptable. Do not commit this file, paste its contents into APP FLAGS, or include it in logs/screenshots/support messages.

Use these exact panel settings:

```text
Java Version:
Java 21

JAR FILE:
EnthusiaStaff-StaffBot.jar

FLAGS:
-Dterminal.jline=false -Dterminal.ansi=true

APP FLAGS:
--staging-ui-preview --token-file=staging-bot-token.txt
```

With these APP FLAGS, no machine-level environment variables are required for the UI preview. `--token-file` is accepted only as part of the explicit staging-preview CLI path; it is not a production token-file interface.

## Existing environment-variable configuration

The original environment-variable deployment path remains supported unchanged:

```bash
export ENTHUSIA_STAFF_BOT_TOKEN='<staging bot token>'
export ENTHUSIA_STAFF_BOT_ENVIRONMENT='staging'
export ENTHUSIA_STAFF_BOT_UI_PREVIEW='true'
java -jar EnthusiaStaff-StaffBot.jar
```

The existing `--smoke-test` argument is also preserved and may be used with the environment path or combined with the staging-preview CLI when non-destructive readiness validation is needed.

The runtime keeps the existing optional loopback health endpoint defaults. No SQL database, Paper authority endpoint, LiteBans connection, production data, or production Discord token is needed merely to use preview mode.

## Fixed staging download

After a safely merged `main` build, the existing `staff-bot-staging` prerelease publishes the runtime under the fixed asset name:

```text
https://github.com/wsg138/EnthusiaStaff/releases/download/staff-bot-staging/EnthusiaStaff-StaffBot.jar
```

The same release also publishes `EnthusiaStaff-StaffBot.jar.sha256` and `staff-bot-staging-source.txt`. Verify the source record and checksum before replacing a dedicated-host runtime.

## Using the preview

Run `/moderate-preview` in the staging guild using an account permitted to use the staging application command. The interaction is ephemeral and uses sample identities/moderation history.

The preview includes:

- moderation overview plus Accounts, History, Notes, and Cases sample views;
- Warn, Mute, Kick, Ban, and Restrict actions;
- Discord, Minecraft, and Both visual scopes;
- preset and custom reasons;
- preset and custom durations;
- DM-user and message-delete option presentation;
- final confirmation and intended higher-approval messaging;
- insufficient-authority, protected-target, approval-required, stale-confirmation, Discord-failure, and partial-result example states.

Selecting the final confirmation produces `Preview complete — no moderation action was applied.` No real punishment path exists behind that control.

## Disabling preview

On the Bloom panel, stop the process and remove `--staging-ui-preview --token-file=staging-bot-token.txt` from APP FLAGS before starting any normal runtime. For the environment-variable path, stop the process and remove or set `ENTHUSIA_STAFF_BOT_UI_PREVIEW=false` before starting the normal staging runtime. Production must never be configured with either preview activation path.
