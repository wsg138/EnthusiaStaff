# Staff bot staging moderation web preview

This runbook covers the owner-directed D16 moderation preview. Discord and the web workspace remain staging-only and simulation-only. A separately authorized temporary live-Paper acceptance path may use the narrow `EnthusiaStaffAuthorityBridge` described below; that exception does not authorize destructive moderation, message deletion, LiteBans mutation/cutover, or deployment of the full EnthusiaStaff Paper moderation runtime solely for D16 acceptance.

## Accepted architecture

- The staging Discord bot runs on a Bloom split as an isolated Java 21 process.
- `/moderate-preview` returns an ephemeral `Open Moderation Panel` link.
- `https://staff-staging.enthusia.info` is the permanent staging web workspace.
- Cloudflare Workers Static Assets serve the approved UI through the Worker; direct unauthenticated workspace access is rejected.
- A SQLite-backed Durable Object owns one-time launch replay state and browser session/CSRF state.
- For each real-data read, the Worker validates the browser request against the server-side moderation session and mints a short-lived HMAC proof for one exact canonical request body. The browser receives the body plus only that one-use timestamp/nonce/signature, never the signing key.
- The browser sends that exact signed POST directly to `https://moderation-read-staging.enthusia.info`, whose named Cloudflare Tunnel terminates at the Staff Bot loopback API `127.0.0.1:8766`.
- The Staff Bot accepts browser CORS only from the exact staging panel origin, re-verifies the HMAC/expiry/replay fence, and then re-authorizes actor, guild, target and channel-read permissions before returning data.
- D16 attaches the hosted workspace to the existing D03/D06 authority/data model. The Staff Bot remains JDBC read-only.
- Destructive punishment, message-deletion, and permission-override operations remain simulation-only.
- The live Paper acceptance plugin serves current LuckPerms-backed staff rank. Under the owner's 2026-09-04 extension it may additionally run the optional transition collector described below; it still has zero moderation/player mutation commands or adapters.

The static UI source remains under `staff-bot/src/main/resources/moderation-preview` so the product contract tests and Cloudflare build share exactly one owner-approved asset set. `moderation-web/scripts/build.mjs` copies those assets into the Cloudflare deployment bundle.

## Safety model

Preview mode can be enabled only for the fixed staging Discord/web environment. A complete `--moderation-config-file` initializes only the read-only D06 data/authority runtime and D16 loopback read API. It does not initialize punishment, deletion, permission-override, or moderation-database mutation adapters.

The D16 read API always binds to `127.0.0.1:8766`. The staging bot supervises a panel-uploaded `cloudflared` process so the named tunnel can reach that loopback listener without a public Bloom allocation. The browser never receives MariaDB credentials, Discord bot credentials, signing keys, authority credentials, component credentials, or Cloudflare tunnel credentials.

Each Worker-issued read proof is bound to the current Worker session's actor ID, guild ID, target key, endpoint, canonical body, timestamp and random nonce. The Staff Bot's short request window and nonce replay guard prevent reuse. A browser cannot retarget the request without invalidating the signature, and every valid request is re-authorized against current Discord/Paper authority state before data is returned.

Paper remains the current LuckPerms-backed staff authority. The bridge's D16 staff-rank listener accepts only private/loopback peers, requires short-lived HMAC-signed replay-resistant requests, and signs authenticated responses. Discord roles never become an authority source.

## Runtime secret boundary

The Discord bot token, MariaDB credential, authority credential, component-signing credential, and Cloudflare tunnel token are runtime-only files/values. They must never appear in browser code, public source, URLs, logs, artifacts, issue comments, or chat.

The Cloudflare account API token used by GitHub Actions is not copied to Bloom. Bloom receives only the connector token for the remotely managed staging tunnel, stored in a file and passed to `cloudflared` using `--token-file`.

## Bloom staff-bot split

Use Java 21 and the exact validated D16 staff-bot JAR. The current owner acceptance split uses:

- `EnthusiaStaff-StaffBot.jar` — exact validated D16 artifact;
- `t` — staging Discord bot token only;
- `m` — D06/D16 database/authority/component configuration;
- `cloudflared` — current Linux binary with `--token-file` support;
- `cloudflared-token.txt` — token for the existing `enthusia-moderation-read-staging` tunnel only.

```text
JAR FILE:
EnthusiaStaff-StaffBot.jar

FLAGS:
-Dterminal.jline=false -Dterminal.ansi=true

APP FLAGS:
--staging-ui-preview --token-file=t --moderation-config-file=m --tunnel-binary-file=cloudflared --tunnel-token-file=cloudflared-token.txt --preview-public-url=https://staff-staging.enthusia.info
```

The file `m` contains:

```properties
db.jdbc-url=jdbc:mariadb://<database-host>:3306/<database-name>
db.username=<database-user>
db.password=<database-password>
authority.url=http://<paper-full-server-id-or-private-hostname>:8771/v1/staff-rank
authority.transport=bloom-private-split
authority.secret=<random-value-at-least-32-characters>
component.secret=<different-random-value-at-least-32-characters>
# Optional:
# db.pool-size=4
# db.timeout-millis=3000
```

The Staff Bot opens this database in JDBC read-only mode and never invokes Flyway. It must point at the same logical EnthusiaStaff database used by the transition collector/full future runtime, never a parallel moderation database.

## Owner-authorized temporary live-Paper bridge

This is the current ES-D16 acceptance path. Upload the exact validated artifact as:

```text
plugins/EnthusiaStaff-AuthorityBridge.jar
```

Do not install the full `EnthusiaStaff-Paper.jar` solely for this acceptance test.

The required authority file remains:

```text
plugins/EnthusiaStaffAuthorityBridge/authority.properties
```

