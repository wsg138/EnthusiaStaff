# Codex execution — Staff Bot staging

Repository: `wsg138/EnthusiaStaff`
Deployment branch: `deploy/temp-network-sftp-staging`
Runtime source: canonical `main` at `6a2db9f9cfb2516cff57b323f1956ccc98cff5c8`
Release tag: `staff-bot-staging`
Artifact: `EnthusiaStaff-StaffBot.jar`
Expected SHA-256: `57eb30dee5d302d0cc05827d4581e528c628c493fe3099c9110979ee676d3691`

## Goal

Bring up the merged base EnthusiaStaff Discord bot in non-destructive staging mode. Do not merge parked Discord feature PRs and do not enable destructive Discord punishment enforcement.

## Fixed staging Discord identity

The token used for staging MUST belong to this exact application and guild setup:

- application ID: `1541279616881397772`
- allowed guild ID: `1410303324745371709`
- required staging test channel ID: `1541286004298752091`

The runtime intentionally fails readiness if the application/guild/channel identity fence does not match. Do not substitute the production bot token or another Discord application merely to make startup succeed.

## Inputs Codex may use

- existing authorized Bloom/Pterodactyl access;
- existing Staff MariaDB credentials already used by EnthusiaStaff Paper;
- the owner-provisioned token for staging application `1541279616881397772`, entered directly in Bloom secrets/environment;
- a newly generated authority secret and a different newly generated component secret.

Never print, commit, screenshot, or return any secret value.

## Paper authority preparation

Use HUB as the preferred authority backend unless live topology proves another always-on Paper backend is safer.

Create `plugins/EnthusiaStaff/discord-staff-authority.properties` from `deploy/temp-network/discord-staff-authority.properties.example` with a new random secret of at least 32 characters. The same value must populate `ENTHUSIA_STAFF_DISCORD_AUTHORITY_SECRET` on the bot split.

Keep `authority.bind=bloom-private-split` and `authority.port=8771`. Port 8771 must remain private/internal.

Do not restart HUB merely to finish this handoff. If a restart is required to bind the authority endpoint, report `PAPER_RESTART_REQUIRED` and wait for owner authorization unless a maintenance restart is already authorized.

## Bot split preparation

Use an isolated Java 21 Bloom/Pterodactyl split. Upload the exact release JAR and verify SHA-256 before start.

Populate the panel environment from `deploy/temp-network/staff-bot.runtime.env.example`. Real secret values belong only in Bloom's secret/environment facility.

Initial startup command:

```text
java -Dterminal.jline=false -Dterminal.ansi=true -jar EnthusiaStaff-StaffBot.jar
```

Required safety value:

```text
ENTHUSIA_STAFF_BOT_DISCORD_ENFORCEMENT_ENABLED=false
```

Do not change it during this task.

## Validation order

1. Verify the Staff DB schema is already current through the Paper deployment; do not run ad-hoc schema SQL from the bot.
2. Verify the selected Paper authority endpoint is listening privately and its log has no authority configuration/bind failure.
3. Start Staff Bot and require the process to remain up.
4. From inside the bot container require `GET http://127.0.0.1:8765/health` success.
5. Require `GET http://127.0.0.1:8765/ready` to return 200 after exact application/guild/channel validation.
6. Stop the normal bot process cleanly, run the exact same JAR once with `--smoke-test` using the same environment, require exit code 0, then restart the normal bot process.
7. Validate only non-destructive reads/account-link/staff-rank behavior.
8. Confirm no destructive Discord punishment mutation ran and enforcement remains false.

## Stop conditions

Stop and report rather than weakening security if any of these occur:

- Discord token/application/guild/test-channel identity mismatch;
- Staff MariaDB unavailable;
- authority endpoint unreachable on the private route;
- HMAC/signature validation failure;
- `/ready` never reaches 200;
- setup would require exposing 8765, 8766, or 8771 publicly;
- requested behavior depends on parked D08/D09/D13 work rather than the merged base bot.

## Report

Return only:

```text
artifact_sha_ok=<yes/no>
paper_authority=<ready/restart-required/failed>
bot_process=<running/failed>
health=<status>
ready=<status>
smoke_test=<pass/fail/not-run>
destructive_enforcement=false
blocker=<none-or-sanitized-reason>
```
