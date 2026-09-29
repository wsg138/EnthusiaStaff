# Staff Bot staging deployment handoff

Use canonical EnthusiaStaff `main` source SHA `6a2db9f9cfb2516cff57b323f1956ccc98cff5c8` and the `staff-bot-staging` release.

Artifact:
- `EnthusiaStaff-StaffBot.jar`
- SHA-256 `57eb30dee5d302d0cc05827d4581e528c628c493fe3099c9110979ee676d3691`

## Runtime

Use an isolated Java 21 Bloom/Pterodactyl split/container.

Startup:

```text
java -Dterminal.jline=false -Dterminal.ansi=true -jar EnthusiaStaff-StaffBot.jar
```

Keep destructive Discord enforcement disabled during initial testing:

```text
ENTHUSIA_STAFF_BOT_DISCORD_ENFORCEMENT_ENABLED=false
```

## Staff Bot environment

Configure through the panel secret/environment facility, never on the command line:

```text
ENTHUSIA_STAFF_BOT_ENVIRONMENT=staging
ENTHUSIA_STAFF_BOT_TOKEN=<staging-discord-app-token>
ENTHUSIA_STAFF_BOT_DB_JDBC_URL=jdbc:mariadb://<private-db-host>:3306/<staff-db>
ENTHUSIA_STAFF_BOT_DB_USERNAME=<staff-db-user>
ENTHUSIA_STAFF_BOT_DB_PASSWORD=<staff-db-password>
ENTHUSIA_STAFF_BOT_AUTHORITY_URL=http://<private-paper-host>:8771/v1/staff-rank
ENTHUSIA_STAFF_BOT_AUTHORITY_TRANSPORT=bloom-private-split
ENTHUSIA_STAFF_DISCORD_AUTHORITY_SECRET=<shared-authority-secret-at-least-32-chars>
ENTHUSIA_STAFF_BOT_COMPONENT_SECRET=<different-secret-at-least-32-chars>
ENTHUSIA_STAFF_BOT_HEALTH_HOST=127.0.0.1
ENTHUSIA_STAFF_BOT_HEALTH_PORT=8765
```

Optional defaults may remain unchanged unless current container limits require tuning.

## Paper authority endpoint

On the Paper backend selected as staff-rank authority, create:

`plugins/EnthusiaStaff/discord-staff-authority.properties`

with:

```properties
authority.secret=<same-value-as-ENTHUSIA_STAFF_DISCORD_AUTHORITY_SECRET>
authority.bind=bloom-private-split
authority.port=8771
```

The secret must be at least 32 characters. Keep port 8771 private to the Bloom/internal network. Do not expose it publicly.

The Paper server must have LuckPerms available. Restart that Paper process after creating the authority file so the endpoint is bound during plugin startup.

## Validation

1. Confirm Staff MariaDB schema is already current through the normal Staff/Paper deployment path.
2. Confirm Paper logs show the Discord staff authority endpoint started rather than configuration/bind failure.
3. Start Staff Bot and require the process to remain up.
4. From inside the bot container check `http://127.0.0.1:8765/health` and `/ready`; `/ready` must return 200 after Discord identity/guild validation.
5. Run the same JAR once with `--smoke-test` using the same secret-bearing environment. The smoke test sends no Discord message and must exit zero.
6. Test non-destructive account-link/staff-rank reads only.
7. Leave `ENTHUSIA_STAFF_BOT_DISCORD_ENFORCEMENT_ENABLED=false` until destructive Discord moderation receives separate acceptance/authorization.

Velocity is not a direct Staff Bot startup dependency.

## Stop conditions

Stop and report rather than weakening security if:
- Discord token/app/guild identity validation fails;
- MariaDB is unavailable;
- Paper authority cannot be reached on the private route;
- authority signatures fail;
- `/ready` never reaches 200;
- starting the bot would require exposing private authority/health ports publicly.

Never commit or print Discord tokens, MariaDB passwords, authority secrets, component secrets, private evidence, reporter data, PM content, coordinates, or staff notes.