```properties
authority.secret=<the-same-authority.secret-used-by-the-staff-bot>
```

Keep the default authority port `8771` and do not create a public Bloom allocation for it. The staging Staff Bot must reach Paper through Bloom-private networking.

### Optional transition collector — owner-authorized 2026-09-04

After live acceptance exposed a clean-but-unmigrated EnthusiaStaff database (`moderation_subject_discord_identities` was absent), the owner explicitly authorized the temporary bridge to initialize that database and gather narrowly useful transition observations.

Enable this mode only by creating a second runtime-only file:

```text
plugins/EnthusiaStaffAuthorityBridge/collector.properties
```

Use the same logical EnthusiaStaff database as the Staff Bot `m` file. The collector account must have the schema/write privileges needed for the repository migrations and observation/link inserts. Do not paste these values into GitHub, chat, or logs.

```properties
db.jdbc-url=jdbc:mariadb://<same-database-host>:3306/<same-database-name>
db.username=<write-capable-transition-user>
db.password=<database-password>
# Optional bounded settings:
# db.pool-size=2
# db.timeout-millis=3000
# collector.server-id=SMP
# collector.interval-seconds=60
```

When this file is present, the bridge:

1. opens a narrow transition runtime and applies the repository's existing Flyway migrations to the EnthusiaStaff database;
2. periodically records bounded online/cached player identity observations with platform left `UNKNOWN` unless a verified provider later supplies it;
3. reads DiscordSRV's current AccountLinkManager snapshot only; it never calls DiscordSRV link/unlink mutators;
4. imports only links whose Minecraft identity has a usable observed username, through the existing `DiscordSrvMigrationService` conflict/replay semantics and `MIGRATED_DISCORDSRV` source;
5. processes at most 128 cached offline linked players per pass and rejects provider snapshots above 5,000 links;
6. performs database persistence on one bounded worker thread and skips overlapping passes;
7. logs aggregate counts and exception classes only, never player IDs, usernames, link pairs, SQL text, or credentials.

If `collector.properties` is absent, the bridge remains authority-only. If collector configuration/migration/startup fails, the collector stays unavailable but the signed LuckPerms authority endpoint remains enabled. The collector has no commands, no punishment/freeze/inventory/economy/reputation/automod/message-deletion adapters, and no player-facing behavior.

DiscordSRV remains the legacy link source during this observation phase; the bridge never writes back to it. LiteBans also remains authoritative. The repository already has a dedicated SELECT-only LiteBans migration/shadow system with checksums, high-water marks, protected identities, and cutover fencing; duplicating that migration engine inside this temporary bridge is intentionally out of scope. No LiteBans writes, authority change, or cutover are authorized here.

### Controlled restart order

For the transition-enabled bridge:

1. Stop live Paper cleanly.
2. Replace `plugins/EnthusiaStaff-AuthorityBridge.jar` with the exact validated artifact.
3. Keep the existing `authority.properties` unchanged unless a real authority configuration error requires otherwise.
4. Add `collector.properties` with the same logical EnthusiaStaff database connection used by the Staff Bot, but with credentials capable of migration/observation writes.
5. Confirm ports `8771` and Staff Bot `8766` still have no public Bloom allocations.
6. Start Paper. Verify sanitized lines for authority startup and `enthusiastaff_transition_collector_started`; never publish config contents.
7. Observe an aggregate `enthusiastaff_transition_collector_pass ...` line. Conflicts are retained rather than overwritten.
8. Start/restart the staging Staff Bot only if its JAR/config changed, then open a fresh Discord-generated moderation preview.
9. Run sanitized D16 acceptance. No destructive moderation action or player mutation is permitted.

Removing `collector.properties` and restarting Paper disables future transition collection without deleting collected EnthusiaStaff data. Removing the bridge entirely restores the pre-acceptance plugin surface.

## Non-production full-Paper path

A future dedicated non-production/full deployment uses `EnthusiaStaff-Paper.jar` and its normal storage files. That is separate from this temporary acceptance bridge and must not be substituted into the live server solely for D16.

## Permanent Cloudflare deployment

The permanent staging workflow is `.github/workflows/moderation-web-staging-deploy.yml`. For an exact source commit it builds/tests the Worker, provisions the fixed staging tunnel and DNS route, deploys the Worker/assets, verifies origin/session/launch-replay behavior, obtains a synthetic session-bound signed read proof, verifies exact-origin CORS against the live Bloom read API, proves a signed synthetic unauthorized actor is denied, proves read-proof replay rejection, and records the exact source SHA. It never uses a real player/message target for that synthetic transport probe.

The fixed D16 ingress is `moderation-read-staging.enthusia.info` to `http://127.0.0.1:8766`. Port `8766` must never be exposed as a public Bloom allocation.

## Owner acceptance

Acceptance requires the staging Discord application entitlement, exact validated staff-bot and bridge artifacts, connected fixed Cloudflare tunnel, private Bloom authority connectivity, migrated/populated EnthusiaStaff read data, and sanitized real-data reads proving actor/target/guild authorization and bounded Discord/D06 data. Evidence must not expose private values.

Final D16 acceptance additionally requires a fresh Discord-launched real target to prove linked identity, sanctions/history semantics, readable-channel/message bounds, and truthful source-unavailable behavior while preserving simulation-only moderation actions.

## Release provenance

The artifact paths publish source/checksum provenance. Before replacing either Bloom runtime, verify the source SHA and JAR checksum against the exact reviewed candidate.

## Disabling preview / rollback

Stop the staging Staff Bot and remove its preview/tunnel activation flags to disable the web preview. For the temporary live-Paper bridge, stop Paper cleanly and remove `plugins/EnthusiaStaff-AuthorityBridge.jar`; removing only `collector.properties` disables future transition collection while leaving the authority endpoint available after restart.
