# Staff Discord bot runtime

Status: ES-D05 implementation contract. This document covers the isolated staff-bot process only. It does not authorize production deployment, Discord production changes, punishment enforcement, AutoMod, LiteBans cutover, or bot-token disclosure.

## Runtime boundary

`staff-bot` is a third Java 21 runtime beside Paper and Velocity. It is not loaded as a Minecraft plugin and a Discord outage/restart does not restart either Minecraft runtime. The existing durable webhook-delivery subsystem remains separate and unchanged.

The runtime uses JDA 6.5.0 without the optional voice/audio-native stack. JDA owns Discord REST rate-limit scheduling and Gateway reconnect behavior. EnthusiaStaff adds fail-closed identity/guild checks, bounded application work, lifecycle health, and replay protection around it.

The executable shaded artifact is `staff-bot/build/libs/EnthusiaStaff-StaffBot-<version>.jar`. Repository `check` verifies the jar is readable, has the correct `Main-Class`, includes both the Enthusia entry point and JDA, excludes the unused Opus/Tink audio classes, verifies the patched Jackson versions, and records size/SHA-256 evidence under `build/reports/runtime-jars/` for hosted validation.

## Dependency security note

The implementation-time dependency review found that JDA 6.5.0 publishes `jackson-core:2.22.0` and `jackson-databind:2.22.0`. `jackson-databind:2.22.0` is in the affected range for CVE-2026-59889; upstream fixes that issue in 2.22.1. The staff-bot runtime therefore pins both Jackson core components to 2.22.1, and `verifyStaffBotRuntime` fails the build if resolution drifts away from 2.22.1.

JDA's earlier GHSA-93fv-4pm9-xp28 SSRF issue was fixed in 6.1.3; selected JDA 6.5.0 is beyond that fixed release. No voice path is used, so JDA's supported `opus-java` and `tink` exclusions remove those unnecessary native/crypto dependencies from the runtime.

## Fixed public identities

| Environment | Application ID | Allowed guild | Required staging test channel |
| --- | --- | --- | --- |
| staging | `1541279616881397772` | `1410303324745371709` | `1541286004298752091` |
| production | `1541279426233376818` | `1410303324745371709` | n/a |

A connected token is not trusted merely because configuration says `staging` or `production`. After the Gateway becomes ready, the runtime retrieves the actual Discord application identity and fails closed unless:

- the application ID exactly matches the selected environment;
- the Discord application is private/non-public;
- the bot is in exactly one guild and that guild is Enthusia;
- staging can resolve the configured test channel in the Enthusia guild and has `VIEW_CHANNEL` plus `MESSAGE_SEND` there.

D05 never sends a test message. The staging channel check proves the bot can perform later staff interactions without creating a destructive or noisy validation side effect.

## Configuration and secrets

Configuration is environment-only. `.env` files are ignored by Git and bot tokens must be supplied by an authorized secret manager/runtime environment, never committed, logged, copied into tests, or pasted into chat.

Required variables:

- `ENTHUSIA_STAFF_BOT_ENVIRONMENT`: exactly `staging` or `production`;
- `ENTHUSIA_STAFF_BOT_TOKEN`: token for the selected application.

Optional variables:

- `ENTHUSIA_STAFF_BOT_HEALTH_HOST`: loopback only; default `127.0.0.1`;
- `ENTHUSIA_STAFF_BOT_HEALTH_PORT`: default `8765`; staging may use `0` for an ephemeral test port, production may not;
- `ENTHUSIA_STAFF_BOT_WORKER_THREADS`: default `4`, bounded `1..16`;
- `ENTHUSIA_STAFF_BOT_WORKER_QUEUE_CAPACITY`: default `256`, bounded `1..4096`;
- `ENTHUSIA_STAFF_BOT_INTERACTION_CAPACITY`: default `4096`, bounded `16..65536`;
- `ENTHUSIA_STAFF_BOT_INTERACTION_TTL_SECONDS`: default `900`, bounded to at most 24 hours.

`StaffBotConfiguration.toString()` always renders the token as `<redacted>`. Lifecycle logging records only environment/state and fixed reason categories; it does not log token values, Discord message content, evidence, user identities, or private moderation data.

## Intents and workload bounds

D05 enables only `GUILD_MEMBERS` as an explicit privileged Gateway intent. It does not request Message Content because no D05 feature reads messages. Member chunking/cache are disabled at this foundation layer.

Later interaction work is submitted to a fixed-size executor with a bounded queue. Saturation rejects new work instead of growing memory without bound and increments a privacy-safe health counter. Gateway lifecycle callbacks are not routed through that application queue, so command workload saturation cannot prevent reconnect/identity fencing. Worker threads receive a five-second graceful shutdown window and are daemon threads so a task that ignores interruption cannot indefinitely pin the standalone JVM during forced termination.

The in-process interaction replay guard is also bounded and fails closed at capacity. It is intended for read-only interaction replay protection. Destructive future packages must additionally use durable database/domain idempotency and must not treat the in-memory guard as a transaction boundary.

## Health and readiness

The HTTP listener is loopback-only.

- `GET /health` returns `200` while the process is live and `503` after fatal failure/stoppage.
- `GET /ready` returns `200` only after the exact Discord application/guild/staging-channel identity fence passes; otherwise `503`.
- `HEAD` is supported; other methods receive `405`.
- responses are `Cache-Control: no-store` and contain only environment, lifecycle phase, readiness, a fixed reason category, and rejected-work count.

A transient Gateway disconnect removes readiness. JDA reconnects with incremental backoff capped at 60 seconds; a resumed/recreated session is revalidated before readiness returns. Asynchronous application-info callbacks are generation-fenced so a response from a disconnected or superseded session cannot restore readiness or fatally poison a newer session. Terminal failure cannot be changed back to `READY` by a late session callback.

## Shutdown and non-destructive smoke validation

Normal shutdown requests JDA's graceful shutdown and waits up to 15 seconds, then escalates to immediate shutdown if required. A forced shutdown that still does not terminate is a terminal runtime failure rather than a clean stop. Health and bounded worker resources are closed in either case. The runtime also installs an idempotent JVM shutdown hook.

The executable accepts `--smoke-test`. In this mode it connects, waits up to 45 seconds for the full identity/guild/test-channel readiness fence, verifies readiness is still current, exits nonzero on failure, and then closes the Gateway normally. It sends no messages and changes no Discord configuration. An authorized staging system can therefore inject the staging token and run:

```text
java -jar EnthusiaStaff-StaffBot-<version>.jar --smoke-test
```

Do not place a token on the command line. The secret-bearing validation system must inject `ENTHUSIA_STAFF_BOT_TOKEN` privately and set `ENTHUSIA_STAFF_BOT_ENVIRONMENT=staging`.

## Production boundary

ES-D05 produces deployable runtime code but does not deploy it to production. Production Discord configuration changes, production data access, moderation commands/enforcement, DiscordSRV role-sync replacement, public-bot installation, LiteBans authority changes, and issue #43 acceptance remain separately gated work.
