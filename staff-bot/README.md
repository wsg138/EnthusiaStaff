# EnthusiaStaff Staff Bot

This module is the standalone Java 25 Discord runtime for EnthusiaStaff. It runs beside the Paper/Velocity platform; it is not a Minecraft plugin.

This runbook describes the safe launch path for the runtime now present on canonical `main` after Staff repair PR #249 merged as `de6d6767d3da3caa8d64c4a30db30bf82c21631d`. It does not authorize production punishment enforcement or merge any parked Discord feature package.

## Build and artifact

Build the exact source you intend to deploy:

```bash
./gradlew :staff-bot:clean :staff-bot:check :staff-bot:shadowJar --no-daemon --console=plain
```

The executable is:

```text
staff-bot/build/libs/EnthusiaStaff-StaffBot-<version>.jar
```

Main class:

```text
net.enthusia.staff.discordbot.StaffBotApplication
```

When using a GitHub-produced artifact, record both the source SHA and JAR SHA-256 before replacing a running copy.

The launch candidate must be rebuilt and exact-head validated from post-#249 canonical `main`; do not deploy the older pre-#249 staging artifact as the final production candidate.

## Safe initial launch mode

Use Java 25. Configuration comes from environment variables. Do not place tokens, database passwords, HMAC secrets, or component secrets on the Java command line.

Normal startup:

```bash
java -jar EnthusiaStaff-StaffBot.jar
```

Recommended Bloom/Pterodactyl startup:

```text
java -Dterminal.jline=false -Dterminal.ansi=true -jar EnthusiaStaff-StaffBot.jar
```

For initial deployment keep:

```text
ENTHUSIA_STAFF_BOT_DISCORD_ENFORCEMENT_ENABLED=false
```

This keeps destructive Discord punishment mutations disabled while the base bot, identity, database reads, account linking, authority checks, and health/readiness behavior are validated.

A non-destructive Discord connectivity check can use:

```bash
java -jar EnthusiaStaff-StaffBot.jar --smoke-test
```

The smoke test connects to Discord, validates the configured application/guild identity fence, sends no test message, and exits nonzero if readiness is not achieved.

## Required configuration

Always required:

- `ENTHUSIA_STAFF_BOT_ENVIRONMENT`: `staging` or `production`.
- `ENTHUSIA_STAFF_BOT_TOKEN`: token for the selected fixed Discord application.

To enable the existing account-link/moderation read integration, configure this complete group together:

- `ENTHUSIA_STAFF_BOT_DB_JDBC_URL`
- `ENTHUSIA_STAFF_BOT_DB_USERNAME`
- `ENTHUSIA_STAFF_BOT_DB_PASSWORD`
- `ENTHUSIA_STAFF_BOT_AUTHORITY_URL`
- `ENTHUSIA_STAFF_DISCORD_AUTHORITY_SECRET`
- `ENTHUSIA_STAFF_BOT_COMPONENT_SECRET`

`ENTHUSIA_STAFF_BOT_AUTHORITY_URL` must target `/v1/staff-rank`.

For separate Bloom/Pterodactyl splits use:

```text
ENTHUSIA_STAFF_BOT_AUTHORITY_TRANSPORT=bloom-private-split
```

Authority and component secrets must each contain at least 32 characters and should be distinct.

Optional safe tuning:

- `ENTHUSIA_STAFF_BOT_HEALTH_HOST` (default `127.0.0.1`; loopback only)
- `ENTHUSIA_STAFF_BOT_HEALTH_PORT` (default `8765`; production may not use port `0`)
- `ENTHUSIA_STAFF_BOT_WORKER_THREADS` (default `4`, range `1..16`)
- `ENTHUSIA_STAFF_BOT_WORKER_QUEUE_CAPACITY` (default `256`, range `1..4096`)
- `ENTHUSIA_STAFF_BOT_INTERACTION_CAPACITY` (default `4096`, range `16..65536`)
- `ENTHUSIA_STAFF_BOT_INTERACTION_TTL_SECONDS` (default `900`, maximum 24 hours)
- `ENTHUSIA_STAFF_BOT_DB_POOL_SIZE` (default `4`, range `2..16`)
- `ENTHUSIA_STAFF_BOT_DB_TIMEOUT_MILLIS` (default `3000`, range `250..60000`)

