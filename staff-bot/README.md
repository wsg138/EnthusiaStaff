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

## Discord account-age admission rule

The **PRODUCTION** StaffBot enables its Discord account-age gate by default
during normal environment-based startup. The pinned STAGING StaffBot stays
disabled by default and refuses explicit enablement because it shares the
live guild. Every new *human* Discord member receives an age
check based on their Discord account creation timestamp (not Minecraft account
age). Accounts younger than **21 complete days** are kicked *after joining*,
unless their exact Discord user ID is bypassed. Accounts exactly 21 days old
and older are allowed. The gate does not search or remove existing members,
ban accounts, or alter the Minecraft punishment system.

Staff members holding **Manage Server** may manage durable exceptions through
these private slash commands, including for users who have not joined:

- `/agebypass action:add user-id:123456789012345678`
- `/agebypass action:remove user-id:123456789012345678`
- `/agebypass action:check user-id:123456789012345678`

The exception list defaults to `account-age-bypass-ids.txt` in the bot
working directory. Set
`ENTHUSIA_STAFF_BOT_ACCOUNT_AGE_BYPASS_FILE=/persistent/path/ids.txt`
if the deployment uses another persistent data directory. **Do not delete
or overwrite that file during jar updates**; exceptions must survive restarts.
Startup rejects a corrupted/unreadable list rather than silently forgetting
approved users.

To **turn off** the gate intentionally, set
`ENTHUSIA_STAFF_BOT_ACCOUNT_AGE_GATE_ENABLED=false`; unset or `true`
means enforcement enabled in PRODUCTION only. This setting is independent of
`ENTHUSIA_STAFF_BOT_DISCORD_ENFORCEMENT_ENABLED` for punishment actions.

**Prerequisites before installing the default-on build:** enable the
privileged **Server Members Intent** for the *StaffBot application* in the
Discord Developer Portal; grant the bot **Kick Members** and position its
role above accounts it must remove. Operators using `/agebypass` require
**Manage Server**. If permissions are missing, join-time kicks cannot
succeed and the process logs errors; the bot does not assert that the
member was removed. A Discord user enters the guild briefly before
the kick can be executed. Members who join while the bot is offline
are not automatically rescanned.

The pinned STAGING and PRODUCTION StaffBot applications both reference the
Enthusia guild. **STAGING rejects account-age enforcement and may not be
used as a destructive test.** Use source-level tests or a separately authorized
isolated guild/application for real join/kick acceptance. This runbook does
not authorize a live bot deployment.

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

## Existing production StaffBot plus separate shadow-chat bot

When DiscordSRV remains responsible for live chat, the production StaffBot may
optionally start a **second, chat-only Discord application** solely for the
hard-pinned staging test channel. This is not an authoritative cutover. The
primary Discord application retains all staff commands, authority, database
reads, moderation roles and enforcement policies; the public-chat token is
never used for those functions.

The existing Bloom file-backed startup can retain the final argument:

```text
--environment=production --token-file=tp --moderation-config-file=m --tunnel-binary-file=cloudflared --tunnel-token-file=prod-tunnel --moderation-web-url=https://staff.enthusia.info --chat=c
```

`--chat=c` or `--chat-bridge-config-file=<private-file>` reads a private
properties file with only allowlisted chat settings; it rejects duplicate
keys, symlinks and conflicts with process environment and cannot override
the main Discord token or environment.

For the opt-in production SHADOW trial, file `c` must contain
`ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_MODE=SHADOW`, the exact
`ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_MIGRATION_ACK` approval, configured
Velocity TLS/HMAC secrets, a route pointing **only** at the pinned staging
Discord channel, and the separate
`ENTHUSIA_STAFF_BOT_PUBLIC_CHAT_TOKEN` and
`ENTHUSIA_STAFF_BOT_PUBLIC_CHAT_APPLICATION_ID`. These are private
values: don't expose them in logs, repository, screenshots or startup flags.
A standalone secondary JDA session is validated against the separately
configured application ID and the pinned guild; it never installs staff
interaction or punishment listeners. Production `AUTHORITATIVE` still
requires `I_ACKNOWLEDGE_DISCORDSRV_CHAT_CUTOVER` separately, and must not
be selected while DiscordSRV owns live chat.

A successful primary `staff_bot_ready` log does not prove the secondary
chat connection. Verify `public_chat_ready` and an actual synthetic
Minecraft message in the pinned Discord test channel. If the secondary
session fails, restore the previous working JAR, not an authoritative
DiscordSRV takeover. The AI mirror durable registration feature remains
staging-only, independently gated and off by default.

## Experimental AI mirror-registration reliability (staging only)

