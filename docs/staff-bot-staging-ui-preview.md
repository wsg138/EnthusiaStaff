# Staff bot staging moderation UI preview

This runbook is for the owner-directed Discord moderation UI prototype. It is not ES-D07 punishment enforcement.

## Safety model

The preview is intentionally available only when both runtime conditions are true:

- `ENTHUSIA_STAFF_BOT_ENVIRONMENT=staging`
- `ENTHUSIA_STAFF_BOT_UI_PREVIEW=true`

Production rejects the preview flag. Preview mode uses the existing fixed staging Discord application and its existing staging guild/test-channel identity fence.

When preview mode is enabled, the process does not initialize the D06 moderation database/authority runtime. The preview workflow uses deterministic in-memory sample data and has no punishment service, Discord moderation adapter, Minecraft/Paper authority adapter, or persistence adapter. Final confirmation only changes the in-memory preview state.

## Dedicated-host configuration

Only these three environment values are required for the preview runtime:

```bash
export ENTHUSIA_STAFF_BOT_TOKEN='<staging bot token>'
export ENTHUSIA_STAFF_BOT_ENVIRONMENT='staging'
export ENTHUSIA_STAFF_BOT_UI_PREVIEW='true'
java -jar EnthusiaStaff-StaffBot.jar
```

Do not place the token in source control, logs, screenshots, shell history intended for sharing, or support messages.

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

Stop the process and remove or set `ENTHUSIA_STAFF_BOT_UI_PREVIEW=false` before starting the normal staging runtime. The production application must never be configured with the preview flag.
