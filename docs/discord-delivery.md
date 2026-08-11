# Discord delivery

EnthusiaStaff persists Discord notification intent in MariaDB before any webhook call. Velocity is the only delivery runtime: it leases due rows, renders a bounded staff-facing projection, posts to one approved route, and then records delivery or a bounded retry/dead-letter result.

Discord delivery is **disabled by default**. A valid moderation/report/staff action does not depend on Discord being reachable. If Discord is disabled or unavailable, committed outbox events remain durable in MariaDB.

## Current event matrix

| Destination | Current durable event types | Delivery projection |
| --- | --- | --- |
| `punishments` | `PUNISHMENT_CREATED`, `SANCTION_CHANGED`, `SANCTION_INHERITED`, `PUNISHMENT_REQUEST_SUBMITTED`, `PUNISHMENT_REQUEST_CLAIMED`, `PUNISHMENT_REQUEST_APPROVED`, `PUNISHMENT_REQUEST_DENIED`, `PUNISHMENT_REQUEST_EXPIRED`, `PUNISHMENT_REQUEST_FULFILLED_EXTERNALLY` | Case/target/reason/sanction/request identifiers plus bounded action/status/type context when present. Raw internal explanation, relationship details, requester metadata and nested structures are not rendered by default. |
| `reports` | `REPORT_CREATED`, `REPORT_CLAIMED`, `REPORT_AWAITING_REVIEW`, `REPORT_CLOSED`, `REPORT_NO_VIOLATION` | Report/target/reason/server/state/actor identifiers when present. Reporter identity, report description, retained chat/private-message evidence, coordinates and client-evidence objects are not rendered. |
| `logs-staffmode` | `PLAYER_FROZEN`, `PLAYER_UNFROZEN`, `VANISH_CHANGED`, `STAFF_MODE_ENTERED`, `STAFF_MODE_EXITED` | Bounded staff/target/actor/session/rank/state fields and the freeze reason when present. Snapshot blobs and unrelated persisted state never enter the webhook body. |
| `alerts` | No ordinary `discord_outbox` producer currently emits this destination. | Reserved approved route. Discord channel-health failures are persisted as internal staff alerts rather than recursively writing another Discord outbox event. |

Adding a new producer is not enough to expose new payload fields. `DiscordEventRenderer` is an explicit destination allowlist; unknown/nested fields remain withheld until reviewed and deliberately added.

## Delivery and duplicate model

Producer transactions write a unique `idempotency_key` with each durable outbox row. Workers claim rows using a lease and MariaDB row locking (`FOR UPDATE SKIP LOCKED`), so concurrent Velocity runtimes cannot intentionally send the same live lease at the same time. Expired leases become claimable again after a crash or restart.

Webhook delivery is therefore **at least once**, not mathematically exactly once. There is an unavoidable external-side-effect crash window if Discord accepts an HTTP request and the process dies before the database acknowledgment commits. The durable row is correctly retried after lease expiry, so a duplicate Discord message is preferable to silently losing a committed moderation event. Staff should use the stable event identifiers in the rendered message when reconciling an apparent duplicate.

## Approved route configuration

The four webhook secret variable names remain in `velocity-config.properties`:

- `ES_DISCORD_PUNISHMENTS_WEBHOOK`
- `ES_DISCORD_REPORTS_WEBHOOK`
- `ES_DISCORD_STAFFMODE_WEBHOOK`
- `ES_DISCORD_ALERTS_WEBHOOK`

The route authorization boundary itself is process-scoped and environment-backed:

- `ES_DISCORD_ROUTE_ENVIRONMENT=STAGING` requires `ES_DISCORD_STAGING_ALLOWED_HOSTS` as a comma-separated exact host allowlist. Every configured webhook must be absolute HTTPS and use one of those exact hosts.
- `ES_DISCORD_ROUTE_ENVIRONMENT=PRODUCTION` accepts only `https://discord.com/api/webhooks/<id>/<token>` or the legacy `discordapp.com` host on the default HTTPS port. Query strings, fragments, embedded user info, alternate ports and non-webhook paths are rejected.
- A single worker may not mix staging and production routes.
- `discord.enabled=false` remains the safe default.