The RoseChat -> Discord post-send mirror receipt publisher is **off by default**
and **cannot be enabled in the production environment**. It is a metadata
registration client, not an AI classifier, message-deletion mechanism, or
punishment engine. It submits only actual JDA-returned Discord snowflakes for
allowlisted public guild/channel copies and uses the original canonical ID and
undecorated source text. The Moderation API requires a separate
mirror:register credential and validates both platforms' public-chat scopes.

The **single daemon worker** permits at most 65 outstanding receipts (one
potentially executing plus 64 pending). Submission from Discord's callback
never waits for the API; further receipts are dropped when full. HTTP requests
have a 2-second deadline and redirects are forbidden. Only these failures may
retry a registration (never a Discord post), with delays of 250/500/1000 ms,
at most **four total requests** and **15 seconds since original receipt**:

- HTTP 409 with X-Moderation-Mirror-Error: canonical_pending
- HTTP 503 with X-Moderation-Mirror-Error: timeout or storage_unavailable
- HTTP network I/O timeout/connection failure

An unknown 404 source, any 409 mirror_conflict, authorization/schema
failure, unexpected status, or missing/unrecognized machine code is terminal.
Pause/shutdown cancels queued retries, and resuming never reactivates previous
receipts. Aggregate-only counters for confirmed, pending, conflict, capacity
drop, timeout, expiry, unavailable, rejection and retries are exposed through
the publisher snapshot and emitted on pause; logs must not include text,
message IDs, token values or endpoints.

**Important remaining limitation:** the retry state is *volatile*.
Crash/restart, lost receipts, long outages, unresolved 404, or a full queue
can leave Discord copies unregistered. There is deliberately no automatic
reconciliation from message content or guessed IDs. Do **not** use this as
evidence of complete mirror coverage or activate retrospective deletion or
punitive AI actions. Durable privacy-reviewed reconciliation, actual JDA
staging validation, and separately approved deletion behavior remain release
gates in wsg138/AI-Moderation-API#116.

An isolated `AiMirrorEncryptedOutbox` **ChaCha20-Poly1305 encrypted-journal prototype** has
unit tests for persisted synthetic receipt replay, key/endpoint binding,
tampering, scope rejection, exclusive lock, capacity, expiry and incomplete
temporary writes. This class is **not instantiated by the running bot**,
has no environment switch and does not yet provide crash reconciliation.
Its proposed disk policy, operating-system access control, key provisioning,
nonblocking I/O pipeline, safe retry orchestration and cross-JVM tests
remain to be reviewed before any staging activation.

The separate `AiMirrorReceiptReplay` follow-up prototype adds a **single
synchronous recovery attempt**, invoked only by synthetic tests and never by
the running bot. It fsyncs a monotonically increasing, authenticated recovery
attempt count in the encrypted journal **before** attempting HTTP. A maximum
of eight slow-recovery attempts applies across restarts; exhausted/expired
records are removed. HTTP 200 confirms an exact alias then acknowledges the
record. Only explicitly typed pending / storage / timeout responses or
transport I/O failure retain a record; unknown-canonical 404, collision,
authorization, malformed responses and other unexpected statuses are terminal.
A stale journal snapshot cannot remove a newer record. The encrypted
receipt now also retains the prior attempt timestamp. If a restart
occurs less than 60 seconds after the prior attempt, replay is
`DEFERRED` **without any HTTP request or attempt-budget charge**.
A simulated clock move backward also defers rather than immediately
issuing duplicate calls; corrupt/future timestamps fail closed.
Synthetic tests advance the fake clock across successive restarts
and verify 8 attempts remain the lifetime cap.

An additional isolated `AiMirrorRecoveryScheduler` prototype now runs
**one slow recovery attempt per minute conservatively, or one every five seconds after an authenticated HTTP 200 and durable ACK, on one daemon worker** from
already encrypted journal records. It keeps only aggregate counts; a corrupt
or inaccessible journal stops its processing without HTTP fallback. Synthetic
tests cover first-attempt success, typed transient recovery across a new
scheduler/unchanged key and corrupted ciphertext fail-closed. It also exposes a privacy-safe,
in-memory `expiredReceipts` count for authenticated records discarded by
the journal's 60-minute TTL, including expiry during recovery admission.
Each record is counted once even if repeatedly inspected. This counter
contains no text or message identifiers and resets with the JVM; it is
**not** yet a durable accounting audit or a production monitoring dashboard.

These components are available only through the separately gated,
**off-by-default staging configuration**; absent that configuration,
StaffBot retains its existing volatile publisher. Target secret
provisioning, actual staging acceptance, and production authorization
are **not** complete. Do not activate real Discord traffic without
owner-approved staging validation, or treat this as proof of full
mirror coverage or safe cross-bot deletion.

