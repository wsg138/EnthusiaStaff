# Staff bot staging moderation web preview

This runbook covers the owner-directed D16 moderation preview. Discord and the web workspace remain staging-only and simulation-only. A separately authorized temporary live-Paper acceptance path may use the narrow `EnthusiaStaffAuthorityBridge` described below; that exception does not authorize destructive moderation, message deletion, LiteBans mutation, cutover, or deployment of the full EnthusiaStaff Paper moderation runtime solely for D16 acceptance.

## Accepted architecture

- The staging Discord bot runs on a Bloom split as an isolated Java 21 process.
- `/moderate-preview` returns an ephemeral `Open Moderation Panel` link.
- `https://staff-staging.enthusia.info` is the permanent staging web workspace.
- Cloudflare Workers Static Assets serve the approved UI through the Worker; direct unauthenticated workspace access is rejected.
- A SQLite-backed Durable Object owns one-time launch replay state and browser session/CSRF state.
- D16 attaches the hosted workspace to the existing D03/D06 read-only authority/data runtime through authenticated private bridges.
- Destructive punishment, message-deletion, and permission-override operations remain simulation-only.
- For the separately owner-authorized live-Paper acceptance path, Paper runs only `EnthusiaStaff-AuthorityBridge.jar`. That plugin exposes current LuckPerms-backed staff rank and contains no commands, declared permissions, Bukkit event listeners, database access, punishment adapters, or other player mutation capability.

The static UI source remains under `staff-bot/src/main/resources/moderation-preview` so the product contract tests and Cloudflare build share exactly one owner-approved asset set. `moderation-web/scripts/build.mjs` copies those assets into the Cloudflare deployment bundle.

## Safety model

Preview mode can be enabled only for the fixed staging Discord/web environment. Without complete D06/D16 runtime configuration the preview remains unable to serve real moderation reads. A complete `--moderation-config-file` initializes only the read-only D06 data/authority runtime and the D16 loopback read API. It does not initialize punishment, deletion, permission-override, or moderation-database mutation adapters.

The D16 read API always binds to `127.0.0.1:8766`. The staging bot may supervise a panel-uploaded `cloudflared` process so the existing named tunnel can reach that loopback listener without a public Bloom allocation. The browser never receives MariaDB credentials, Discord bot credentials, component-signing secrets, authority credentials, or Cloudflare tunnel credentials.

Paper remains the current LuckPerms-backed staff authority. The live acceptance bridge listens only for the D16 staff-rank resource, accepts only private/loopback peers, requires short-lived HMAC-signed replay-resistant requests, and signs authenticated responses. The staging bot resolves and pins the configured Paper hostname only when it resolves exclusively to private addresses. Discord roles never become an authority source.

## Runtime secret boundary

The Discord bot token, MariaDB credential, authority credential, component-signing credential, and Cloudflare tunnel token are runtime-only files/values. They must never appear in browser code, public source, URLs, logs, artifacts, issue comments, or chat.

The Cloudflare account API token used by GitHub Actions is not copied to Bloom. Bloom receives only the connector token for the one remotely managed staging tunnel, stored in a file and passed to `cloudflared` using `--token-file`.

## Bloom staff-bot split

Use Java 21 and the exact validated D16 staff-bot JAR. Bloom does not need panel environment variables for this path.

The staff-bot split root contains:

- `EnthusiaStaff-StaffBot.jar` — exact validated D16 artifact;
- `staging-bot-token.txt` — staging Discord bot token only;
- `staff-bot-runtime.properties` — D06/D16 database/authority/component configuration;
- `cloudflared` — current Linux binary, version 2025.4.0 or later because `--token-file` is required;
- `cloudflared-token.txt` — token for the existing `enthusia-moderation-read-staging` tunnel only.

The Java runtime starts `cloudflared` without a shell, monitors it, fails the bot closed on unexpected connector exit, and terminates it during normal shutdown. The tunnel token contents never become a process argument.

```text
JAR FILE:
EnthusiaStaff-StaffBot.jar

FLAGS:
-Dterminal.jline=false -Dterminal.ansi=true

APP FLAGS:
--staging-ui-preview --token-file=staging-bot-token.txt --moderation-config-file=staff-bot-runtime.properties --tunnel-binary-file=cloudflared --tunnel-token-file=cloudflared-token.txt --preview-public-url=https://staff-staging.enthusia.info
```

Create `staff-bot-runtime.properties` in the Discord-bot split root:

```properties
db.jdbc-url=jdbc:mariadb://<database-host>:3306/<database-name>
db.username=<database-user>
db.password=<database-password>
authority.url=http://<paper-full-server-id-or-private-hostname>:8771/v1/staff-rank
authority.transport=bloom-private-split
authority.secret=<random-value-at-least-32-characters>
component.secret=<different-random-value-at-least-32-characters>
# Optional bounded tuning:
# db.pool-size=4
# db.timeout-millis=3000
```