Changing route environment, approved staging hosts, or webhook environment values requires a process restart. `/estaff reload` does not partially republish route authorization.

The ES-P06 implementation and acceptance process must use only an isolated non-production route/fake transport. Production Discord contact is outside this package.

## TLS, redirects and request privacy

The production transport uses the JDK HTTPS client and the platform trust store. HTTP routes are rejected before worker startup. Redirect following is disabled; any 3xx response becomes `HTTP_REDIRECT_REJECTED`, so a webhook secret is never forwarded to a redirect target.

Webhook bodies contain only:

- a bounded plain-text event projection (maximum 1,800 characters);
- destination-approved scalar fields/short scalar arrays;
- `allowed_mentions.parse=[]` so stored identifiers/text cannot trigger `@everyone`, role or user mentions through Discord parsing.

Stored JSON is capped before rendering. Malformed/non-object payloads fail closed as `PAYLOAD_REJECTED`. Nested objects have no generic fallback renderer. Error logs use message IDs/destination/error classes rather than webhook URLs or webhook secrets.

## Retry, circuit and dead-letter behavior

`discord.maximum-attempts`, `discord.failure-threshold`, `discord.circuit-open-seconds`, and `discord.request-timeout-millis` bound delivery behavior.

A failed leased attempt records a safe error code and exponential delay capped at 15 minutes. Repeated destination failures open the persisted circuit for the configured duration. Later rows for that destination are deferred without consuming another attempt while the circuit is open. Once `maximum-attempts` is reached, the row becomes `DEAD_LETTER` rather than retrying forever.

The database remains authoritative across restart. Lease expiry, attempt count, channel failure count/open-until state, last safe error code, delivery timestamp and dead-letter status all survive process replacement.

## Operator commands

Permission: `enthusiastaff.discord.manage`.

- `/estaff discord status` — inspect durable destination health/state.
- `/estaff discord retry <punishments|reports|logs-staffmode|alerts>` — requeue a bounded set of recoverable/dead-letter rows for the selected destination through the existing durable store path.

Do not manually delete queued/dead-letter rows to make a status screen green. Investigate the route/Discord failure first, correct it, restart if route authorization changed, and then use the bounded retry command.

## Troubleshooting

### Delivery is disabled

This is expected when `discord.enabled=false`. Events remain in MariaDB. Before enabling, configure all four webhook environment variables plus `ES_DISCORD_ROUTE_ENVIRONMENT`; staging also requires `ES_DISCORD_STAGING_ALLOWED_HOSTS`.

### Worker does not start after enabling

Treat this as a route/configuration failure. Check that all webhook variables exist, every URI is HTTPS, staging hosts exactly match the approved host list, and production routes match the Discord webhook host/path policy. Do not weaken validation or switch to HTTP to bypass the failure.

### `HTTP_REDIRECT_REJECTED`

The configured endpoint returned a redirect. The worker intentionally will not follow it. Correct the configured endpoint itself; do not add a redirect allowlist that forwards the webhook credential.

### Repeated `HTTP_429` or `HTTP_5XX`

Allow the persisted retry/circuit policy to back off. Confirm `/estaff discord status`, avoid manually hammering retry, and wait for the circuit window unless the underlying route problem has actually been corrected.

### `PAYLOAD_REJECTED`

A durable producer wrote malformed, oversized or structurally unsupported JSON. Do not expose raw storage JSON as a workaround. Fix the producer/renderer contract, validate the event, then use the bounded retry command.

### Apparent duplicate message

Compare stable case/report/request/sanction identifiers. A duplicate can occur only after an ambiguous external-success crash window; concurrent workers otherwise use leased row ownership. Never "fix" this by marking a row delivered before making the webhook call, because that would convert an ambiguous crash into silent notification loss.

## Validation expectations

For an exact candidate, validation includes Java 21 warnings-as-errors, the full unit/Testcontainers suite, route/redaction/redirect tests, MariaDB concurrent-claim and restart lease recovery, review/static checks, and the canonical Pi staging bridge. No validation run may contact a production Discord route.
