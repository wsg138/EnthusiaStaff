# Staff bot staging moderation web preview

This runbook covers the owner-directed, staging-only moderation preview. Discord is the launch surface and `https://staff-staging.enthusia.info` is the permanent web workspace. This is not ES-D07 and it has no live punishment, message deletion, Minecraft enforcement, LiteBans mutation, punishment-database mutation, or production authority path.

## Accepted architecture

- The staging Discord bot runs on a Bloom split as an isolated Java 21 process.
- `/moderate-preview` returns an ephemeral `Open Moderation Panel` link.
- The moderation website is permanently hosted by Cloudflare from this repository.
- Cloudflare Workers Static Assets serve the approved UI through the Worker; direct unauthenticated workspace access is rejected.
- A SQLite-backed Durable Object owns one-time launch replay state and browser session/CSRF state.
- D16 attaches the hosted workspace to the existing D03/D06 read-only authority/data runtime through authenticated private bridges.
- Destructive punishment, message-deletion, and permission-override operations remain simulation-only.

The static UI source remains under `staff-bot/src/main/resources/moderation-preview` so the product contract tests and Cloudflare build share exactly one owner-approved asset set. `moderation-web/scripts/build.mjs` copies those assets into the Cloudflare deployment bundle.

## Safety model

Preview mode can be enabled only for the fixed staging environment. Production configuration rejects it. Without complete D06/D16 runtime configuration the preview remains unable to serve real moderation reads. A complete `--moderation-config-file` initializes only the read-only D06 data/authority runtime and the D16 loopback read API. It does not initialize any punishment, deletion, permission-override, or moderation-database mutation adapter.

The D16 read API always binds only to `127.0.0.1:8766`. The staging bot may supervise a panel-uploaded `cloudflared` process so the existing named tunnel can reach that loopback listener without a public Bloom allocation. The browser never receives MariaDB credentials, Discord bot credentials, component-signing secrets, authority credentials, or Cloudflare tunnel credentials. The Cloudflare Worker signs bounded service requests to the fixed private backend and applies explicit response allowlists.

The Paper authority remains the current LuckPerms-backed authority. Its default mode remains exact IPv4 loopback. The staging-only Bloom split transport is an explicit opt-in: Paper binds its authority listener for split-network reachability, accepts only private/loopback peers, requires short-lived HMAC-signed replay-resistant requests, and signs authenticated responses. The staff bot resolves and pins the configured split hostname only when it resolves exclusively to private addresses. Discord roles never become an authority source.

## Launch and browser-session security

Each Discord launch URL contains only a short-lived signed staging ticket. Claims bind the staff actor, staging guild, concrete target, issue/expiry times, and a random nonce. The Worker verifies HMAC-SHA256 and atomically consumes the nonce before creating a short-lived browser session.

The browser session cookie is host-only, `Secure`, `HttpOnly`, and `SameSite=Strict`, and protected responses use `private, no-store` plus the existing CSP/cross-origin controls. Rejection responses intentionally do not expose internal validation details.

## Runtime secret boundary

The raw Discord bot token, MariaDB credential, authority credential, component-signing credential, and Cloudflare tunnel token are runtime-only files/values. They must never appear in browser code, public source, URLs, logs, artifacts, issue comments, or chat. Protected GitHub automation derives only the staging launch/service signing material it needs; raw Discord credentials are not uploaded to Cloudflare.

The Cloudflare account API token used by GitHub Actions is not copied to Bloom. Bloom receives only the token for the one remotely managed staging tunnel, stored in a file and passed to `cloudflared` using `--token-file`.

## Bloom staff-bot split — file-backed configuration

Use Java 21 and the exact validated staff-bot JAR. Bloom does not need panel environment variables for the D16 read-only configuration.

Upload these runtime-only files to the staff-bot split root:

- `EnthusiaStaff-StaffBot.jar` — exact validated D16 artifact;
- `staging-bot-token.txt` — staging Discord bot token only;
- `staff-bot-runtime.properties` — D06/D16 database/authority/component configuration;
- `cloudflared` — current Linux binary, version 2025.4.0 or later because `--token-file` is required;
- `cloudflared-token.txt` — token for the existing `enthusia-moderation-read-staging` tunnel only.

The Java runtime makes the uploaded `cloudflared` binary executable when the panel filesystem permits it, starts it without a shell, monitors it, fails the bot closed on unexpected connector exit, and terminates it during normal shutdown. The tunnel token contents never become a process argument.