The bot opens its MariaDB pool in JDBC read-only mode and never invokes Flyway. Use the same logical EnthusiaStaff database as the authoritative Paper-side system rather than creating a parallel Discord moderation database. A database principal with read-only grants is preferred when the provider supports one.

## Owner-authorized temporary live-Paper authority bridge

This is the current ES-D16 acceptance path. It is separate from the ordinary full EnthusiaStaff Paper runtime.

Upload the exact validated artifact to the live Paper server's `plugins/` directory as:

```text
plugins/EnthusiaStaff-AuthorityBridge.jar
```

Do not install the full `EnthusiaStaff-Paper.jar` solely for this acceptance test.

On first controlled startup, Paper creates the plugin data directory. The required runtime file is:

```text
plugins/EnthusiaStaffAuthorityBridge/authority.properties
```

Its allowlisted content is:

```properties
authority.secret=<the-same-authority.secret-used-by-the-staff-bot>
# Optional; defaults to 8771:
# authority.port=8771
```

`authority.secret` must contain at least 32 characters. Unknown properties, a missing/weak value, an unreadable file, or an invalid port fail closed.

Do **not** create a public Bloom allocation for port `8771`. The staging staff-bot must reach the live Paper process through Bloom-private networking, and `staff-bot-runtime.properties` must use that private Paper hostname/server ID in `authority.url`.

The bridge has a hard dependency on LuckPerms and resolves the existing EnthusiaStaff staff-rank permission contract from current LuckPerms state. It registers no commands, declares no permissions, registers no Bukkit listeners, opens no database connection, and has no punishment or player-mutation adapter. Stop/remove the bridge after acceptance if it is no longer needed.

### Controlled restart order

1. Stop the live Paper server cleanly.
2. Upload the exact validated `EnthusiaStaff-AuthorityBridge.jar` to `plugins/`.
3. Ensure `plugins/EnthusiaStaffAuthorityBridge/authority.properties` contains the matching authority value before the acceptance start. If the directory does not yet exist, it may be created manually in DuckPanel; a failed first start caused only by the missing file is not required.
4. Confirm port `8771` has no public Bloom allocation.
5. Start live Paper and verify the bridge reports a successful, sanitized startup without exposing the authority value.
6. Only after Paper is healthy, start/restart the staging Staff Bot with its validated JAR/config/tunnel files.
7. Run sanitized D16 acceptance. No destructive moderation action or player mutation is permitted.

## Non-production full-Paper path (not used for the current live acceptance)

For a future dedicated non-production Paper split, the full EnthusiaStaff Paper runtime may use file-backed database and authority configuration under `plugins/EnthusiaStaff/`. That is a separate deployment path and must not be confused with the temporary live authority bridge above.

The full runtime database file is:

```text
plugins/EnthusiaStaff/database.properties
```

and the full runtime authority file is:

```text
plugins/EnthusiaStaff/discord-staff-authority.properties
```

Those files are not required by `EnthusiaStaff-AuthorityBridge.jar` and must not be created as substitutes for `plugins/EnthusiaStaffAuthorityBridge/authority.properties`.

## Permanent Cloudflare deployment

The permanent staging workflow is `.github/workflows/moderation-web-staging-deploy.yml`. For an exact source commit it builds/tests the Worker, provisions the fixed staging tunnel and DNS route, deploys the Worker/assets, verifies origin/session/replay behavior, and records the exact source SHA. Queued, skipped, failed, cancelled, or wrong-head runs are not acceptance evidence.

The fixed D16 private ingress is `moderation-read-staging.enthusia.info` to `http://127.0.0.1:8766`. Port `8766` must never be exposed as a public Bloom allocation. The staff-bot process owns the connector lifecycle so the connector and read API share the same container/network namespace.

## Owner acceptance

Acceptance requires the staging Discord application entitlement, exact validated staff-bot and authority-bridge artifacts, connected fixed Cloudflare tunnel, private Bloom authority connectivity, and sanitized real-data reads proving actor/target/guild authorization and bounded Discord/D06 data. Evidence must not expose private values.

## Release provenance

The artifact paths publish source/checksum provenance. Before replacing either Bloom runtime, verify the source SHA and JAR checksum against the exact reviewed candidate.

## Disabling preview / rollback

Stop the staging Staff Bot and remove its preview/tunnel activation flags to disable the web preview. For the temporary live-Paper acceptance bridge, stop Paper cleanly, remove `plugins/EnthusiaStaff-AuthorityBridge.jar` and optionally `plugins/EnthusiaStaffAuthorityBridge/`, then restart Paper. Removing the bridge restores the live server to its pre-acceptance plugin surface.