A separate `AiMirrorDurableReceiptIngress` prototype accepts only scoped
actual post-send receipts into a queue of at most 64 pending, served by one
daemon disk writer. Submission is nonblocking; queue overflow is counted and
does not delay Discord delivery. Disk failure closes admission; pending tasks
are discarded on pause/close. It **only persists encrypted receipts**: it
does not make API calls, start scheduling, send Discord messages, or connect
itself to StaffBotRuntime. Synthetic tests cover blocked disk I/O, overflow,
scope rejection, encrypted persistence/reopening and fail-closed disk errors.
Admission cannot guarantee zero loss on queue overflow, crash-before-fsync or
full/failed storage. Activation requires separate privacy-reviewed
staging-only config and lifecycle wiring.

**Opt-in staging-only runtime selection (code only; not deployed):**
`StaffBotRuntime` now reads `ENTHUSIA_STAFF_BOT_AI_MIRROR_DURABLE_ENABLED`
at startup, **default false**. When false, the existing volatile mirror
registration works unchanged and no durable key is read. When true, the
existing mirror registration flag/config must already be enabled **in
STAGING**, then a separately pre-provisioned directory and key are required:
`ENTHUSIA_STAFF_BOT_AI_MIRROR_DURABLE_DIRECTORY` (absolute path, POSIX 0700)
and `ENTHUSIA_STAFF_BOT_AI_MIRROR_DURABLE_KEY_FILE` (absolute path, exactly
32 random binary bytes, POSIX 0400 or 0600, same owner, no symlink). Invalid
settings fail startup closed. Production cannot select this mode. The
key must be separate from the existing API/Discord bearer tokens. Key
loading rechecks provisioning, rejects symlink final components when opening,
and reads exactly 32 bytes through a bounded file handle. It also rechecks
pathname permissions and ownership after reading; Java cannot atomically
verify those attributes against the open file descriptor, so same-owner
concurrent path replacement is outside this guarantee. Invalidated keys
fail closed with sanitized diagnostics. The
authenticated destination binding covers endpoint, client ID, guild,
profile and authorized channels, so a change to any of these refuses
old ciphertext.

Exactly **one** JDA post-send observer is installed: either the volatile
publisher, or the durable session which persists receipts off the JDA
callback thread, attempts prompt first registration through a separate
bounded HTTP worker, and uses a durable, nonpunitive slow HTTP
replay worker only for retained receipts. Gateway
identity readiness gates `resume()`; disconnect pauses admission/retries,
and shutdown cancels queued work and only releases the store lock after all
three ingress/fast/recovery workers terminate. An early identity callback before `chat.start()` is
deferred safely. Settings are *not provisioned* in any Bloom environment
by this change: the live server, staff bot, Discord, punishment/deletion,
moderation policy and production runtime remain untouched.

**Remaining acceptance gates before a real staging enablement:** explicit
owner authorization to provision and activate this mode in the staging
app, target Linux filesystem/key security review and retention/backup
policy, real JDA post-send proof, loss/overflow monitoring, duplicate
registration test, and safe non-punitive replay under restart stress.
This follow-up persists a separate ChaCha20-Poly1305 authenticated
`.mirror-rate` timestamp on disk. The rate update is fsynced **before**
any HTTP attempt, and remains after an individual receipt is acknowledged.
Across JVM restarts, an unconfirmed or failed slow attempt requires the
same 60-second cooldown; after HTTP 200 plus a successful durable journal
ACK, an authenticated version-3 rate state permits one slow attempt every
five seconds. Each retry persists a conservative token before HTTP, so any
409/503/IO failure or crash before the ACK revokes the faster mode. The
old version-2 rate file remains readable with its conservative limit. A corrupted or unauthenticated rate file
blocks HTTP with no fallback. Interrupted `.mirror-rate.tmp` files are
discarded during recovery. The rate record contains no chat text or
Discord identifiers. Synthetic tests cover ACK followed by a second
receipt, rapid restart, deferred retry and tampered state. Target Linux
filesystem security and staging load testing remain acceptance gates.
Recovery selection prioritizes **never-attempted** receipts first, then
retries by least recent attempt time, instead of letting one stale older
receipt monopolize the full retry budget. Deterministic synthetic
three-receipt tests verify the order survives journal reopenings.
No retrospective deletion or automatic sanctions are authorized.