See `runtime.env.example` for a placeholder-only inventory.

## MariaDB and Paper authority

Staff Bot connects to the same logical EnthusiaStaff MariaDB used by the Staff platform. The bot does not own schema migration; run Flyway/schema updates through the authorized Staff/Paper deployment path first.

Paper remains the staff-rank authority. Staff Bot authority requests are HMAC authenticated, short-lived, replay protected, and response authenticated. Discord roles are not authoritative Minecraft staff ranks.

For split deployment, keep the Paper authority listener on private networking. Do not expose the authority port publicly merely to make the bot connect.

Velocity is not a direct startup dependency of the standalone bot.

## Health and dependency behavior

The runtime exposes loopback-only:

```text
GET /health
GET /ready
```

Expected behavior:

- `/health` is process liveness; terminal runtime failure returns `503` while the listener remains available.
- `/ready` returns `200` only after the Discord identity/guild fence passes.
- Discord disconnect removes readiness until JDA reconnects and identity is revalidated.
- Invalid Discord token/application/guild fails closed.
- MariaDB unavailable during moderation-runtime startup causes startup failure rather than partial healthy operation.
- MariaDB or Paper-authority failure during a request fails closed; do not fall back to Discord roles or cached privilege guesses.
- Paper authority unavailable means staff-sensitive reads/actions remain unavailable until authority returns.

DB initialization currently happens before the health listener starts, so a DB startup failure is observed through process exit/console rather than `/health`. Bloom/Pterodactyl should therefore use process restart policy plus readiness checks after successful startup.

## Startup order

For a normal split deployment:

1. Ensure the shared EnthusiaStaff MariaDB schema is current.
2. Start the Paper Staff plugin and verify the private signed staff-rank authority endpoint is available.
3. Start Staff Bot with the exact reviewed JAR and runtime secrets.
4. Verify the process remains up.
5. Check `http://127.0.0.1:8765/health` and `/ready` from inside the container/split.
6. Run the non-destructive `--smoke-test` against the candidate configuration in staging.
7. Keep destructive Discord enforcement disabled until separately accepted and authorized.

The Discord punishment runtime has durable database-backed work/recovery, but it is opt-in. Do not enable it merely as a launch test.

## Bloom / Pterodactyl requirements

Use an isolated Java 25 split/container.

Keep these private/loopback unless an explicitly documented private tunnel is in use:

- health: `8765`
- moderation preview: `8766`
- Paper authority: `8771`

None requires a public Minecraft allocation.

Store runtime values through the panel's environment/secret facility. Never commit or paste real tokens, MariaDB credentials, HMAC secrets, private evidence, reporter data, PM content, coordinates, or staff notes.

## Update and rollback

1. Record the currently running source SHA and JAR checksum.
2. Obtain/build the replacement JAR and verify source metadata/checksum.
3. Run repository tests and the non-destructive staging smoke test.
4. Stop Staff Bot cleanly; do not restart Paper/Velocity solely because the bot JAR changes.
5. Replace only the Staff Bot JAR; preserve runtime secrets/config unless a reviewed change requires otherwise.
6. Start Staff Bot and require a healthy process plus `/ready=200`.
7. If startup/readiness fails, stop the candidate, restore the previous known-good JAR, and investigate from sanitized logs. Do not weaken identity, authority, replay, or network fences to make an update start.

Normal shutdown gives JDA a graceful window, closes moderation/database resources, and stops bounded workers.

## Production cutover checklist

Before turning on the production application:

- build the exact Staff Bot launch artifact from canonical post-#249 `main` and exact-head validate it;
- record artifact checksum and source provenance;
- select Java 25;
- supply the production Discord token only through the runtime secret store;
- verify shared MariaDB connectivity/schema;
- verify the private Paper authority route;
- verify `/health` and `/ready` behavior;
- verify the bot is installed only in the expected Enthusia guild;
- keep `ENTHUSIA_STAFF_BOT_DISCORD_ENFORCEMENT_ENABLED=false` unless destructive Discord moderation has separately passed acceptance and been explicitly authorized;
- keep secrets and private moderation data out of GitHub, console evidence, and handoffs.

Parked feature packages such as cross-platform enforcement, investigations, or role-sync replacement are not prerequisites for starting the merged base Staff Bot and must not be merged merely to satisfy this checklist.
