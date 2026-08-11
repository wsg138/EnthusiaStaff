# Discord Delivery

EnthusiaStaff writes Discord notification intent to MariaDB in the same durable workflow as the originating action. Velocity leases due rows, renders a bounded staff-facing projection, sends to an approved HTTPS route, and then records delivery or retry/dead-letter state. Discord failure never rolls back a valid moderation/report/staff action.

For the source-oriented contract and full event matrix, see [`docs/discord-delivery.md`](../../../docs/discord-delivery.md).

## Safety defaults

- `discord.enabled=false` by default.
- All four webhook values come from environment variables; webhook URLs/tokens do not belong in repository configuration or logs.
- `ES_DISCORD_ROUTE_ENVIRONMENT` must be `STAGING` or `PRODUCTION` before delivery is enabled.
- `STAGING` additionally requires `ES_DISCORD_STAGING_ALLOWED_HOSTS` containing the exact approved staging hosts.
- `PRODUCTION` accepts only Discord HTTPS webhook host/path shapes on the normal HTTPS port.
- HTTP, embedded credentials, query strings, fragments and unsafe production hosts/paths are rejected.
- Redirects are never followed; a 3xx becomes `HTTP_REDIRECT_REJECTED` so the webhook credential is not forwarded.
- Route authorization is process-scoped. Changing route environment/approved hosts/webhook environment values requires restart rather than `/estaff reload`.

ES-P06 validation uses isolated non-production delivery only. Production Discord contact is not an acceptance shortcut.

## Privacy boundary

The worker does not post raw `payload_json`. `DiscordEventRenderer` applies a destination-specific allowlist, length bounds and scalar-only rules. Unexpected nested objects are withheld.

Important examples:

- report reporter identity, description, coordinates, retained chat/private-message evidence and client-evidence objects are not rendered;
- punishment internal explanations and unrelated relationship metadata are not rendered;
- staff-session snapshot blobs are not rendered;
- malformed/non-object/oversized payloads fail closed as `PAYLOAD_REJECTED`;
- Discord mention parsing is disabled with an empty `allowed_mentions.parse` list.

Adding a new outbox producer does not automatically expose its fields. Review and deliberately extend the renderer when a new field is actually required.

## Delivery semantics

MariaDB row leasing and `FOR UPDATE SKIP LOCKED` prevent separate Velocity workers from intentionally sending the same active lease concurrently. Expired leases are recoverable after process failure or restart. Retry attempt count, circuit state, safe error code and dead-letter state are persisted.

The external webhook boundary remains **at least once**. If Discord accepts a request and the process dies before the delivery acknowledgment commits, the expired lease is retried and Discord may receive a duplicate. That is safer than marking delivered before the request and silently losing a committed notification. Reconcile apparent duplicates by their stable case/report/request/sanction identifiers.

## Status and recovery

Permission: `enthusiastaff.discord.manage`.

- `/estaff discord status` — inspect destination delivery health.
- `/estaff discord retry <punishments|reports|logs-staffmode|alerts>` — requeue a bounded set for one destination after the underlying issue is corrected.

Do not delete outbox/dead-letter rows manually just to clear health state.

A failed attempt receives bounded exponential backoff. Repeated destination failures open the persisted circuit for `discord.circuit-open-seconds`; later rows are deferred without spending attempts while it is open. `discord.maximum-attempts` sends poison/repeatedly failing rows to `DEAD_LETTER` instead of retrying forever.

## Troubleshooting

**Disabled:** queued events remain durable. Configure and validate route environment plus all webhook variables before enabling.

**Startup route rejection:** verify HTTPS, exact staging host allowlist, or the Discord production host/path rule. Do not weaken the route policy.

**`HTTP_REDIRECT_REJECTED`:** fix the configured endpoint itself. Redirect following is intentionally disabled.

**`HTTP_429` / `HTTP_5XX`:** allow circuit/backoff policy to work. Avoid repeated manual retry until the cause is corrected.

**`PAYLOAD_REJECTED`:** fix the producer/renderer contract. Never post raw stored JSON as a workaround.

**Dead letter:** investigate `/estaff discord status`, correct the route or payload failure, restart if route authorization changed, then use the bounded retry command.

## Current routes/events

- `punishments`: punishment creation, sanction change/inheritance, punishment-request lifecycle.
- `reports`: report creation and report state lifecycle.
- `logs-staffmode`: freeze/unfreeze, vanish, staff-mode enter/exit.
- `alerts`: reserved approved webhook route; Discord channel-health failures themselves remain internal staff alerts rather than recursively enqueueing another Discord notification.