**Immediate post-persistence registration (code only; no staging activation).**
The opted-in STAGING durable session now constructs one fast HTTP worker
with 64 waiting tasks, alongside the authenticated, outcome-gated slow replay.
After authenticated encrypted fsync on the bounded ingress disk worker,
new receipts are offered to the fast worker without blocking JDA. Fast
admission claims the opaque record name before queueing. The fast and slow
workers share this single-session claim guard, so both cannot send or
acknowledge the same receipt concurrently. Before either sends HTTP, the
outbox authenticates its exact current encrypted revision. Missing
already-acknowledged snapshots are skipped; an existing mismatched or
corrupt revision fails closed. The journal (not in-memory claims) remains
authoritative across JVM restarts.

HTTP 200 acknowledges the exact record. Only typed `409 canonical_pending`,
typed `503 timeout/storage_unavailable` and transport timeout/IO are
temporary; they retain ciphertext for bounded slow recovery. Other 404,
409, auth, schema, redirect or unknown responses are terminal. Saturation,
pause, shutdown or loss of in-memory claims must never erase a committed
receipt. Slow recovery alone consumes the persisted global interval (60 seconds
conservatively, 5 seconds only after a successful slow HTTP 200+ACK)
and the eight-attempts-per-receipt limit. The fast worker does not.
All metrics remain aggregate-only; this feature never reposts to Discord
or issues any deletion or punishment.

Offline verification includes the real Java session against disposable
FastAPI/SQLite with 70 distinct healthy canonical source events and
70 actual registrations. The still-open #541 / private API #116 release
gates require final exact-head CI/review, target staging volume/loss
measurements, key/backup/retention review and separate owner authorization
before enabling this setting. No live or production configuration changes
have been made.

**Aggregate staging telemetry (code only; not enabled):** When and only
when the durable STAGING session is actually selected, the loopback
`GET /ready` response additionally contains numeric-only
`mirrorRecovery` counters: eligible post-send callbacks, encrypted
persistence, ingress capacity/storage/cancel drops, prompt confirmed /
transient / deferred / terminal / storage failures, slow confirmed /
transient / deferred / terminal / exhausted / storage failures, and
expired receipts. `GET /health` and all non-durable/PRODUCTION
responses retain their previous shapes. No text, IDs, keys, paths,
endpoints or exception messages are ever included. The values are
process-lifetime snapshots (not durable across restarts) and cannot
independently prove complete coverage: compare externally counted
eligible original and Discord post-send events, restart windows, and
the journal's eventual pending count before approving any deletion.

**Catch-up limitations and rollback compatibility:** The five-second
catch-up is bounded to one recovery HTTP request at a time (12/minute),
and only while the authenticated previous slow attempt was successfully
acknowledged. It is **not** a zero-loss guarantee: 60-minute receipt TTL,
128-record capacity, restart delays, privacy exclusions, and permanent
rejections still apply. The new authenticated rate record is format v3.
New code reads an existing v2 record conservatively; an older StaffBot
jar may reject v3 on rollback. Never delete `.mirror-rate` or discard the
encrypted journal to force startup. Before a real staging deployment,
review a rollback plan using a compatible jar or a separately approved,
authenticated rate-state migration. No live key, config or server was
modified by the redesign.

The opt-in zero-cost cross-language test harness in the private repository
runs a disposable loopback Python API and the actual Java StaffBot HTTP client,
including PENDING -> 409 -> FINAL -> 200, strict structured IDs, replay,
privacy/conflict refusals, and zero punitive actions. The opt-in harness
also invokes two separate `JavaExec` JVM processes for a synthetic
**real-HTTP commit-before-local-ACK crash**: the first writes an authenticated
receipt, sends the mirror registration to the disposable Python API, then
exits abnormally without acknowledging it. The second JVM acquires the
journal lock, reloads that exact receipt, and verifies idempotent HTTP 200.
The Python fixture checks exactly two scoped message aliases for each
canonical source and no sanction output, including the optional
70-message real HTTP throughput exercise. Neither JavaExec phase is part of normal
build/startup, and the harness strictly allows only 127.0.0.1 loopback.

This is a local test procedure, **not** permission to deploy or enable
production API clients.

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

When the additional staging-only durable mirror registration mode is
explicitly selected, `/ready` also requires both encrypted receipt
ingress and recovery workers to remain healthy. If either reports a
terminal storage/replay failure after the Discord identity becomes ready,
readiness fails closed with HTTP 503 and the **fixed** non-sensitive reason
`mirror_recovery_unavailable`. That failure remains latched until process
restart, even if a later callback briefly reports healthy. `/health`
continues to represent process liveness. No receipt text, IDs, paths,
credentials or underlying exception details enter these status responses.
When durable mode is disabled (the default), previous health behavior
remains unchanged.

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