```text
JAR FILE:
EnthusiaStaff-StaffBot.jar

FLAGS:
-Dterminal.jline=false -Dterminal.ansi=true

APP FLAGS:
--staging-ui-preview --token-file=staging-bot-token.txt --moderation-config-file=staff-bot-runtime.properties --tunnel-binary-file=cloudflared --tunnel-token-file=cloudflared-token.txt --preview-public-url=https://staff-staging.enthusia.info
```

Create `staff-bot-runtime.properties` in the Discord-bot split. Use the same EnthusiaStaff MariaDB database as Paper. Do not create a parallel moderation database.

```properties
db.jdbc-url=jdbc:mariadb://<database-host>:3306/<database-name>
db.username=<database-user>
db.password=<database-password>
authority.url=http://<paper-full-server-id-or-private-split-hostname>:8771/v1/staff-rank
authority.transport=bloom-private-split
authority.secret=<random-secret-at-least-32-characters>
component.secret=<different-random-secret-at-least-32-characters>
# Optional bounded tuning:
# db.pool-size=4
# db.timeout-millis=3000
```

The bot opens its MariaDB pool in JDBC read-only mode and never invokes Flyway. A database principal with read-only grants is still preferred when the provider supports one, but the bot does not require a second database. The file parser accepts only documented keys, rejects partial/unknown configuration, and never renders secret values or the authority hostname through configuration `toString()` output.

## Bloom Paper split — database and authority files

These files are for an authorized non-production/staging Paper runtime. D16 does not authorize installing or activating them on a production Minecraft server.

### Paper database file

Paper can use the same MariaDB database without panel environment variables. Place this runtime-only file at:

```text
plugins/EnthusiaStaff/database.properties
```

with:

```properties
db.jdbc-url=jdbc:mariadb://<database-host>:3306/<database-name>
db.username=<database-user>
db.password=<database-password>
```

The ordinary Paper runtime owns schema/Flyway initialization. The staff bot only reads the resulting authoritative schema.

### Paper authority file

Place this runtime-only file at:

```text
plugins/EnthusiaStaff/discord-staff-authority.properties
```

with:

```properties
authority.bind=bloom-private-split
authority.port=8771
authority.secret=<the-same-authority.secret-used-by-the-staff-bot>
```

Do not create a public Bloom allocation for port `8771`. The Paper split and staff-bot split must be in the same Bloom split group, and the bot's `authority.url` must use Paper's full server ID/private split hostname. Paper continues to derive rank from current LuckPerms state on every request. Private-split requests are HMAC-authenticated, short-lived, replay-resistant, source-private-only, and their responses are signed before the bot accepts a rank.

If the Paper authority file is missing, partial, malformed, weak, or requests a mode other than the documented loopback/private-split choices, the authority bridge remains unavailable rather than inventing authority. Existing environment/loopback behavior remains supported for older deployments.

## Permanent Cloudflare deployment

The permanent staging workflow is `.github/workflows/moderation-web-staging-deploy.yml`. For an exact source commit it builds/tests the Worker, provisions the fixed staging tunnel and DNS route, deploys the Worker/assets, verifies origin/session/replay behavior, and records the exact source SHA. Queued, skipped, failed, cancelled, or wrong-head runs are not acceptance evidence.

The fixed D16 private ingress is `moderation-read-staging.enthusia.info` to `http://127.0.0.1:8766`. Port `8766` must never be exposed as a public Bloom allocation. The staff-bot process owns the connector lifecycle so the connector and read API share the same container/network namespace.

## Owner acceptance

Acceptance for D16 requires the staging Discord application Message Content entitlement, exact validated staff-bot and applicable Paper artifacts, connected fixed Cloudflare tunnel, private Bloom authority connectivity, and sanitized real-data reads that prove actor/target/guild authorization and bounded Discord/D06 data without exposing private values in evidence.

## Release provenance

The staging release/artifact path publishes the staff-bot JAR plus source/checksum provenance. Applicable Paper artifacts are likewise source-bound by repository validation. Before replacing a Bloom staging runtime, verify source SHA and JAR checksum against the exact reviewed candidate.

## Disabling preview

Stop the Bloom staff-bot process and remove the staging-preview/tunnel activation flags. Remove runtime-only configuration/tunnel files if the preview is being decommissioned. Stop/remove any staging-only Paper authority configuration separately. Production must never be configured with the staging-preview activation path under D16.
